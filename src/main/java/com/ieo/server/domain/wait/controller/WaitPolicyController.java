package com.ieo.server.domain.wait.controller;

import com.ieo.server.domain.wait.dto.WaitPolicyRequest;
import com.ieo.server.domain.wait.dto.WaitPolicyResponse;
import com.ieo.server.domain.wait.service.WaitPolicyService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 주선사의 대기비 정책 (WAIT-010). 주선사 계정만 쓸 수 있다 */
@RestController
@RequestMapping("/api/brokers/me/wait-policy")
@RequiredArgsConstructor
public class WaitPolicyController {

	private final WaitPolicyService waitPolicyService;

	/** 지금 정책. 없으면 204 */
	@GetMapping
	public ResponseEntity<WaitPolicyResponse> getCurrent(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return waitPolicyService.getCurrent(userId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/** 정책 바꾸기 (대표만). 이전 정책은 꺼지고, 이미 시작된 대기는 이전 정책으로 계산된다 */
	@PutMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WaitPolicyResponse replace(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody WaitPolicyRequest request) {
		return waitPolicyService.replace(userId, request);
	}
}
