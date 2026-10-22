package com.ieo.server.domain.driver.entity;

import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차주 프로필. users와 1:1이고, users.id를 그대로 PK로 쓴다.
 * 계정 상태(정지·탈퇴)는 User가 관리한다.
 */
@Getter
@Entity
@Table(name = "drivers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Driver extends BaseTimeEntity {

	@Id
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	private Driver(User user) {
		this.user = user;
	}

	/** 차주 역할 계정에만 차주 프로필을 만들 수 있다. */
	public static Driver of(User user) {
		if (user.getRole() != UserRole.DRIVER) {
			throw new IllegalArgumentException("차주 역할 계정이 아닙니다: " + user.getRole());
		}
		return new Driver(user);
	}
}
