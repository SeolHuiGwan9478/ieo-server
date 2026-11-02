package com.ieo.server.domain.wait.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.util.RandomTokens;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대기 증빙 웹 링크 (WAIT-020, 022). 주선사가 로그인 없이 /proof/{shareToken}으로 본다.
 * 링크만 알면 볼 수 있으므로 토큰은 추측할 수 없는 무작위 값(32바이트)으로 만든다.
 * PDF(WAIT-021)는 P2라 나중에 붙인다.
 */
@Getter
@Entity
@Table(name = "wait_proofs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitProof extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wait_record_id", unique = true)
	private WaitRecord waitRecord;

	@Column(nullable = false, unique = true, length = 43)
	private String shareToken;

	/** 링크 만료 시각. 비어 있으면 만료 없음 */
	private Instant expiresAt;

	/** 주선사에게 보낸 시각 */
	private Instant sentAt;

	private WaitProof(WaitRecord waitRecord, String shareToken, Instant expiresAt) {
		this.waitRecord = waitRecord;
		this.shareToken = shareToken;
		this.expiresAt = expiresAt;
	}

	public static WaitProof create(WaitRecord waitRecord, Instant expiresAt) {
		return new WaitProof(waitRecord, RandomTokens.urlSafe(), expiresAt);
	}

	public void markSent(Instant now) {
		this.sentAt = now;
	}

	public boolean isExpired(Instant now) {
		return expiresAt != null && !now.isBefore(expiresAt);
	}
}
