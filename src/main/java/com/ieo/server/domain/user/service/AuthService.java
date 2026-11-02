package com.ieo.server.domain.user.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.dto.ConsentRequest;
import com.ieo.server.domain.user.dto.PhoneLoginRequest;
import com.ieo.server.domain.user.dto.RefreshTokenRequest;
import com.ieo.server.domain.user.dto.SignUpRequest;
import com.ieo.server.domain.user.dto.TokenResponse;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.entity.UserConsent;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserConsentRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.service.VehicleService;
import com.ieo.server.global.auth.JwtProvider;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * phoneToken으로 로그인·가입하고 토큰을 발급한다.
 * 번호가 계정의 열쇠라서, 구글로 가입한 계정도 문자 인증만 통과하면 로그인된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

	private final PhoneAuthService phoneAuthService;
	private final UserRepository userRepository;
	private final UserConsentRepository userConsentRepository;
	private final DriverRepository driverRepository;
	private final VehicleService vehicleService;
	private final JwtProvider jwtProvider;
	private final Clock clock;

	/** "이미 가입된 계정이 있어요" 모달에서 로그인을 누르면 호출한다. */
	public TokenResponse loginWithPhone(PhoneLoginRequest request) {
		String phone = phoneAuthService.consumePhoneToken(request.phoneToken(), request.deviceId());
		User user = userRepository.findByPhone(phone)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		user.recordLogin(clock.instant());
		return jwtProvider.issue(user.getId());
	}

	/** 주선사 담당자는 계정만 만들고, 주선사 소속은 주선사 등록·초대에서 붙인다. */
	public TokenResponse signUp(SignUpRequest request) {
		// 입력을 먼저 확인해서, 고칠 수 있는 실수 때문에 phoneToken을 쓰지 않게 한다
		validateConsents(request.role(), request.consents());
		if (request.vehicle() != null && request.role() != UserRole.DRIVER) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "차주만 가입할 때 차량을 등록할 수 있습니다.");
		}
		String phone = phoneAuthService.consumePhoneToken(request.phoneToken(), request.deviceId());
		if (userRepository.existsByPhone(phone)) {
			throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_REGISTERED);
		}

		Instant now = clock.instant();
		User user = User.create(phone, request.role());
		user.changeName(request.name());
		user.recordLogin(now);
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 같은 번호로 동시에 가입한 경우. users.phone 유니크 제약이 하나만 남긴다
			throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_REGISTERED);
		}

		userConsentRepository.saveAll(request.consents().stream()
				.map(consent -> UserConsent.of(user, consent.type(), consent.version(), consent.agreed()))
				.toList());
		if (request.role() == UserRole.DRIVER) {
			driverRepository.save(Driver.of(user));
		}
		// 번호가 겹치는 등으로 실패하면 가입 전체가 롤백되고 phoneToken도 되살아난다
		if (request.vehicle() != null) {
			vehicleService.register(user.getId(), request.vehicle());
		}
		return jwtProvider.issue(user.getId());
	}

	@Transactional(readOnly = true)
	public TokenResponse refresh(RefreshTokenRequest request) {
		Long userId = jwtProvider.parseRefreshToken(request.refreshToken());
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
		}
		return jwtProvider.issue(userId);
	}

	private void validateConsents(UserRole role, List<ConsentRequest> consents) {
		Map<ConsentType, ConsentRequest> byType;
		try {
			byType = consents.stream().collect(Collectors.toMap(ConsentRequest::type, Function.identity()));
		} catch (IllegalStateException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 약관이 두 번 들어왔습니다.");
		}
		boolean missingRequired = Arrays.stream(ConsentType.values())
				.filter(type -> type.isRequiredFor(role))
				.anyMatch(type -> byType.get(type) == null || !byType.get(type).agreed());
		if (missingRequired) {
			throw new BusinessException(ErrorCode.AUTH_REQUIRED_CONSENT_MISSING);
		}
	}
}
