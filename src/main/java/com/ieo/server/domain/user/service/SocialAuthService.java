package com.ieo.server.domain.user.service;

import com.ieo.server.domain.user.dto.ExistingAccountResponse;
import com.ieo.server.domain.user.dto.SocialVerifyRequest;
import com.ieo.server.domain.user.dto.SocialVerifyResponse;
import com.ieo.server.domain.user.entity.SocialIdentity;
import com.ieo.server.domain.user.entity.SocialToken;
import com.ieo.server.domain.user.enums.SocialProvider;
import com.ieo.server.domain.user.repository.SocialIdentityRepository;
import com.ieo.server.domain.user.repository.SocialTokenRepository;
import com.ieo.server.domain.user.service.social.SocialIdentityVerifier;
import com.ieo.server.domain.user.service.social.SocialProfile;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.Hashes;
import com.ieo.server.global.util.RandomTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 확인. 공급자가 준 토큰을 확인해 socialToken을 발급한다.
 * 실제 로그인·가입은 AuthService가 socialToken을 받아서 한다 (휴대폰 인증의 PhoneAuthService와 같은 역할).
 */
@Service
@Transactional
public class SocialAuthService {

	private final List<SocialIdentityVerifier> verifiers;
	private final SocialTokenRepository socialTokenRepository;
	private final SocialIdentityRepository socialIdentityRepository;
	private final Clock clock;

	public SocialAuthService(List<SocialIdentityVerifier> verifiers, SocialTokenRepository socialTokenRepository,
			SocialIdentityRepository socialIdentityRepository, Clock clock) {
		this.verifiers = verifiers;
		this.socialTokenRepository = socialTokenRepository;
		this.socialIdentityRepository = socialIdentityRepository;
		this.clock = clock;
	}

	/** 공급자 토큰을 확인하고 socialToken을 발급한다. 이 소셜 계정으로 가입한 계정이 있는지도 알려 준다. */
	public SocialVerifyResponse verify(SocialVerifyRequest request) {
		// 호출할 때마다 고른다. 시작 시점에 묶어 두면 테스트에서 가짜로 바꾼 확인기를 못 찾는다
		SocialIdentityVerifier verifier = verifiers.stream()
				.filter(v -> v.provider() == request.provider())
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_SOCIAL_NOT_CONFIGURED));
		if (!verifier.isConfigured()) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_NOT_CONFIGURED);
		}
		SocialProfile profile = verifier.verify(request.token(), request.redirectUri());

		Instant now = clock.instant();
		String rawToken = RandomTokens.urlSafe();
		SocialToken token = socialTokenRepository.save(SocialToken.issue(
				Hashes.sha256Hex(rawToken), profile.provider(), profile.providerUserId(), profile.email(), request.deviceId(), now));

		Optional<SocialIdentity> identity = socialIdentityRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
		return new SocialVerifyResponse(
				rawToken,
				token.getExpiresAt(),
				identity.isPresent(),
				identity.map(SocialIdentity::getUser).map(ExistingAccountResponse::from).orElse(null));
	}

	/**
	 * socialToken을 쓰고 지운다. 없거나, 이미 썼거나, 만료됐거나, 다른 기기면 401.
	 * 확인된 소셜 계정을 돌려준다. 호출한 쪽 트랜잭션이 롤백되면 토큰도 되살아난다.
	 */
	public SocialProfile consumeSocialToken(String rawToken, String deviceId) {
		String tokenHash = Hashes.sha256Hex(rawToken);
		SocialToken token = socialTokenRepository.findById(tokenHash)
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_SOCIAL_TOKEN_INVALID));
		if (socialTokenRepository.deleteByTokenHash(tokenHash) == 0 || !token.isUsableBy(deviceId, clock.instant())) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
		}
		return new SocialProfile(token.getProvider(), token.getProviderUserId(), token.getEmail());
	}
}
