package com.ieo.server.domain.recommendation.controller;

import com.ieo.server.domain.recommendation.dto.AnswerRecommendationRequest;
import com.ieo.server.domain.recommendation.dto.RecommendationAnswerResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationListResponse;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceRequest;
import com.ieo.server.domain.recommendation.dto.RecommendationPreferenceResponse;
import com.ieo.server.domain.recommendation.service.RecommendationService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배차 추천 (03). 차주 계정만 쓸 수 있다 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	/** 지금 운행 다음에 이어서 할 배차 후보 (REC-003, REC-013) */
	@GetMapping("/trips/{tripId}/recommendations")
	public RecommendationListResponse recommend(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return recommendationService.recommend(userId, tripId);
	}

	/** 안내한 추천과 차주의 대답 (REC-020~024). 수락이면 배차 요청이 간다 */
	@PutMapping("/trips/{tripId}/recommendations/{orderId}")
	public RecommendationAnswerResponse answer(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId, @PathVariable Long orderId,
			@RequestBody(required = false) AnswerRecommendationRequest request) {
		return recommendationService.answer(userId, tripId, orderId, request);
	}

	@GetMapping("/recommendation-preference")
	public RecommendationPreferenceResponse getPreference(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return recommendationService.getPreference(userId);
	}

	/** 추천 켜기·끄기, 반경, 최소 실수익 (REC-004, REC-025) */
	@PutMapping("/recommendation-preference")
	public RecommendationPreferenceResponse changePreference(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody RecommendationPreferenceRequest request) {
		return recommendationService.changePreference(userId, request);
	}
}
