package com.ieo.server.global.auth;

import com.ieo.server.domain.user.dto.TokenResponse;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 로그인 뒤에 쓰는 액세스·리프레시 토큰 (HS256 JWT).
 * 로그인 전에 쓰는 phoneToken은 JWT가 아니다. (PhoneVerificationStore 참고)
 */
@Component
public class JwtProvider {

	private static final String TYPE_CLAIM = "typ";
	private static final String CAR_SESSION_CLAIM = "csid";

	private enum TokenType { ACCESS, REFRESH }

	private final JwtProperties properties;
	private final Clock clock;
	private final JWSSigner signer;
	private final JWSVerifier verifier;

	public JwtProvider(JwtProperties properties, Clock clock) {
		byte[] key = properties.secret().getBytes(StandardCharsets.UTF_8);
		try {
			this.signer = new MACSigner(key);
			this.verifier = new MACVerifier(key);
		} catch (JOSEException e) {
			throw new IllegalStateException("ieo.jwt.secret은 32바이트 이상이어야 합니다.", e);
		}
		this.properties = properties;
		this.clock = clock;
	}

	public TokenResponse issue(Long userId) {
		return issue(userId, null);
	}

	/** 차량에 주는 토큰. carSessionId를 넣어 차량별로 끊을 수 있게 한다 */
	public TokenResponse issue(Long userId, UUID carSessionId) {
		Instant now = clock.instant();
		String accessToken = sign(userId, TokenType.ACCESS, carSessionId, now, now.plus(properties.accessTokenTtl()));
		String refreshToken = sign(userId, TokenType.REFRESH, carSessionId, now, now.plus(properties.refreshTokenTtl()));
		return new TokenResponse(accessToken, refreshToken);
	}

	/** 인증 인터셉터에서 쓴다. 올바르지 않거나 만료되면 401. */
	public TokenClaims parseAccessToken(String token) {
		return parse(token, TokenType.ACCESS);
	}

	public TokenClaims parseRefreshToken(String token) {
		return parse(token, TokenType.REFRESH);
	}

	private String sign(Long userId, TokenType type, UUID carSessionId, Instant issuedAt, Instant expiresAt) {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(userId.toString())
				.claim(TYPE_CLAIM, type.name())
				.claim(CAR_SESSION_CLAIM, carSessionId == null ? null : carSessionId.toString())
				.jwtID(UUID.randomUUID().toString())
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(expiresAt))
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		try {
			jwt.sign(signer);
		} catch (JOSEException e) {
			throw new IllegalStateException("토큰 서명 실패", e);
		}
		return jwt.serialize();
	}

	private TokenClaims parse(String token, TokenType expectedType) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
				throw invalid();
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			Date expiresAt = claims.getExpirationTime();
			if (expiresAt == null || !clock.instant().isBefore(expiresAt.toInstant())) {
				throw invalid();
			}
			if (!expectedType.name().equals(claims.getStringClaim(TYPE_CLAIM))) {
				throw invalid();
			}
			String carSessionId = claims.getStringClaim(CAR_SESSION_CLAIM);
			return new TokenClaims(Long.valueOf(claims.getSubject()), carSessionId == null ? null : UUID.fromString(carSessionId));
		} catch (ParseException | JOSEException | IllegalArgumentException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
	}
}
