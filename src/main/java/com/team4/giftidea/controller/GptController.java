package com.team4.giftidea.controller;

import com.team4.giftidea.configuration.GptConfig;
import com.team4.giftidea.dto.GptRequestDTO;
import com.team4.giftidea.dto.GptResponseDTO;
import com.team4.giftidea.entity.Product;
import com.team4.giftidea.service.ProductService;
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
   * 1️⃣ 파일 업로드 및 전처리 API
   * 프론트엔드에서 업로드한 카카오톡 파일을 서버에서 전처리하여 정제된 메시지를 반환합니다.
   *
   * @param file MultipartFile (카톡 파일)
   * @param targetName 분석할 대상의 이름
   * @return 전처리된 텍스트 리스트
   */
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
   * 2️⃣ 선물 추천 API
   * 정제된 메시지를 기반으로 GPT에서 카테고리 생성 후, DB에서 상품을 검색하여 반환합니다.
   *
   * @param processedText 정제된 메시지 리스트
   * @param relation 사용자와의 관계 (예: couple, parent, friend)
   * @param sex 성별 (male, female)
   * @param theme 선물 테마 (birth, anniversary 등)
   * @return 추천된 상품 리스트
   */
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
   * 카카오톡 파일 전처리
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