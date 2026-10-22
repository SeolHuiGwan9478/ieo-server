package com.ieo.server.domain.user.entity;

import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차주와 주선사 담당자의 공통 계정. 로그인과 계정 상태를 관리한다.
 * 역할별 정보는 drivers(차주), broker_members(주선사 담당자) 테이블에 따로 둔다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 로그인 아이디. 숫자만 저장한다 (예: 01012345678). 탈퇴하면 null로 지운다. */
	@Column(unique = true, length = 11)
	private String phone;

	@Column(length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	/** 정지·탈퇴 사유 */
	@Column(length = 200)
	private String statusReason;

	private Instant statusChangedAt;

	private Instant lastLoginAt;

	private User(String phone, UserRole role) {
		this.phone = phone;
		this.role = role;
		this.status = UserStatus.ACTIVE;
	}

	public static User create(String phone, UserRole role) {
		return new User(phone, role);
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public void changeName(String name) {
		requireNotWithdrawn();
		this.name = name;
	}

	public void recordLogin(Instant now) {
		if (!isActive()) {
			throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
		}
		this.lastLoginAt = now;
	}

	public void suspend(String reason, Instant now) {
		if (status != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.USER_INVALID_STATUS);
		}
		changeStatus(UserStatus.SUSPENDED, reason, now);
	}

	public void reactivate(Instant now) {
		if (status != UserStatus.SUSPENDED) {
			throw new BusinessException(ErrorCode.USER_INVALID_STATUS);
		}
		changeStatus(UserStatus.ACTIVE, null, now);
	}

	/** 탈퇴. 운행·장부 기록과의 연결을 위해 행은 남기고 개인정보만 지운다. */
	public void withdraw(String reason, Instant now) {
		requireNotWithdrawn();
		this.phone = null;
		this.name = null;
		changeStatus(UserStatus.WITHDRAWN, reason, now);
	}

	private void changeStatus(UserStatus status, String reason, Instant now) {
		this.status = status;
		this.statusReason = reason;
		this.statusChangedAt = now;
	}

	private void requireNotWithdrawn() {
		if (status == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_INVALID_STATUS);
		}
	}
}
