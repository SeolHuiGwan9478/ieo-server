package com.ieo.server.domain.recommendation.controller;

import com.ieo.server.domain.recommendation.dto.RecommendationListResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceRequest;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceResponse;
import com.ieo.server.domain.recommendation.service.RecommendationService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배차 추천 (03). 차주 계정만 쓸 수 있다. 추천 목록은 기록하지 않는다 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	/** 차를 세운 자리 근처 추천 3건 (REC-001). 차량 앱이 기어 P·주차 브레이크가 들어온 순간 부른다 */
	@GetMapping("/recommendations")
	public RecommendationListResponse recommend(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @RequestParam double lat, @RequestParam double lng) {
		return recommendationService.recommend(userId, lat, lng);
	}

	@GetMapping("/recommendation-preference")
	public RecommendationPreferenceResponse getPreference(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return recommendationService.getPreference(userId);
	}

	/** 추천 켜기·끄기, 반경, 최소 실수익 (REC-004, REC-005) */
	@PutMapping("/recommendation-preference")
	public RecommendationPreferenceResponse changePreference(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody RecommendationPreferenceRequest request) {
		return recommendationService.changePreference(userId, request);
	}
}
