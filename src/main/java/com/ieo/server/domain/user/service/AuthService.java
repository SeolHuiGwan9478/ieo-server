package com.ieo.server.domain.user.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.dto.ConsentRequest;
import com.ieo.server.domain.user.dto.PhoneLoginRequest;
import com.ieo.server.domain.user.dto.RefreshTokenRequest;
import com.ieo.server.domain.user.dto.SignUpRequest;
import com.ieo.server.domain.user.dto.SocialLoginRequest;
import com.ieo.server.domain.user.dto.TokenResponse;
import com.ieo.server.domain.user.entity.SocialIdentity;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.entity.UserConsent;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.SocialIdentityRepository;
import com.ieo.server.domain.user.repository.UserConsentRepository;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.user.service.social.SocialProfile;
import com.ieo.server.domain.vehicle.service.VehicleService;
import com.ieo.server.global.auth.JwtProvider;
import com.ieo.server.global.auth.TokenClaims;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * phoneToken 또는 socialToken으로 로그인·가입하고 토큰을 발급한다.
 * 번호가 계정의 열쇠다. 소셜 로그인은 번호 계정에 붙은 소셜 계정(social_identities)으로 그 계정을 찾는 또 다른 문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

	private final PhoneAuthService phoneAuthService;
	private final SocialAuthService socialAuthService;
	private final UserRepository userRepository;
	private final SocialIdentityRepository socialIdentityRepository;
	private final UserConsentRepository userConsentRepository;
	private final DriverRepository driverRepository;
	private final VehicleService vehicleService;
	private final JwtProvider jwtProvider;
	private final CarSessionService carSessionService;
	private final Clock clock;

	/** "이미 가입된 계정이 있어요" 모달에서 로그인을 누르면 호출한다. */
	public TokenResponse loginWithPhone(PhoneLoginRequest request) {
		String phone = phoneAuthService.consumePhoneToken(request.phoneToken(), request.deviceId());
		User user = userRepository.findByPhone(phone)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		user.recordLogin(clock.instant());
		if (request.carDeviceName() != null) {
			return jwtProvider.issue(user.getId(), carSessionService.open(user, request.deviceId(), request.carDeviceName(), request.appVersion()));
		}
		return jwtProvider.issue(user.getId());
	}

	/** 소셜 계정으로 가입한 사람이 socialToken으로 로그인한다. */
	public TokenResponse loginWithSocial(SocialLoginRequest request) {
		SocialProfile profile = socialAuthService.consumeSocialToken(request.socialToken(), request.deviceId());
		User user = socialIdentityRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
				.map(SocialIdentity::getUser)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		user.recordLogin(clock.instant());
		return jwtProvider.issue(user.getId());
	}

	/**
	 * 주선사 담당자는 계정만 만들고, 주선사 소속은 주선사 등록·초대에서 붙인다.
	 * 소셜로 들어온 사람(socialToken)도 번호 인증을 거친다. 그 번호로 가입한 계정이 이미 있으면
	 * 새로 만들지 않고 그 계정에 소셜 계정을 연결한 뒤 로그인시킨다.
	 */
	public TokenResponse signUp(SignUpRequest request) {
		// 입력을 먼저 확인해서, 고칠 수 있는 실수 때문에 인증 토큰을 쓰지 않게 한다
		validateConsents(request.role(), request.consents());
		if (request.vehicle() != null && request.role() != UserRole.DRIVER) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "차주만 가입할 때 차량을 등록할 수 있습니다.");
		}
		SocialProfile profile = request.hasSocialToken() ? consumeSocialProfile(request) : null;
		String phone = phoneAuthService.consumePhoneToken(request.phoneToken(), request.deviceId());
		Instant now = clock.instant();

		Optional<User> registered = userRepository.findByPhone(phone);
		if (registered.isPresent()) {
			if (profile == null) {
				throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_REGISTERED);
			}
			// 번호로 이미 가입한 사람이 소셜로 처음 들어온 경우: 그 계정에 소셜 계정을 붙인다
			User user = registered.get();
			linkSocialIdentity(user, profile);
			user.recordLogin(now);
			return jwtProvider.issue(user.getId());
		}

		User user = User.create(phone, request.role());
		user.changeName(request.name());
		user.recordLogin(now);
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 같은 번호로 동시에 가입한 경우. users.phone 유니크 제약이 하나만 남긴다
			throw new BusinessException(ErrorCode.AUTH_PHONE_ALREADY_REGISTERED);
		}
		if (profile != null) {
			linkSocialIdentity(user, profile);
		}

		userConsentRepository.saveAll(request.consents().stream()
				.map(consent -> UserConsent.of(user, consent.type(), consent.version(), consent.agreed()))
				.toList());
		if (request.role() == UserRole.DRIVER) {
			driverRepository.save(Driver.of(user));
		}
		// 번호가 겹치는 등으로 실패하면 가입 전체가 롤백되고 인증 토큰도 되살아난다
		if (request.vehicle() != null) {
			vehicleService.register(user.getId(), request.vehicle());
		}
		return jwtProvider.issue(user.getId());
	}

	/** 소셜 계정이 다른 이에오 계정에 이미 붙어 있으면 가입·연결할 수 없다 */
	private SocialProfile consumeSocialProfile(SignUpRequest request) {
		SocialProfile profile = socialAuthService.consumeSocialToken(request.socialToken(), request.deviceId());
		if (socialIdentityRepository.existsByProviderAndProviderUserId(profile.provider(), profile.providerUserId())) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_ALREADY_REGISTERED);
		}
		return profile;
	}

	private void linkSocialIdentity(User user, SocialProfile profile) {
		try {
			socialIdentityRepository.saveAndFlush(SocialIdentity.link(user, profile.provider(), profile.providerUserId(), profile.email()));
		} catch (DataIntegrityViolationException e) {
			// 같은 소셜 계정으로 동시에 가입한 경우. 유니크 제약이 하나만 남긴다
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_ALREADY_REGISTERED);
		}
	}

	@Transactional(readOnly = true)
	public TokenResponse refresh(RefreshTokenRequest request) {
		TokenClaims claims = jwtProvider.parseRefreshToken(request.refreshToken());
		Long userId = claims.userId();
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
		}
		if (claims.carSessionId() != null) {
			carSessionService.touch(claims.carSessionId(), userId);
		}
		return jwtProvider.issue(userId, claims.carSessionId());
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
