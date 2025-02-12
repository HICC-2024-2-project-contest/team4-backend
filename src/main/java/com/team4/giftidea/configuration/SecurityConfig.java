package com.team4.giftidea.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.List;

/**
 * Spring Security 및 CORS 설정을 담당하는 설정 클래스입니다.
 */
@Configuration
@RestController
@RequestMapping("/api") // 모든 API 요청 처리
public class SecurityConfig {

    /**
     * HTTP 보안 설정을 구성하는 Bean입니다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // CORS 적용
                .csrf(csrf -> csrf.disable()) // CSRF 비활성화
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").authenticated() // "/admin/**" 경로는 인증 필요
                        .anyRequest().permitAll() // 나머지 요청은 인증 없이 허용
                );

        return http.build();
    }

    /**
     * CORS 설정을 구성하는 Bean입니다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 출처 설정
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5174",
                "http://localhost:3000",
                "https://presentalk.store",
                "https://app.presentalk.store"
        ));
        
        // 허용할 HTTP 메서드 설정
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 허용할 요청 헤더 설정
        configuration.setAllowedHeaders(List.of("*")); // 모든 헤더 허용

        // 쿠키 포함 요청 허용
        configuration.setAllowCredentials(true);

        // CORS 설정을 특정 경로에 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * CORS 필터가 Spring Security보다 먼저 실행되도록 설정
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        FilterRegistrationBean<CorsFilter> filterBean = new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource()));
        filterBean.setOrder(0); // 가장 먼저 실행되도록 설정
        return filterBean;
    }

    /**
     * OPTIONS 요청을 수동으로 처리 (CORS 문제 해결)
     */
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok().build();
    }
}
