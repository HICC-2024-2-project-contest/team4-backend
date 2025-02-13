package com.team4.giftidea.service;

import com.team4.giftidea.entity.Product;
import com.team4.giftidea.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import jakarta.transaction.Transactional;

@Service
public class ProductService {
	private static final Logger log = LoggerFactory.getLogger(ProductService.class);

	private final ProductRepository productRepository;

	@Autowired
	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	/**
	 * 키워드에 해당하는 상품을 반환
	 * 각 키워드 별로 상품 목록을 구분하여 반환
	 *
	 * @param keywords 검색 키워드 리스트
	 * @return 키워드 별로 구분된 상품 목록
	 */
	public List<Product> searchByKeywords(List<String> keywords) {
		// 여러 키워드를 받아 해당하는 상품들을 반환
		return productRepository.findByKeywordIn(keywords);
	}

	/**
	 * 상품 리스트를 저장하며, 기존 상품 ID가 존재하는 경우 중복 저장을 방지합니다.
	 *
	 * @param productList 저장할 상품 리스트
	 * @param keyword     상품에 연관된 키워드
	 */
	@Transactional
	public void saveItems(List<Product> productList, String keyword) {
		log.info("🟢 [{}] 저장 시작 - 총 {}개", keyword, productList.size());

		productList.forEach(product -> {
			product.setKeyword(keyword);

			// ✅ 기존 상품이 있는 경우 → 업데이트 (덮어쓰기)
			productRepository.findByProductId(product.getProductId()).ifPresentOrElse(existingProduct -> {
				existingProduct.setTitle(product.getTitle());
				existingProduct.setPrice(product.getPrice());
				existingProduct.setImage(product.getImage());
				existingProduct.setLink(product.getLink());
				existingProduct.setMallName(product.getMallName());
				productRepository.save(existingProduct);
				log.info("🔄 상품 업데이트 완료 [{}]", existingProduct.getProductId());
			}, () -> {
				// ✅ 기존 상품이 없으면 신규 저장
				productRepository.save(product);
				log.info("💾 신규 상품 저장 [{}]", product.getProductId());
			});
		});

		productRepository.flush();
		log.info("✅ [{}] 저장 완료 (flush 호출됨)", keyword);
	}
}