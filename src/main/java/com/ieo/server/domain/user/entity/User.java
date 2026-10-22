package com.ieo.server.domain.user.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ieo 사용자. 휴대폰 번호로 가입하고 로그인한다. (요구사항: docs/requirements/08_계정_차량.md)
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 숫자만 저장한다. 예: 01012345678 */
	@Column(nullable = false, unique = true, length = 11)
	private String phone;

	@Column(length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	/** 위치 수집 동의 (ACC-002, 필수) */
	@Column(nullable = false)
	private boolean consentLocation;

	/** 주선사에 위치·운행 정보 공유 동의 (ACC-002, 선택) */
	@Column(nullable = false)
	private boolean consentBrokerShare;

	/** 음성 데이터 처리 동의 (ACC-002, 선택) */
	@Column(nullable = false)
	private boolean consentVoice;

	private User(String phone, UserRole role) {
		this.phone = phone;
		this.role = role;
	}

	public static User createDriver(String phone) {
		return new User(phone, UserRole.DRIVER);
	}

	public void changeName(String name) {
		this.name = name;
	}

	public void changeConsents(boolean location, boolean brokerShare, boolean voice) {
		this.consentLocation = location;
		this.consentBrokerShare = brokerShare;
		this.consentVoice = voice;
	}
}
