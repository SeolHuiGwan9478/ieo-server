package com.ieo.server.domain.user.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인된 차량 한 대. 차량에 준 토큰에는 이 id가 들어 있어서, 끊으면 그 차량의 토큰만 바로 막힌다 (ACC-005).
 * 휴대폰 토큰은 이 표와 상관없다.
 */
@Getter
@Entity
@Table(name = "car_sessions", indexes = @Index(name = "idx_car_sessions_user", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarSession extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	/** 차량 앱 설치 ID. 같은 차에서 다시 로그인하면 전 세션을 끝낸다 */
	@Column(nullable = false, length = 100)
	private String deviceId;

	@Column(length = 50)
	private String deviceName;

	@Column(length = 20)
	private String appVersion;

	/** 토큰을 새로 받아 간 마지막 시각 */
	@Column(nullable = false)
	private Instant lastUsedAt;

	private Instant revokedAt;

	private CarSession(User user, String deviceId, String deviceName, String appVersion, Instant now) {
		this.user = user;
		this.deviceId = deviceId;
		this.deviceName = deviceName;
		this.appVersion = appVersion;
		this.lastUsedAt = now;
	}

	public static CarSession open(User user, String deviceId, String deviceName, String appVersion, Instant now) {
		return new CarSession(user, deviceId, deviceName, appVersion, now);
	}

	public void touch(Instant now) {
		this.lastUsedAt = now;
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}

	public boolean isActive() {
		return revokedAt == null;
	}
}
