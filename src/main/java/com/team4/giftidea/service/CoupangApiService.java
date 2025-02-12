package com.team4.giftidea.service;

import com.team4.giftidea.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 쿠팡에서 상품을 크롤링하는 서비스 클래스
 */
@Service
@Slf4j
public class CoupangApiService {

	private static final String COUPANG_SEARCH_URL = "https://www.coupang.com/np/search?q=%s&channel=user";
	private static final String USER_AGENT = "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.90 Safari/537.36";

	@Value("${selenium.chromedriver-path}")
	private String chromeDriverPath;

	private final ProductService productService;

	/**
	 * CoupangApiService 생성자
	 *
	 * @param productService 상품 저장을 위한 서비스
	 */
	public CoupangApiService(ProductService productService) {
		this.productService = productService;
	}

	/**
     * 주어진 키워드에 대해 쿠팡에서 상품을 검색하고 리스트로 반환합니다.
     *
     * @param query 검색 키워드
     * @return 크롤링된 상품 리스트
     */
    public List<Product> searchItems(String query) {
	    List<Product> productList = new ArrayList<>();
	    System.setProperty("webdriver.chrome.driver", chromeDriverPath);
	    ChromeOptions options = new ChromeOptions();
	    options.setBinary("/opt/google/chrome/chrome");  // 크롬 바이너리 직접 지정 (AWS 환경)
	    options.addArguments("--headless");  // 기본적으로 headless 모드 유지
	    options.addArguments("--disable-gpu");
	    options.addArguments("--no-sandbox");
	    options.addArguments("--disable-dev-shm-usage");
	    options.addArguments("--remote-debugging-port=9222");
	    options.addArguments("--window-size=1920,1080");
	    options.addArguments("--disable-software-rasterizer");
	    options.addArguments("--disable-extensions");
	    options.addArguments("--disable-popup-blocking");
	
	    // 최신 User-Agent 추가 (봇 탐지 우회)
	    options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.5672.63 Safari/537.36");
	    
	    WebDriver driver = new ChromeDriver(options);
	
	    try {
	        log.info("검색어: {}", query);
	
	        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
	        String searchUrl = String.format("https://www.coupang.com/np/search?q=%s&channel=user", encodedQuery);
	        
	        log.info("쿠팡 검색 URL: {}", searchUrl);
	        driver.get(searchUrl);
	        
	        log.info("쿠팡 페이지 접속 완료. 5초 대기 중...");
	        Thread.sleep(5000); // 페이지 로딩 대기
	
	        // navigator.webdriver 감추기
	        JavascriptExecutor js = (JavascriptExecutor) driver;
	        js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
	
	        log.info("navigator.webdriver 속성 제거 완료.");
	
	        // 현재 페이지의 HTML 확인
	        String pageSource = driver.getPageSource();
	        log.debug("현재 페이지 HTML (앞부분): {}", pageSource.substring(0, Math.min(1000, pageSource.length())));
	
	        // 페이지가 제대로 로드되었는지 확인
	        if (pageSource.contains("captcha") || pageSource.contains("robot check")) {
	            log.error("쿠팡에서 봇 탐지를 수행함. 크롤링 차단됨.");
	            return Collections.emptyList();
	        }
	
	        log.info("상품 리스트 로딩 시작...");
	        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(90));
	        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".search-product")));
	
	        List<WebElement> products = driver.findElements(By.cssSelector(".search-product"));
	
	        if (products.isEmpty()) {
	            log.warn("검색 결과가 없음. 크롤링할 제품 없음.");
	            return Collections.emptyList();
	        }
	
	        log.info("총 {}개의 상품 발견", products.size());
	
	        for (WebElement productElement : products) {
	            try {
	                Product productEntity = extractProductInfo(productElement, query);
	                if (productEntity != null) {
	                    productList.add(productEntity);
	                    productService.saveItems(List.of(productEntity), query);
	                    log.info("상품 저장 완료: {}", productEntity.getTitle());
	                }
	            } catch (NoSuchElementException e) {
	                log.warn("요소를 찾을 수 없음: {}", e.getMessage());
	            }
	        }
	
	    } catch (TimeoutException e) {
	        log.error("페이지 로딩 시간 초과: {}", e.getMessage());
	    } catch (WebDriverException e) {
	        log.error("ChromeDriver 관련 오류 발생: {}", e.getMessage());
	    } catch (Exception e) {
	        log.error("크롤링 중 알 수 없는 오류 발생: {}", e.getMessage());
	    } finally {
	        log.info("ChromeDriver 종료...");
	        driver.quit();
	    }
	
	    return productList;
	}

	/**
	 * 개별 상품 정보를 추출하여 Product 객체를 생성합니다.
	 *
	 * @param productElement 크롤링된 상품 요소
	 * @param query          검색 키워드
	 * @return Product 객체 (추출 실패 시 null 반환)
	 */
	private Product extractProductInfo(WebElement productElement, String query) {
		try {
			String title = productElement.findElement(By.cssSelector(".name")).getText();
			String priceText = productElement.findElement(By.cssSelector(".price-value"))
				.getText().replaceAll("[^0-9]", "");
			Integer price = priceText.isEmpty() ? 0 : Integer.parseInt(priceText);
			String imageUrl = productElement.findElement(By.tagName("img")).getAttribute("src");
			String link = productElement.findElement(By.tagName("a")).getAttribute("href");

			// 쿠팡 상품의 고유 ID 추출
			String productId = extractProductId(link);

			Product product = new Product();
			product.setTitle(title);
			product.setPrice(price);
			product.setImage(imageUrl);
			product.setMallName("Coupang");
			product.setLink(link);
			product.setKeyword(query);
			product.setProductId(productId);

			return product;
		} catch (NoSuchElementException e) {
			log.warn("상품 정보 추출 실패: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 상품 링크에서 고유한 상품 ID를 추출합니다.
	 *
	 * @param link 상품 링크
	 * @return 추출된 상품 ID
	 */
	private String extractProductId(String link) {
		try {
			return link.split("\\?")[0].split("/")[5];
		} catch (ArrayIndexOutOfBoundsException e) {
			log.warn("상품 ID 추출 실패: {}", e.getMessage());
			return "unknown";
		}
	}
}
