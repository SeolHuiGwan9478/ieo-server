package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.DriverLinkResponse;
import com.ieo.server.domain.broker.service.DriverLinkService;
import com.ieo.server.global.auth.AuthInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 차량 앱의 단골 주선사 (BRK-002). 차주 계정만 (아니면 403 DRIVER_LINK_403_ROLE).
 * 수락 화면에서 "○○물류 일을 하는 동안 내 위치와 도착 예정 시각이 ○○물류에 공유돼요"를 보여 주고,
 * 수락을 그 회사에 대한 공유 동의로 기록한다. 주행 중에는 화면을 띄우지 않는다 (정차 중 확인).
 */
@RestController
@RequestMapping("/api/driver-links")
@RequiredArgsConstructor
public class DriverLinkController {

	private final DriverLinkService linkService;

	/** 내가 연결된 회사 */
	@GetMapping
	public List<DriverLinkResponse> getLinks(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return linkService.getLinks(userId);
	}

	/** 해제하면 위치 공유와 새 지정 배차가 끝난다 */
	@PostMapping("/{linkId}/unlink")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unlink(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long linkId) {
		linkService.unlink(userId, linkId);
	}

	/** 내 번호로 온 연결 요청 */
	@GetMapping("/invitations")
	public List<DriverLinkResponse> getInvitations(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return linkService.getInvitations(userId);
	}

	@PostMapping("/invitations/{linkId}/accept")
	public DriverLinkResponse accept(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long linkId) {
		return linkService.accept(userId, linkId);
	}

	@PostMapping("/invitations/{linkId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void decline(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long linkId) {
		linkService.decline(userId, linkId);
	}
}
