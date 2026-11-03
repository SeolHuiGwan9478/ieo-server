package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.BrokerMemberResponse;
import com.ieo.server.domain.broker.dto.ChangePositionRequest;
import com.ieo.server.domain.broker.dto.InviteMemberRequest;
import com.ieo.server.domain.broker.dto.SentInvitationResponse;
import com.ieo.server.domain.broker.service.BrokerMemberService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 우리 회사 담당자와 보낸 초대 (BRK-001 담당자 여러 명).
 * 보기는 담당자 모두, 초대·직책 변경·내보내기는 대표만 (아니면 403 BROKER_403_OWNER).
 */
@RestController
@RequestMapping("/api/brokers/me")
@RequiredArgsConstructor
public class BrokerMemberController {

	private final BrokerMemberService memberService;

	@GetMapping("/members")
	public List<BrokerMemberResponse> getMembers(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return memberService.getMembers(userId);
	}

	/** 직책 변경. 마지막 대표를 담당자로 내리면 409 BROKER_409_LAST_OWNER */
	@PatchMapping("/members/{memberUserId}")
	public BrokerMemberResponse changePosition(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long memberUserId,
			@Valid @RequestBody ChangePositionRequest request) {
		return memberService.changePosition(userId, memberUserId, request.position());
	}

	/** 내보내기. 기록은 남고 소속만 끊긴다 */
	@PostMapping("/members/{memberUserId}/remove")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long memberUserId) {
		memberService.remove(userId, memberUserId);
	}

	/** 스스로 회사를 나간다. 마지막 대표면 409 */
	@PostMapping("/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		memberService.leave(userId);
	}

	/** 수락을 기다리는 초대 (만료된 것은 빼고) */
	@GetMapping("/invitations")
	public List<SentInvitationResponse> getInvitations(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return memberService.getInvitations(userId);
	}

	/** 번호로 초대. 7일 동안 유효 */
	@PostMapping("/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	public SentInvitationResponse invite(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody InviteMemberRequest request) {
		return memberService.invite(userId, request.phone());
	}

	@PostMapping("/invitations/{invitationId}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancelInvitation(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long invitationId) {
		memberService.cancelInvitation(userId, invitationId);
	}
}
