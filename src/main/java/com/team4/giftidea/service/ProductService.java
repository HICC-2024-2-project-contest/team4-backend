package com.team4.giftidea.service;

import com.team4.giftidea.entity.Product;
import com.team4.giftidea.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

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
	public void saveItems(List<Product> productList, String keyword) {
		productList.forEach(product -> {
			product.setKeyword(keyword); // 상품에 키워드 설정

			// 상품 ID가 존재하지 않으면 저장
			if (!productRepository.existsByProductId(product.getProductId())) {
				productRepository.save(product);
			}
		});
	}
}