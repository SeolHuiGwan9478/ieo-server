package com.ieo.server.domain.plan.controller;

import com.ieo.server.domain.plan.dto.ChooseDriverPlanRequest;
import com.ieo.server.domain.plan.dto.DriverPlanResponse;
import com.ieo.server.domain.plan.service.DriverPlanService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기사님 플랜 (12장). 결제는 데모 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plans/me")
public class DriverPlanController {

	private final DriverPlanService driverPlanService;

	@GetMapping
	public DriverPlanResponse getMine(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return driverPlanService.getMine(userId);
	}

	@PutMapping
	public DriverPlanResponse choose(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@Valid @RequestBody ChooseDriverPlanRequest request) {
		return driverPlanService.choose(userId, request.plan());
	}

	@PostMapping("/trial")
	public DriverPlanResponse startTrial(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return driverPlanService.startTrial(userId);
	}

	@PostMapping("/resume")
	public DriverPlanResponse resume(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return driverPlanService.resume(userId);
	}
}
