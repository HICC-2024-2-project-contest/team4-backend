package com.team4.giftidea.controller;

import com.team4.giftidea.entity.Product;
import com.team4.giftidea.service.CoupangApiService;
import com.team4.giftidea.service.KreamApiService;
import com.team4.giftidea.service.NaverApiService;
import com.team4.giftidea.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j  // 로깅 추가
@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final NaverApiService naverApiService;
	private final CoupangApiService coupangApiService;
	private final ProductService productService;
	private final KreamApiService kreamApiService;

	public ProductController(
		NaverApiService naverApiService,
		CoupangApiService coupangApiService,
		KreamApiService kreamApiService,
		ProductService productService) {
		this.naverApiService = naverApiService;
		this.coupangApiService = coupangApiService;
		this.kreamApiService = kreamApiService;
		this.productService = productService;
	}

	/**
	 * 상품 정보를 크롤링하고 데이터베이스에 저장하는 엔드포인트
	 */
	@GetMapping("/crawl")
	public void crawlAndStoreData() {
		log.info("🔍 크롤링 시작...");

		// 네이버 키워드 목록
		List<String> naverKeywords = List.of(
			"현금 박스", "부모님 신발", "건강식품", "헬스가방", "핸드크림", "디퓨저",
			"오설록 티세트", "휴지", "초콜릿", "수제 초콜릿 키트", "파자마세트"
		);

		// 쿠팡 키워드 목록
		List<String> coupangKeywords = List.of("안마기기", "무선이어폰", "스마트워치");

		// Kream 키워드 목록
		List<String> kreamKeywords = List.of(
			"남성 지갑", "남성 스니커즈", "백팩", "토트백", "크로스백", "벨트",
			"선글라스", "향수", "여성 지갑", "여성 스니커즈", "숄더백", "목걸이",
			"텀블러", "립밤", "조명", "핸드워시", "식기"
		);

		log.info("📢 네이버 크롤링 시작...");
		naverKeywords.forEach(keyword -> {
			log.debug("🔎 네이버 검색 키워드: {}", keyword);
			List<Product> naverProducts = naverApiService.searchItems(List.of(keyword));
			log.info("✅ 네이버 크롤링 완료 (키워드: {}, 검색 결과: {} 개)", keyword, naverProducts.size());

			if (!naverProducts.isEmpty()) {
				productService.saveItems(naverProducts, keyword);
				log.info("✅ 네이버 상품 저장 완료 (키워드: {}, 저장된 개수: {})", keyword, naverProducts.size());
			} else {
				log.warn("⚠️ 네이버 크롤링 실패 또는 검색 결과 없음 (키워드: {})", keyword);
			}
		});

		log.info("📢 쿠팡 크롤링 시작...");
		coupangKeywords.forEach(keyword -> {
			log.debug("🔎 쿠팡 검색 키워드: {}", keyword);
			List<Product> coupangProducts = coupangApiService.searchItems(keyword);
			log.info("✅ 쿠팡 크롤링 완료 (키워드: {}, 검색 결과: {} 개)", keyword, coupangProducts.size());

			if (!coupangProducts.isEmpty()) {
				productService.saveItems(coupangProducts, keyword);
				log.info("✅ 쿠팡 상품 저장 완료 (키워드: {}, 저장된 개수: {})", keyword, coupangProducts.size());
			} else {
				log.warn("⚠️ 쿠팡 크롤링 실패 또는 검색 결과 없음 (키워드: {})", keyword);
			}
		});

		log.info("📢 Kream 크롤링 시작...");
		kreamKeywords.forEach(keyword -> {
			log.debug("🔎 Kream 검색 키워드: {}", keyword);
			List<Product> kreamProducts = kreamApiService.searchItems(keyword);
			log.info("✅ Kream 크롤링 완료 (키워드: {}, 검색 결과: {} 개)", keyword, kreamProducts.size());

			if (!kreamProducts.isEmpty()) {
				productService.saveItems(kreamProducts, keyword);
				log.info("✅ Kream 상품 저장 완료 (키워드: {}, 저장된 개수: {})", keyword, kreamProducts.size());
			} else {
				log.warn("⚠️ Kream 크롤링 실패 또는 검색 결과 없음 (키워드: {})", keyword);
			}
		});

		log.info("🎯 크롤링 및 저장 작업 완료!");
	}
}