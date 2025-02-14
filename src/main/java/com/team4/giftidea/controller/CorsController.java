package com.team4.giftidea.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CorsController {

	@RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
	public ResponseEntity<?> handleOptions() {
		return ResponseEntity.ok().build();
	}
}