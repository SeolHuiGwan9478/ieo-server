package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.MyBrokerResponse;
import com.ieo.server.domain.broker.dto.ReceivedInvitationResponse;
import com.ieo.server.domain.broker.service.BrokerInvitationService;
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
 * 내 번호로 온 담당자 초대. 아직 회사 소속이 없는 주선사 계정이 부른다.
 * 콘솔은 GET /api/brokers/me가 404일 때 이 목록을 먼저 보여 주고, 없으면 회사 등록으로 안내한다.
 */
@RestController
@RequestMapping("/api/broker-invitations")
@RequiredArgsConstructor
public class BrokerInvitationController {

	private final BrokerInvitationService invitationService;

	@GetMapping
	public List<ReceivedInvitationResponse> getMine(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return invitationService.getMine(userId);
	}

	/** 수락하면 담당자가 되고 내 회사 정보를 돌려준다 */
	@PostMapping("/{invitationId}/accept")
	public MyBrokerResponse accept(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long invitationId) {
		return invitationService.accept(userId, invitationId);
	}

	@PostMapping("/{invitationId}/decline")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void decline(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long invitationId) {
		invitationService.decline(userId, invitationId);
	}
}
