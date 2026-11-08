package com.ieo.server.domain.user.service.social;

import com.ieo.server.domain.user.enums.SocialProvider;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Google ID 토큰 확인. 콘솔이 Google Identity Services로 받은 ID 토큰(JWT)을
 * Google 공개키로 서명 검증하고, 우리 클라이언트 ID로 발급된 것인지 본다. 사용자 ID는 sub 클레임이다.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier implements SocialIdentityVerifier {

	private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

	private final String clientId;
	private final DefaultJWTProcessor<SecurityContext> processor;

	public GoogleIdTokenVerifier(SocialProperties properties) {
		SocialProperties.Google google = properties.google();
		this.clientId = google == null || google.clientId() == null || google.clientId().isBlank() ? null : google.clientId();
		this.processor = clientId == null ? null : buildProcessor(google.jwksUrl(), clientId);
	}

	private static DefaultJWTProcessor<SecurityContext> buildProcessor(String jwksUrl, String clientId) {
		try {
			// 공개키는 받아 두었다가 다시 쓰고, Google이 키를 바꾸면 새로 받는다
			JWKSource<SecurityContext> keys = JWKSourceBuilder.create(URI.create(jwksUrl).toURL()).build();
			DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
			processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
			processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(clientId, null, Set.of("sub", "iss", "exp")));
			return processor;
		} catch (MalformedURLException e) {
			throw new IllegalStateException("ieo.social.google.jwks-url이 올바른 주소가 아닙니다.", e);
		}
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	public boolean isConfigured() {
		return processor != null;
	}

	@Override
	public SocialProfile verify(String idToken, String redirectUri) {
		if (processor == null) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_NOT_CONFIGURED);
		}
		try {
			JWTClaimsSet claims = processor.process(idToken, null);
			if (!ISSUERS.contains(claims.getIssuer())) {
				throw rejected();
			}
			boolean emailVerified = Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"));
			String email = emailVerified ? claims.getStringClaim("email") : null;
			return new SocialProfile(SocialProvider.GOOGLE, claims.getSubject(), email);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			// 토큰 내용은 개인정보라 남기지 않는다
			log.info("Google ID 토큰 확인 실패: {}", e.getClass().getSimpleName());
			throw rejected();
		}
	}

	private static BusinessException rejected() {
		return new BusinessException(ErrorCode.AUTH_SOCIAL_PROVIDER_REJECTED);
	}
}
