package com.ieo.server.domain.site.dto;

import java.math.BigDecimal;

/** 내 기본 차량 높이와 상하차지 높이 제한을 비교한다 (VEH-002) */
public record HeightWarning(HeightWarningLevel level, BigDecimal vehicleHeightM, BigDecimal limitM) {

	static final BigDecimal CAUTION_MARGIN_M = new BigDecimal("0.20");

	public static HeightWarning of(BigDecimal vehicleHeightM, BigDecimal limitM) {
		if (vehicleHeightM == null || limitM == null) {
			return new HeightWarning(HeightWarningLevel.UNKNOWN, vehicleHeightM, limitM);
		}
		BigDecimal margin = limitM.subtract(vehicleHeightM);
		HeightWarningLevel level;
		if (margin.signum() <= 0) {
			level = HeightWarningLevel.BLOCKED;
		} else if (margin.compareTo(CAUTION_MARGIN_M) < 0) {
			level = HeightWarningLevel.CAUTION;
		} else {
			level = HeightWarningLevel.OK;
		}
		return new HeightWarning(level, vehicleHeightM, limitM);
	}
}
