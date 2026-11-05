package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.report.CompanyReportResponse;
import com.ieo.server.domain.broker.dto.report.DriverReportResponse;
import com.ieo.server.domain.broker.dto.report.RouteReportResponse;
import com.ieo.server.domain.broker.dto.report.SiteReportResponse;
import com.ieo.server.domain.broker.service.BrokerReportService;
import com.ieo.server.global.auth.AuthInterceptor;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주선사 운행 데이터 리포트 (BRK-040~044). from·to는 한국 날짜(yyyy-MM-dd, 양 끝 포함)이고 비우면 오늘까지 최근 90일.
 * 소속이 없으면 404 BROKER_404_MEMBER. 요금제 검사(PRO 이상)는 2-8에서 붙는다
 */
@RestController
@RequestMapping("/api/brokers/me/reports")
@RequiredArgsConstructor
public class BrokerReportController {

	private final BrokerReportService reportService;

	/** 노선별 실제 운임·소요 시간 (모든 회사의 완료 운행, 시·군·구 단위) */
	@GetMapping("/routes")
	public RouteReportResponse routes(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reportService.routes(userId, from, to);
	}

	/** 우리가 다니는 상하차지의 대기·작업 시간 */
	@GetMapping("/sites")
	public SiteReportResponse sites(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reportService.sites(userId, from, to);
	}

	/** 내 회사: 오더·배차·완료·취소, 평균 확정 시간, 차량별 이행률, 대기비 */
	@GetMapping("/company")
	public CompanyReportResponse company(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reportService.company(userId, from, to);
	}

	/** 함께 일한 차주의 이행 기록 */
	@GetMapping("/drivers")
	public DriverReportResponse drivers(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reportService.drivers(userId, from, to);
	}
}
