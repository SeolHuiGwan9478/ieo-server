package com.ieo.server.domain.recommendation.enums;

/** 안내한 추천에 차주가 한 대답 (REC-020~023). 대답이 없으면 null */
public enum RecommendationAnswer {
	/** 다음 후보를 듣겠다 */
	NEXT,
	/** 이 건은 싫다. 한 운행에서 3번이면 그 운행에는 더 추천하지 않는다 (REC-024) */
	REJECTED,
	/** 수락. 배차 요청을 보낸다 */
	ACCEPTED
}
