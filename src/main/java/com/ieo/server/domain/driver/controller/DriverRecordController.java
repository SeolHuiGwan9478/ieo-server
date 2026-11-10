package com.ieo.server.domain.driver.controller;

import com.ieo.server.domain.driver.dto.DriverRecordResponse;
import com.ieo.server.domain.driver.service.DriverRecordService;
import com.ieo.server.global.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** 차주 본인의 이력: 인증 마크와 주선사에게 보이는 운행·취소 수 */
@RestController
@RequiredArgsConstructor
public class DriverRecordController {

	private final DriverRecordService driverRecordService;

	@GetMapping("/api/drivers/me/record")
	public DriverRecordResponse record(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return driverRecordService.mine(userId);
	}
}
