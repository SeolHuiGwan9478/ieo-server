package com.ieo.server.domain.recommendation.service;

import java.util.Map;

/** 시도별 경유 평균가격을 어디선가 받아 온다. 구현은 오피넷({@link OpinetDieselPriceSource}) */
public interface DieselPriceSource {

	/** 시도 이름("서울", "전국" 등) → 1L 가격(원). 못 받으면 빈 맵. 예외를 던지지 않는다 */
	Map<String, Integer> fetch();
}
