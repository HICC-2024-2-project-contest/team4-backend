package com.team4.giftidea.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

	private final RestTemplate restTemplate = new RestTemplate();

	@GetMapping("/kream")
	public ResponseEntity<String> proxyToKream(@RequestParam String url) {
		String response = restTemplate.getForObject(url, String.class);
		return ResponseEntity.ok(response);
	}
}