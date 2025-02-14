package com.team4.giftidea.repository;

import java.util.List;
import java.util.Optional;

import com.team4.giftidea.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 상품 정보를 저장 및 조회하는 JPA Repository 인터페이스
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

	/**
	 * 주어진 키워드 목록에 해당하는 상품들을 검색합니다.
	 *
	 * @param keywords 검색할 키워드 목록
	 * @return 해당 키워드에 맞는 상품 리스트
	 */
	List<Product> findByKeywordIn(List<String> keywords);

	/**
	 * ✅ 특정 productId로 상품 조회
	 * @param productId 상품 ID
	 * @return 상품 엔티티 (없으면 Optional.empty())
	 */
	Optional<Product> findByProductId(String productId);
}