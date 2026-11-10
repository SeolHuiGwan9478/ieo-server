package com.ieo.server.domain.user.controller;

import com.ieo.server.domain.user.dto.ChangePhoneRequest;
import com.ieo.server.domain.user.dto.UpdateUserRequest;
import com.ieo.server.domain.user.dto.UserResponse;
import com.ieo.server.domain.user.dto.WithdrawRequest;
import com.ieo.server.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * userId는 인증 인터셉터가 JWT를 파싱해 요청 attribute로 넣어 준다. (인터셉터는 아직 없음)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public UserResponse getMe(@RequestAttribute("userId") Long userId) {
		return userService.getUser(userId);
	}

	@PatchMapping("/me")
	public UserResponse updateMe(@RequestAttribute("userId") Long userId, @Valid @RequestBody UpdateUserRequest request) {
		return userService.updateUser(userId, request);
	}

	/** 탈퇴. 개인정보를 지우고 되돌릴 수 없다. */
	/** 휴대폰 번호 바꾸기 (ACC-008): 새 번호를 /api/auth/phone/code·verify로 인증하고 받은 phoneToken을 보낸다 */
	@PostMapping("/me/phone")
	public UserResponse changePhone(@RequestAttribute("userId") Long userId, @Valid @RequestBody ChangePhoneRequest request) {
		return userService.changePhone(userId, request);
	}

	@PostMapping("/me/withdraw")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@RequestAttribute("userId") Long userId, @Valid @RequestBody(required = false) WithdrawRequest request) {
		userService.withdraw(userId, request);
	}
}
