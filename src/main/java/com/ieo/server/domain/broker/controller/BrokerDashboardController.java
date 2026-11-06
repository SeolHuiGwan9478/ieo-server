package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.BrokerDashboardResponse;
import com.ieo.server.domain.broker.enums.DashboardUnit;
import com.ieo.server.domain.broker.service.BrokerDashboardService;
import com.ieo.server.global.auth.AuthInterceptor;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 콘솔 첫 화면 대시보드. 모든 플랜. unit은 DAY·WEEK·MONTH (기본 MONTH), from·to는 한국 날짜(양 끝 포함).
 * from을 비우면 unit별 기본 기간 (30일, 12주, 6개월), to를 비우면 오늘. 소속이 없으면 404 BROKER_404_MEMBER
 */
@RestController
@RequiredArgsConstructor
public class BrokerDashboardController {

	private final BrokerDashboardService dashboardService;

	@GetMapping("/api/brokers/me/dashboard")
	public BrokerDashboardResponse get(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(defaultValue = "MONTH") DashboardUnit unit,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return dashboardService.get(userId, unit, from, to);
	}
}
