package com.ieo.server.domain.user.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 번호별 문자 인증 상태. 번호 하나에 행 하나를 두고 인증번호·발송 횟수·잠금을 같이 관리한다.
 * 인증번호는 해시만 저장한다.
 *
 * TODO: 운영 시 Redis로 변경 예정 (PhoneAuthService 참고)
 */
@Getter
@Entity
@Table(name = "phone_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification extends BaseTimeEntity {

	public static final Duration CODE_TTL = Duration.ofMinutes(3);
	public static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
	public static final int DAILY_SEND_LIMIT = 5;
	public static final int MAX_FAILED_ATTEMPTS = 5;
	public static final Duration LOCK_DURATION = Duration.ofMinutes(10);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 11)
	private String phone;

	/** 지금 유효한 인증번호의 해시. 맞히거나 만료되면 null */
	@Column(length = 64)
	private String codeHash;

	private Instant codeExpiresAt;

	@Column(nullable = false)
	private int failedAttempts;

	/** 발송 횟수를 센 날 (한국 날짜) */
	private LocalDate sendDay;

	@Column(nullable = false)
	private int sendCount;

	private Instant lastSentAt;

	private Instant lockedUntil;

	private PhoneVerification(String phone) {
		this.phone = phone;
	}

	public static PhoneVerification create(String phone) {
		return new PhoneVerification(phone);
	}

	/** 새 인증번호로 바꾼다. 1분에 한 번, 하루 5번까지. */
	public void issueCode(String codeHash, Instant now, LocalDate today) {
		ensureNotLocked(now);
		if (lastSentAt != null && now.isBefore(lastSentAt.plus(RESEND_INTERVAL))) {
			throw new BusinessException(ErrorCode.AUTH_CODE_TOO_SOON);
		}
		int todayCount = today.equals(sendDay) ? sendCount : 0;
		if (todayCount >= DAILY_SEND_LIMIT) {
			throw new BusinessException(ErrorCode.AUTH_CODE_DAILY_LIMIT);
		}
		this.codeHash = codeHash;
		this.codeExpiresAt = now.plus(CODE_TTL);
		this.failedAttempts = 0;
		this.sendDay = today;
		this.sendCount = todayCount + 1;
		this.lastSentAt = now;
	}

	/** 맞으면 인증번호를 지워 다시 쓸 수 없게 한다. 5번 틀리면 10분 동안 잠근다. */
	public void verify(String codeHash, Instant now) {
		ensureNotLocked(now);
		if (this.codeHash == null || !now.isBefore(codeExpiresAt)) {
			clearCode();
			throw new BusinessException(ErrorCode.AUTH_CODE_EXPIRED);
		}
		if (!MessageDigest.isEqual(
				this.codeHash.getBytes(StandardCharsets.UTF_8),
				codeHash.getBytes(StandardCharsets.UTF_8))) {
			failedAttempts++;
			int remaining = MAX_FAILED_ATTEMPTS - failedAttempts;
			if (remaining <= 0) {
				clearCode();
				lockedUntil = now.plus(LOCK_DURATION);
				throw new BusinessException(ErrorCode.AUTH_CODE_LOCKED);
			}
			throw new BusinessException(ErrorCode.AUTH_CODE_MISMATCH,
					"인증번호가 맞지 않습니다. " + remaining + "번 더 틀리면 10분 동안 막힙니다.");
		}
		clearCode();
	}

	private void ensureNotLocked(Instant now) {
		if (lockedUntil != null && now.isBefore(lockedUntil)) {
			throw new BusinessException(ErrorCode.AUTH_CODE_LOCKED);
		}
	}

	private void clearCode() {
		this.codeHash = null;
		this.codeExpiresAt = null;
		this.failedAttempts = 0;
	}
}
