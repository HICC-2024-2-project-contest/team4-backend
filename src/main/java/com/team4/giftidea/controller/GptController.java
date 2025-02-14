package com.team4.giftidea.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team4.giftidea.configuration.GptConfig;
import com.team4.giftidea.dto.GptRequestDTO;
import com.team4.giftidea.dto.GptResponseDTO;
import com.team4.giftidea.entity.Product;
import com.team4.giftidea.service.ProductService;

@Slf4j
@RestController
@RequestMapping("/api/gpt")
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


  // GPT 모델의 입력 토큰 제한 (예: 출력 토큰 고려 후 설정, 여기서는 예시로 25000)
  private static final int GPT_INPUT_LIMIT = 12000;

  /**
   * 파일의 아랫부분부터 토큰을 센 후, 총 토큰 수가 GPT_INPUT_LIMIT 이하인 내용만
   * 선택하여 로컬에 저장하고, 그 청크를 반환합니다.
   *
   * @param file       업로드된 카카오톡 대화 파일 (.txt)
   * @param targetName 대상 이름 (예: "여자친구")
   * @return 전처리된 청크 (아랫부분부터 토큰 누적하여 GPT_INPUT_LIMIT 이하)
   */
  @Operation(
      summary = "카톡 대화 분석 후 선물 추천",
      description = "카카오톡 대화 파일을 분석하여 GPT API를 이용해 키워드를 추출하고, 이에 맞는 추천 상품을 반환합니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "추천 상품 목록 반환"),
      @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
      @ApiResponse(responseCode = "415", description = "지원되지 않는 파일 형식"),
      @ApiResponse(responseCode = "500", description = "서버 내부 오류 발생")
  })
  @PostMapping(value = "/process", consumes = "multipart/form-data", produces = "application/json")
  public List<Product> processFileAndRecommend(
      @RequestParam("file") @Parameter(description = "카카오톡 대화 파일 (.txt)", required = true) MultipartFile file,
      @RequestParam("targetName") @Parameter(description = "분석 대상 이름 (예: '여자친구')", required = true) String targetName,
      @RequestParam("relation") @Parameter(description = "대상과의 관계 (couple, friend, parent 등)", required = true) String relation,
      @RequestParam("sex") @Parameter(description = "대상 성별 (male 또는 female)", required = true) String sex,
      @RequestParam("theme") @Parameter(description = "선물 주제 (birthday, valentine 등)", required = true) String theme
  ) {

    List<String> processedMessages = new ArrayList<>();
    int formatType = detectFormatType(file);

    // 1. 파일의 모든 줄을 읽고, targetName이 포함된 줄만 필터링하여 리스트에 저장
    List<String> allTargetLines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains(targetName) && !line.trim().isEmpty()) {
          String formattedLine = formatLine(line, formatType, targetName);
          allTargetLines.add(formattedLine);
        }
      }
    } catch (IOException e) {
      log.error("파일 읽기 오류: ", e);
    }

    // 2. 파일의 아랫부분부터 토큰을 누적 (역순으로 처리)
    int currentTokenCount = 0;
    List<String> selectedLines = new ArrayList<>();
    // reverse 순회
    for (int i = allTargetLines.size() - 1; i >= 0; i--) {
      String currentLine = allTargetLines.get(i);
      int tokenCount = countTokens(currentLine);
      if (currentTokenCount + tokenCount > GPT_INPUT_LIMIT) {
        // 토큰 제한을 초과하면 중단
        break;
      }
      // 아랫부분부터 선택하므로, 먼저 선택된 줄이 마지막에 온다.
      selectedLines.add(currentLine);
      currentTokenCount += tokenCount;
    }
    // 원래 순서대로 복원 (파일에서 아랫부분이 우선이므로, 리스트를 reverse)
    Collections.reverse(selectedLines);

    // 3. 선택된 줄들을 하나의 청크로 합침
    StringBuilder finalChunk = new StringBuilder();
    for (String s : selectedLines) {
      finalChunk.append(s).append("\n");
    }
    processedMessages.add(finalChunk.toString());

    // 2. GPT API 호출: 전처리된 메시지로 키워드 반환
    String categories = generatePrompt(processedMessages, relation, sex, theme);

    // 3. 키워드 리스트 변환 및 상품 검색
    List<String> keywords = Arrays.asList(categories.split(","));
    keywords.replaceAll(String::trim);

    List<Product> products = productService.searchByKeywords(keywords);

    return products;
  }

  private int detectFormatType(MultipartFile file) {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String firstLine = reader.readLine();
      if (firstLine != null && firstLine.contains("님과 카카오톡 대화")) {
        return 1;
      } else {
        return 2;
      }
    } catch (IOException e) {
      log.error("파일 판별 오류: ", e);
    }
    return 0;
  }

  private String formatLine(String line, int formatType, String targetName) {
    if (formatType == 1) {
      return line.replaceAll("\\[.*?\\] \\[.*?\\] ", "")
          .replaceAll("[ㅎㅋ.]+", "").trim();
    } else if (formatType == 2) {
      return line.replaceAll("^" + targetName + " : ", "")
          .replaceAll("[ㅎㅋ.]+", "").trim();
    }
    return line;
  }

  private int countTokens(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return text.split("\\s+").length;
  }

  private String generatePrompt(List<String> processedMessages, String relation, String sex, String theme) {
    String combinedMessages = String.join("\n", processedMessages);  // List<String>을 하나의 String으로 합침

    if ("couple".equals(relation)) {
      if ("male".equals(sex)) {
        return extractKeywordsAndReasonsCoupleMan(theme, combinedMessages);
      } else if ("female".equals(sex)) {
        return extractKeywordsAndReasonsCoupleWoman(theme, combinedMessages);
      }
    } else if ("parent".equals(relation)) {
      if ("male".equals(sex)) {
        return extractKeywordsAndReasonsDad(theme, combinedMessages);
      } else if ("female".equals(sex)) {
        return extractKeywordsAndReasonsMom(theme, combinedMessages);
      }
    } else if ("friend".equals(relation)) {
      return extractKeywordsAndReasonsFriend(theme, combinedMessages);
    } else if ("housewarming".equals(theme)) {
      return extractKeywordsAndReasonsHousewarming(combinedMessages);
    } else if ("valentine".equals(theme)) {
      if ("male".equals(sex)) {
        return extractKeywordsAndReasonsSeasonalMan(theme, combinedMessages);
      } else if ("female".equals(sex)) {
        return extractKeywordsAndReasonsSeasonalWoman(theme, combinedMessages);
      }
    }

    return "조건에 맞는 선물 추천 기능이 없습니다.";
  }

  private String generateText(String prompt) {
    GptRequestDTO request = new GptRequestDTO(gptConfig.getModel(), prompt);
    try {

      // HTTP 요청 전에 request 객체 로깅
      ObjectMapper mapper = new ObjectMapper();

      GptResponseDTO response = restTemplate.postForObject(gptConfig.getApiUrl(), request, GptResponseDTO.class);

      // 응답 검증
      if (response != null) {
        log.debug("GPT 응답 수신: {}", mapper.writeValueAsString(response));

        // 응답에 'choices'가 있고, 그 중 첫 번째 항목이 존재하는지 확인
        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
          String content = response.getChoices().get(0).getMessage().getContent();

          // 필요한 형태로 카테고리 추출 (예: "1. [무선이어폰, 스마트워치, 향수]" 형태)
          if (content.contains("1.")) {
            String categories = content.split("1.")[1].split("\n")[0]; // 첫 번째 카테고리 라인 추출

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
      if (e.getCause() != null) {
        log.error("원인 예외: {}", e.getCause().getMessage());
      }
      return "GPT 요청 오류";
    }
  }

  private String extractKeywordsAndReasonsCoupleMan(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 남자 애인이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 남성 지갑, 남성 스니커즈, 백팩, 토트백, 크로스백, 벨트, 선글라스, 향수, 헬스가방, 무선이어폰, 스마트워치, 맨투맨, 마우스, 키보드, 전기면도기, 게임기

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
    카테고리: 여성 지갑, 여성 스니커즈, 숄더백, 토트백, 크로스백, 향수, 목걸이, 무선이어폰, 스마트워치, 에어랩

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

  private String extractKeywordsAndReasonsDad(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 부모님이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 현금 박스, 안마기기, 아버지 신발, 시계

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

  private String extractKeywordsAndReasonsMom(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 부모님이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 현금 박스, 안마기기, 어머니 신발, 건강식품, 스카프

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
    카테고리: 핸드크림, 텀블러, 립밤, 머플러, 비타민, 입욕제, 블루투스 스피커

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
    카테고리: 조명, 핸드워시, 식기, 디퓨저, 오설록 티세트, 휴지, 파자마세트, 무드등, 디퓨저, 수건, 전기포트, 에어프라이기

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

  private String extractKeywordsAndReasonsSeasonalMan(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 초콜릿, 수제 초콜릿 키트, 파자마세트, 남자 화장품

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

  private String extractKeywordsAndReasonsSeasonalWoman(String theme, String message) {
    String prompt = String.format("""
    다음 텍스트를 참고하여 %s에 선물로 받으면 좋아할 카테고리 3개와 판단에 참고한 대화를 제공해주세요. 
    카테고리: 초콜릿, 수제 초콜릿 키트, 립밤, 파자마세트, 립스틱

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