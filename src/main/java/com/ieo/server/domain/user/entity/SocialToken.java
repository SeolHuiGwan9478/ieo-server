package com.ieo.server.domain.user.entity;

import com.ieo.server.domain.user.enums.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 소셜 인증을 통과했다는 표시. phoneToken과 같은 역할로, 로그인·가입에 한 번 쓰면 행을 지운다.
 * 클라이언트에 준 값은 저장하지 않고 해시만 저장한다.
 *
 * TODO: 운영 시 phoneToken과 함께 Redis로 변경 예정
 */
@Getter
@Entity
@Table(name = "social_tokens", indexes = @Index(name = "idx_social_tokens_expires_at", columnList = "expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialToken {

	public static final Duration TTL = Duration.ofMinutes(10);

	@Id
	@Column(length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	@Column(nullable = false, length = 100)
	private String providerUserId;

	@Column(length = 200)
	private String email;

	/** 인증받은 기기에서만 쓸 수 있다 */
	@Column(nullable = false, length = 100)
	private String deviceId;

	@Column(nullable = false)
	private Instant expiresAt;

	private SocialToken(String tokenHash, SocialProvider provider, String providerUserId, String email, String deviceId, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.email = email;
		this.deviceId = deviceId;
		this.expiresAt = expiresAt;
	}

	public static SocialToken issue(String tokenHash, SocialProvider provider, String providerUserId, String email, String deviceId, Instant now) {
		return new SocialToken(tokenHash, provider, providerUserId, email, deviceId, now.plus(TTL));
	}

	public boolean isUsableBy(String deviceId, Instant now) {
		return this.deviceId.equals(deviceId) && now.isBefore(expiresAt);
	}
}
