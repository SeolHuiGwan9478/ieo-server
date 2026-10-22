package com.ieo.server.domain.user.controller;

import com.ieo.server.domain.user.dto.RefreshTokenRequest;
import com.ieo.server.domain.user.dto.SendCodeRequest;
import com.ieo.server.domain.user.dto.TokenResponse;
import com.ieo.server.domain.user.dto.VerifyCodeRequest;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴대폰 인증번호 로그인. (요구사항: ACC-001)
 *
 * TODO: 인증번호 발송·확인과 토큰 발급은 아직 구현하지 않았다. 지금은 모두 501을 돌려준다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	/** 휴대폰 번호로 인증번호를 보낸다. */
	@PostMapping("/code")
	public void sendCode(@Valid @RequestBody SendCodeRequest request) {
		throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
	}

	/** 인증번호를 확인하고 토큰을 발급한다. 처음 로그인하는 번호면 차주로 가입시킨다. */
	@PostMapping("/verify")
	public TokenResponse verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
		throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
	}

	/** 리프레시 토큰으로 액세스 토큰을 다시 발급한다. */
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
	}
}
