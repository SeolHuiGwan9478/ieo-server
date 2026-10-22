package com.ieo.server.domain.user.controller;

import com.ieo.server.domain.user.dto.UpdateUserRequest;
import com.ieo.server.domain.user.dto.UserResponse;
import com.ieo.server.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
