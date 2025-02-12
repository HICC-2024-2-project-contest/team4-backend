package com.team4.giftidea.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team4.giftidea.configuration.GptConfig;
import com.team4.giftidea.dto.GptRequestDTO;
import com.team4.giftidea.dto.GptResponseDTO;
import com.team4.giftidea.entity.Product;
import com.team4.giftidea.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Tag(name = "GPT 추천 API", description = "GPT를 이용하여 사용자 맞춤 선물 추천을 제공하는 API")
@RestController
@RequestMapping("/api/gpt")
@Slf4j
public class GptController {

  private final RestTemplate restTemplate;
  private final GptConfig gptConfig;
  private final ProductService productService;

  @Autowired
  public GptController(RestTemplate restTemplate, GptConfig gptConfig, ProductService productService) {
    this.restTemplate = restTemplate;
    this.gptConfig = gptConfig;
    this.productService = productService;
  }

  /**
   * 카카오톡 대화 파일을 분석하여 키워드를 추출하고, 추천 상품 목록을 반환하는 API
   *
   * @param file       카카오톡 대화 내용이 포함된 파일
   * @param targetName 대상 이름 (예: "여자친구", "남자친구")
   * @param relation   관계 (예: "couple", "friend", "parent")
   * @param sex        대상 성별 ("male" 또는 "female")
   * @param theme      선물 테마 (예: "birthday", "valentine")
   * @return 추천된 상품 목록
   */
  @Operation(summary = "대화 분석 후 추천 상품 반환", description = "카카오톡 대화를 분석하여 GPT API를 통해 키워드를 추출하고, 해당 키워드에 맞는 추천 상품을 반환합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "추천 상품 목록 반환"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
      @ApiResponse(responseCode = "500", description = "서버 내부 오류 발생")
  })
  @PostMapping("/process")
  public List<Product> processFileAndRecommend(
      @RequestParam("file") @Parameter(description = "카카오톡 대화 내용이 포함된 파일", required = true) MultipartFile file,
      @RequestParam("targetName") @Parameter(description = "대상 이름", required = true) String targetName,
      @RequestParam("relation") @Parameter(description = "대상과의 관계", required = true) String relation,
      @RequestParam("sex") @Parameter(description = "대상 성별", required = true) String sex,
      @RequestParam("theme") @Parameter(description = "선물의 주제", required = true) String theme) {

    // 1. 파일 전처리
    List<String> processedMessages = preprocessKakaoFile(file, targetName);

    // 2. GPT API 호출: 전처리된 메시지로 키워드 반환
    String categories = generatePrompt(processedMessages, relation, sex, theme);  // 이미 키워드를 추출했음

    // 3. 키워드 리스트로 변환된 값 그대로 사용
    List<String> keywords = Arrays.asList(categories.split(","));
    keywords.replaceAll(String::trim);  // 공백 제거

    log.debug("추출된 키워드 목록: {}", keywords);

    // 4. 상품 검색 (DB에서 키워드 기반으로 추천 상품 검색)
    List<Product> products = productService.searchByKeywords(keywords);

    // 검색된 상품 확인 로그
    log.debug("검색된 상품: {}", products);

    return products;  // 상품 목록 반환
  }

  private static final int MAX_TOKENS = 15000; // 15000 토큰 제한

  private List<String> preprocessKakaoFile(MultipartFile file, String targetName) {
    List<String> processedMessages = new ArrayList<>();
    int formatType = detectFormatType(file);
    File outputFile = null;
    int currentTokenCount = 0;
    StringBuilder currentChunk = new StringBuilder();

    try {
      outputFile = File.createTempFile("processed_kakaochat", ".txt");

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
           BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

        String line;
        while ((line = reader.readLine()) != null) {
          // 해당 targetName이 포함된 경우만 처리
          if (line.contains(targetName) && !line.trim().isEmpty()) {
            String formattedLine = formatLine(line, formatType, targetName);
            int lineTokenCount = countTokens(formattedLine);

            // 현재 청크가 15000 토큰을 초과할 경우 새로운 청크 생성
            if (currentTokenCount + lineTokenCount > MAX_TOKENS) {
              processedMessages.add(currentChunk.toString()); // 기존 청크 저장
              currentChunk.setLength(0); // 새 청크 초기화
              currentTokenCount = 0;
            }

            // 현재 청크에 추가
            currentChunk.append(formattedLine).append("\n");
            currentTokenCount += lineTokenCount;
            writer.write(formattedLine);
            writer.newLine();
          }
        }

        // 마지막 청크 추가
        if (currentChunk.length() > 0) {
          processedMessages.add(currentChunk.toString());
        }

      }
    } catch (IOException e) {
      log.error("파일 처리 오류: ", e);
    }

    // 파일 삭제 (전처리 후 필요 없으므로 삭제)
    if (outputFile != null) {
      outputFile.delete();
    }

    return processedMessages;
  }

  /**
   * ✅ Format Type에 따라 카카오톡 메시지를 정리
   */
  private String formatLine(String line, int formatType, String targetName) {
    if (formatType == 1) {
      return line.replaceAll("\\[.*?\\] \\[.*?\\] ", "").replaceAll("[ㅎㅋ.]+", "").trim(); // 양식 1: [시간] [이름] 제거
    } else if (formatType == 2) {
      return line.replaceAll("^" + targetName + " : ", "").replaceAll("[ㅎㅋ.]+", "").trim(); // 양식 2: "이름 :" 제거
    }
    return line;
  }

  /**
   * ✅ 메시지의 토큰 개수 세는 함수 (단순 공백 기준으로 토큰 계산)
   */
  private int countTokens(String text) {
    return text.split("\\s+").length; // 공백 기준으로 나누어 토큰 수 계산
  }

  private int detectFormatType(MultipartFile file) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String firstLine = reader.readLine();

      if (firstLine != null && firstLine.contains("님과 카카오톡 대화")) {
        return 1; // 양식 1
      } else {
        return 2; // 양식 2
      }

    } catch (IOException e) {
      log.error("파일 판별 오류: ", e);
    }

    return 0; // 기본값: 알 수 없는 양식
  }

  private String generatePrompt(List<String> processedMessages, String relation, String sex, String theme) {
    String combinedMessages = String.join("\n", processedMessages);  // List<String>을 하나의 String으로 합침

    switch (relation) {
      case "couple":
        return sex.equals("male") ? extractKeywordsAndReasonsCoupleMan(theme, combinedMessages)
            : extractKeywordsAndReasonsCoupleWoman(theme, combinedMessages);
      case "parent":
        return extractKeywordsAndReasonsParents(theme, combinedMessages);
      case "friend":
        return extractKeywordsAndReasonsFriend(theme, combinedMessages);
      case "housewarming":
        return extractKeywordsAndReasonsHousewarming(combinedMessages);
      case "valentine":
        return extractKeywordsAndReasonsSeasonal(theme, combinedMessages);
      default:
        return "조건에 맞는 선물 추천이 없습니다.";
    }
  }

  private String generateText(String prompt) {
    GptRequestDTO request = new GptRequestDTO(gptConfig.getModel(), prompt);
    try {
      log.info("GPT 요청 시작 - 모델: {}", gptConfig.getModel());
      log.debug("요청 내용: {}", prompt);

      // HTTP 요청 전에 request 객체 로깅
      ObjectMapper mapper = new ObjectMapper();
      log.debug("전체 요청 바디: {}", mapper.writeValueAsString(request));

      GptResponseDTO response = restTemplate.postForObject(gptConfig.getApiUrl(), request, GptResponseDTO.class);

      // 응답 검증
      if (response != null) {
        log.debug("GPT 응답 수신: {}", mapper.writeValueAsString(response));

        // 응답에 'choices'가 있고, 그 중 첫 번째 항목이 존재하는지 확인
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
          String content = response.getChoices().get(0).getMessage().getContent();
          log.debug("추출된 콘텐츠: {}", content);

          // 필요한 형태로 카테고리 추출 (예: "1. [무선이어폰, 스마트워치, 향수]" 형태)
          if (content.contains("1.")) {
            String categories = content.split("1.")[1].split("\n")[0]; // 첫 번째 카테고리 라인 추출
            log.debug("GPT 응답에서 추출된 카테고리: {}", categories);

            // 괄호 안의 항목들을 추출하고, 쉼표로 구분하여 키워드 리스트 만들기
            String[] categoryArray = categories.split("\\[|\\]")[1].split(",");
            List<String> keywords = new ArrayList<>();
            for (String category : categoryArray) {
              keywords.add(category.trim());
            }
            return String.join(", ", keywords); // 최종적으로 카테고리들을 반환
          } else {
            log.warn("GPT 응답에서 카테고리 정보가 올바르지 않습니다.");
          }
        } else {
          log.warn("GPT 응답에 'choices'가 없거나 빈 리스트입니다.");
        }
      } else {
        log.warn("GPT 응답이 null입니다.");
      }
      return "GPT 응답 오류 발생";
    } catch (Exception e) {
      log.error("GPT 요청 중 오류 발생: ", e);
      log.error("상세 오류 메시지: {}", e.getMessage());
      if (e.getCause() != null) {
        log.error("원인 예외: {}", e.getCause().getMessage());
      }
      return "GPT 요청 오류";
    }
  }

  private String extractKeywordsAndReasonsCoupleMan(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 남자 애인이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 남성 지갑, 남성 스니커즈, 백팩, 토트백, 크로스백, 벨트, 선글라스, 향수, 헬스가방, 무선이어폰, 스마트워치

    텍스트: %s

    출력 형식:
    1. [카테고리1,카테고리2,카테고리3]
    2. 
       - 카테고리1: [근거1]
       - 카테고리2: [근거2]
       - 카테고리3: [근거3]
    """, theme, message);

    return generateText(prompt);
  }

  private String extractKeywordsAndReasonsCoupleWoman(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 여자 애인이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 여성 지갑, 여성 스니커즈, 숄더백, 토트백, 크로스백, 향수, 목걸이, 무선이어폰, 스마트워치

    텍스트: %s

    출력 형식:
    1. [카테고리1,카테고리2,카테고리3]
    2. 
       - 카테고리1: [근거1]
       - 카테고리2: [근거2]
       - 카테고리3: [근거3]
    """, theme, message);

    return generateText(prompt);  // GPT 모델 호출
  }

  private String extractKeywordsAndReasonsParents(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 부모님이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 현금 박스, 안마기기, 부모님 신발, 건강식품

    텍스트: %s

    출력 형식:
    1. [카테고리1,카테고리2,카테고리3]
    2. 
       - 카테고리1: [근거1]
       - 카테고리2: [근거2]
       - 카테고리3: [근거3]
    """, theme, message);

    return generateText(prompt);
  }

  private String extractKeywordsAndReasonsFriend(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 친구가 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    제시된 카테고리에 없는 추천 선물이 있다면 3개에 포함해주세요.
    카테고리: 핸드크림, 텀블러, 립밤

    텍스트: %s

    출력 형식:
    1. [카테고리1,카테고리2,카테고리3]
    2. 
       - 카테고리1: [근거1]
       - 카테고리2: [근거2]
       - 카테고리3: [근거3]
    """, theme, message);

    return generateText(prompt);
  }

  private String extractKeywordsAndReasonsHousewarming(String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 집들이에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 조명, 핸드워시, 식기, 디퓨저, 꽃, 오설록 티세트, 휴지

    텍스트: %s

    출력 형식:
    1. [카테고리1,카테고리2,카테고리3]
    2. 
       - 카테고리1: [근거1]
       - 카테고리2: [근거2]
       - 카테고리3: [근거3]
    """, message);

    return generateText(prompt);
  }

  private String extractKeywordsAndReasonsSeasonal(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 초콜릿, 수제 초콜릿 키트, 립밤, 파자마세트

    텍스트: %s

    출력 형식:
    1. [카테고리1,카테고리2,카테고리3]
    2. 
       - 카테고리1: [근거1]
       - 카테고리2: [근거2]
       - 카테고리3: [근거3]
    """, theme, message);

    return generateText(prompt);
  }
}
