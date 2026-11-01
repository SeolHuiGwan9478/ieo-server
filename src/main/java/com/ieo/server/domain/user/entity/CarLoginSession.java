package com.ieo.server.domain.user.entity;

import com.ieo.server.domain.user.enums.CarLoginStatus;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차량 QR 로그인 요청. 로그인 전인 차량이 만들고, 로그인된 휴대폰이 승인한다.
 *
 * code는 화면·QR에 보이는 공개 값으로, 휴대폰이 승인할 세션을 가리킬 때 쓴다.
 * pollSecret은 세션을 만든 차량만 아는 값으로, 토큰을 받아 갈 때 쓴다. 해시만 저장한다.
 *
 * TODO: 운영 시 Redis로 변경 예정 (차량이 2초마다 조회하므로 가장 먼저 옮길 대상)
 */
@Getter
@Entity
@Table(name = "car_login_sessions", indexes = @Index(name = "idx_car_login_sessions_expires_at", columnList = "expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarLoginSession extends BaseTimeEntity {

	/** 승인을 기다리는 시간 */
	public static final Duration TTL = Duration.ofMinutes(5);
	/** 승인된 뒤 차량이 토큰을 받아 가야 하는 시간. 만료 직전에 승인돼도 받아 갈 수 있게 따로 둔다 */
	public static final Duration CONSUME_WINDOW = Duration.ofMinutes(1);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** 화면에 보이는 코드 (예: KX7P-M4QZ) */
	@Column(nullable = false, unique = true, length = 9)
	private String code;

	@Column(nullable = false, length = 64)
	private String pollSecretHash;

	/** 차량 앱 설치 ID */
	@Column(nullable = false, length = 100)
	private String deviceId;

	/** 승인 화면과 로그인된 차량 목록에 보여 줄 이름 */
	@Column(length = 50)
	private String deviceName;

	@Column(length = 20)
	private String appVersion;

	@Column(length = 45)
	private String requestIp;

	/** 요청 IP로 추정한 대략 위치. 모르면 null */
	@Column(length = 50)
	private String approxLocation;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private CarLoginStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approved_user_id")
	private User approvedUser;

	/** 승인하거나 거절한 시각 */
	private Instant decidedAt;

	/** 차량이 토큰을 받아 간 시각 */
	private Instant consumedAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private CarLoginSession(String code, String pollSecretHash, String deviceId, String deviceName,
			String appVersion, String requestIp, String approxLocation, Instant expiresAt) {
		this.code = code;
		this.pollSecretHash = pollSecretHash;
		this.deviceId = deviceId;
		this.deviceName = deviceName;
		this.appVersion = appVersion;
		this.requestIp = requestIp;
		this.approxLocation = approxLocation;
		this.status = CarLoginStatus.PENDING;
		this.expiresAt = expiresAt;
	}

	public static CarLoginSession create(String code, String pollSecretHash, String deviceId, String deviceName,
			String appVersion, String requestIp, String approxLocation, Instant now) {
		return new CarLoginSession(code, pollSecretHash, deviceId, deviceName, appVersion, requestIp, approxLocation,
				now.plus(TTL));
	}

	/** 지금 시각 기준 상태. 저장된 상태에 만료를 더해서 판단한다. */
	public CarLoginStatus statusAt(Instant now) {
		if (status == CarLoginStatus.PENDING && !now.isBefore(expiresAt)) {
			return CarLoginStatus.EXPIRED;
		}
		if (status == CarLoginStatus.APPROVED && !now.isBefore(decidedAt.plus(CONSUME_WINDOW))) {
			return CarLoginStatus.EXPIRED;
		}
		return status;
	}

	public boolean matchesPollSecret(String pollSecretHash) {
		return MessageDigest.isEqual(
				this.pollSecretHash.getBytes(StandardCharsets.UTF_8),
				pollSecretHash.getBytes(StandardCharsets.UTF_8));
	}

	/** 휴대폰에서 승인. 승인한 사용자로 차량이 로그인된다. */
	public void approve(User user, Instant now) {
		requirePending(now);
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
		}
		this.status = CarLoginStatus.APPROVED;
		this.approvedUser = user;
		this.decidedAt = now;
	}

	public void reject(Instant now) {
		requirePending(now);
		this.status = CarLoginStatus.REJECTED;
		this.decidedAt = now;
	}

	/** 차량이 토큰을 받아 간다. 승인된 세션에서 한 번만 된다. 토큰을 발급할 사용자를 돌려준다. */
	public User consume(Instant now) {
		if (statusAt(now) != CarLoginStatus.APPROVED) {
			throw new BusinessException(ErrorCode.CAR_LOGIN_NOT_APPROVED);
		}
		this.status = CarLoginStatus.CONSUMED;
		this.consumedAt = now;
		return approvedUser;
	}

	private void requirePending(Instant now) {
		CarLoginStatus current = statusAt(now);
		if (current == CarLoginStatus.EXPIRED) {
			throw new BusinessException(ErrorCode.CAR_LOGIN_EXPIRED);
		}
		if (current != CarLoginStatus.PENDING) {
			throw new BusinessException(ErrorCode.CAR_LOGIN_ALREADY_DECIDED);
		}
	}
}
