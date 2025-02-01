package com.team4.giftidea.controller;

import com.team4.giftidea.configuration.GptConfig;
import com.team4.giftidea.dto.GptRequestDTO;
import com.team4.giftidea.dto.GptResponseDTO;
import com.team4.giftidea.entity.Product;
import com.team4.giftidea.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GPT API와 연동하여 선물 추천을 제공하는 컨트롤러
 */
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
   * 1️⃣ **카카오톡 파일 업로드 및 전처리**
   * - 프론트엔드에서 **카카오톡 파일을 업로드**
   * - 대상 이름을 입력받아 해당 사용자의 메시지만 추출
   * - **이모티콘, 불필요한 특수문자, 반복 문자(ㅋㅋ, ㅎㅎ, ㅠㅠ 등) 정리**
   * - 전처리된 **깨끗한 메시지 리스트 반환**
   *
   * @param file 카카오톡 텍스트 파일 (MultipartFile)
   * @param targetName 분석할 대화 상대 이름
   * @return 정제된 메시지 리스트
   */
  @Operation(
      summary = "카카오톡 파일 업로드 및 전처리",
      description = "카카오톡 대화 내용을 업로드하고 특정 사용자의 메시지만 정리하여 반환합니다.",
      responses = {
          @ApiResponse(responseCode = "200", description = "정제된 메시지 반환",
              content = @Content(mediaType = "application/json", schema = @Schema(implementation = List.class))),
          @ApiResponse(responseCode = "500", description = "파일 처리 오류 발생")
      }
  )
  @PostMapping("/upload")
  public List<String> uploadFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam("targetName") String targetName) {

    try {
      // 파일 저장
      File tempFile = File.createTempFile("kakaochat", ".txt");
      file.transferTo(tempFile);

      // 파일 전처리
      List<String> processedMessages = preprocessKakaoFile(tempFile, targetName);

      // 파일 삭제 (일회성 사용)
      tempFile.delete();

      return processedMessages;
    } catch (IOException e) {
      log.error("파일 처리 오류: ", e);
      return Collections.emptyList();
    }
  }

  /**
   * 2️⃣ **선물 추천 API**
   * - 사용자의 **카카오톡 대화 내용을 기반으로 GPT가 선물 추천**
   * - **관계, 성별, 테마(생일, 기념일 등)** 정보를 기반으로 선물 추천 카테고리를 생성
   * - **추천된 카테고리를 기반으로 DB에서 상품 검색**
   *
   * @param processedText 정제된 메시지 리스트 (이전 `/upload` API 응답 값)
   * @param relation 사용자와의 관계 (couple, parent, friend, housewarming, valentine 등)
   * @param sex 성별 (male, female)
   * @param theme 선물 테마 (birth, anniversary 등)
   * @return 추천된 상품 리스트
   */
  @Operation(
      summary = "GPT 선물 추천 API",
      description = "정제된 카톡 메시지와 관계, 성별, 테마를 기반으로 GPT가 선물을 추천하고, 해당 카테고리의 상품을 검색합니다.",
      responses = {
          @ApiResponse(responseCode = "200", description = "추천된 상품 리스트 반환",
              content = @Content(mediaType = "application/json", schema = @Schema(implementation = List.class))),
          @ApiResponse(responseCode = "500", description = "GPT 요청 오류 발생")
      }
  )
  @PostMapping("/recommend")
  public List<Product> recommendGifts(
      @RequestBody List<String> processedText,
      @RequestParam(name = "relation") String relation,
      @RequestParam(name = "sex") String sex,
      @RequestParam(name = "theme") String theme) {

    // GPT 프롬프트 생성
    String prompt = generatePrompt(processedText, relation, sex, theme);

    // GPT API 호출
    GptRequestDTO request = new GptRequestDTO(gptConfig.getModel(), prompt);
    GptResponseDTO response = restTemplate.postForObject(gptConfig.getApiUrl(), request, GptResponseDTO.class);

    if (response != null && !response.getChoices().isEmpty()) {
      // GPT 응답에서 카테고리 추출
      String categories = response.getChoices().get(0).getMessage().getContent();
      List<String> keywords = Arrays.asList(categories.split(","));

      // DB에서 추천 상품 검색
      return productService.searchByKeywords(keywords, 0);
    }

    return List.of(); // 추천할 상품이 없을 경우 빈 리스트 반환
  }

  /**
   * GPT 프롬프트 생성
   */
  private String generatePrompt(List<String> messages, String relation, String sex, String theme) {
    String combinedMessages = String.join("\n", messages);

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

  /**
   * GPT 응답에서 키워드 추출
   */
  private String extractKeywordsAndReasonsCoupleMan(String theme, String message) {
    return generateText(String.format("""
                다음 텍스트를 참고하여 남자 애인이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단 근거를 제공해주세요.
                텍스트: %s
                """, theme, message));
  }

  private String extractKeywordsAndReasonsCoupleWoman(String theme, String message) {
    return generateText(String.format("""
                다음 텍스트를 참고하여 여자 애인이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단 근거를 제공해주세요.
                텍스트: %s
                """, theme, message));
  }

  private String extractKeywordsAndReasonsParents(String theme, String message) {
    return generateText(String.format("""
                다음 텍스트를 참고하여 부모님이 %s에 선물로 받으면 좋아할 카테고리 3개와 판단 근거를 제공해주세요.
                텍스트: %s
                """, theme, message));
  }

  private String extractKeywordsAndReasonsFriend(String theme, String message) {
    return generateText(String.format("""
                다음 텍스트를 참고하여 친구가 %s에 선물로 받으면 좋아할 카테고리 3개와 판단 근거를 제공해주세요.
                텍스트: %s
                """, theme, message));
  }

  private String extractKeywordsAndReasonsHousewarming(String message) {
    return generateText(String.format("""
                다음 텍스트를 참고하여 집들이에 선물로 받으면 좋아할 카테고리 3개와 판단 근거를 제공해주세요.
                텍스트: %s
                """, message));
  }

  private String extractKeywordsAndReasonsSeasonal(String theme, String message) {
    return generateText(String.format("""
                다음 텍스트를 참고하여 %s에 선물로 받으면 좋아할 카테고리 3개와 판단 근거를 제공해주세요.
                텍스트: %s
                """, theme, message));
  }

  /**
   * GPT API 호출
   */
  private String generateText(String prompt) {
    GptRequestDTO request = new GptRequestDTO(gptConfig.getModel(), prompt);
    try {
      GptResponseDTO response = restTemplate.postForObject(gptConfig.getApiUrl(), request, GptResponseDTO.class);
      if (response != null && !response.getChoices().isEmpty()) {
        return response.getChoices().get(0).getMessage().getContent();
      }
      return "GPT 응답 오류 발생";
    } catch (Exception e) {
      log.error("GPT 요청 중 오류 발생: ", e);
      return "GPT 요청 오류";
    }
  }

  /**
   * 카카오톡 파일 전처리 함수
   * - 카카오톡에서 대상 사용자의 메시지만 추출
   * - 불필요한 문자, 특수기호, 이모티콘 정리
   * - 사용자의 최신 메시지부터 일정 개수만 유지
   *
   * @param file 원본 카카오톡 텍스트 파일
   * @param targetName 분석할 사용자 이름
   * @return 정제된 메시지 리스트
   */
  private List<String> preprocessKakaoFile(File file, String targetName) {
    List<String> processedMessages = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.contains(targetName)) {
          processedMessages.add(line);
        }
      }
    } catch (IOException e) {
      log.error("파일 처리 오류: ", e);
    }

    return processedMessages;
  }
}