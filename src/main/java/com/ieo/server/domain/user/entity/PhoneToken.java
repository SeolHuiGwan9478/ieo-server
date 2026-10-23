package com.ieo.server.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문자 인증을 통과했다는 표시. 로그인·가입에 한 번 쓰면 행을 지운다.
 * 클라이언트에 준 값은 저장하지 않고 해시만 저장한다. DB가 새어도 토큰으로 쓸 수 없게.
 *
 * TODO: 운영 시 Redis로 변경 예정 (PhoneAuthService 참고)
 */
@Getter
@Entity
@Table(name = "phone_tokens", indexes = @Index(name = "idx_phone_tokens_expires_at", columnList = "expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneToken {

	public static final Duration TTL = Duration.ofMinutes(10);

	@Id
	@Column(length = 64)
	private String tokenHash;

	@Column(nullable = false, length = 11)
	private String phone;

	/** 인증받은 기기에서만 쓸 수 있다 */
	@Column(nullable = false, length = 100)
	private String deviceId;

	@Column(nullable = false)
	private Instant expiresAt;

	private PhoneToken(String tokenHash, String phone, String deviceId, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.phone = phone;
		this.deviceId = deviceId;
		this.expiresAt = expiresAt;
	}

	public static PhoneToken issue(String tokenHash, String phone, String deviceId, Instant now) {
		return new PhoneToken(tokenHash, phone, deviceId, now.plus(TTL));
	}

	public boolean isUsableBy(String deviceId, Instant now) {
		return this.deviceId.equals(deviceId) && now.isBefore(expiresAt);
	}
}
