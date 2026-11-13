package com.ieo.server.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 휴대폰 푸시를 받을 기기 (09 알림). 휴대폰 앱이 로그인하면 FCM 토큰을 올린다.
 * 같은 기기에 다른 사람이 로그인하면 토큰의 주인이 바뀐다. 로그아웃하면 지운다
 */
@Getter
@Entity
@Table(name = "push_tokens", indexes = @Index(name = "idx_push_tokens_user", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, unique = true, length = 512)
	private String token;

	@Column(nullable = false)
	private Instant updatedAt;

	private PushToken(Long userId, String token, Instant now) {
		this.userId = userId;
		this.token = token;
		this.updatedAt = now;
	}

	public static PushToken of(Long userId, String token, Instant now) {
		return new PushToken(userId, token, now);
	}

	/** 같은 기기에 다시 로그인했거나 다른 사람이 로그인했다 */
	public void belongTo(Long userId, Instant now) {
		this.userId = userId;
		this.updatedAt = now;
	}
}
