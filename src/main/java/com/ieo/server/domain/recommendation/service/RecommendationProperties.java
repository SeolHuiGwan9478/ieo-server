package com.ieo.server.domain.recommendation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 배차 추천 계산에 쓰는 값 (application.yml의 ieo.recommendation). 가안은 docs/design/04_배차추천.md 3장.
 *
 * @param dieselPriceWon      경유 1L 가격(원). 나중에 오피넷 지역 평균으로 바꾼다
 * @param roadFactor          직선거리에 곱해 도로 거리로 어림하는 값
 * @param averageSpeedKph     공차 이동 시간을 어림하는 평균 속도
 * @param defaultFinishMinutes 하차지 통계가 없을 때 하차 도착부터 끝날 때까지(분)
 * @param longWaitMinutes     상차지 평균 대기가 이 이상이면 "대기 길었던 곳"
 */
@ConfigurationProperties("ieo.recommendation")
public record RecommendationProperties(
		@DefaultValue("1600") int dieselPriceWon,
		@DefaultValue("1.3") double roadFactor,
		@DefaultValue("60") double averageSpeedKph,
		@DefaultValue("60") int defaultFinishMinutes,
		@DefaultValue("60") int longWaitMinutes) {
}
