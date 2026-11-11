package com.ieo.server.domain.voice.classifier;

/**
 * 판별 결과.
 *
 * @param answer     예, 아니오, 모름
 * @param confidence 0~1. 모델이 얼마나 확신하는지
 */
public record Classification(YesNo answer, double confidence) {

	public static Classification unknown() {
		return new Classification(YesNo.UNKNOWN, 0);
	}
}
