package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.InviteDriverRequest;
import com.ieo.server.domain.broker.dto.LinkedDriverResponse;
import com.ieo.server.domain.broker.dto.SentDriverInvitationResponse;
import com.ieo.server.domain.broker.service.BrokerDriverService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 우리 회사 단골 차주 (BRK-002). 담당자 누구나 쓸 수 있다.
 * 단골 차주에게만 지정 배차할 수 있고(오더의 targetDriverId = driverUserId),
 * 단골이 우리 운행 중일 때만 위치를 볼 수 있다 (진행 중 운행, 2-5).
 */
@RestController
@RequestMapping("/api/brokers/me")
@RequiredArgsConstructor
public class BrokerDriverController {

	private final BrokerDriverService driverService;

	@GetMapping("/drivers")
	public List<LinkedDriverResponse> getDrivers(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return driverService.getDrivers(userId);
	}

	/** 단골 해제. 확정된 배차·운행은 그대로 */
	@PostMapping("/drivers/{linkId}/unlink")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlink(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long linkId) {
		driverService.unlink(userId, linkId);
	}

	@GetMapping("/driver-invitations")
	public List<SentDriverInvitationResponse> getInvitations(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return driverService.getInvitations(userId);
	}

	/** 번호로 연결 요청. 미가입 번호여도 201 (가입 여부를 알려 주지 않는다) */
	@PostMapping("/driver-invitations")
	@ResponseStatus(HttpStatus.CREATED)
	public SentDriverInvitationResponse invite(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody InviteDriverRequest request) {
		return driverService.invite(userId, request.phone());
	}

	@PostMapping("/driver-invitations/{linkId}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancelInvitation(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long linkId) {
		driverService.cancelInvitation(userId, linkId);
	}
}
