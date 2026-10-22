package com.ieo.server.domain.user.entity;

import com.ieo.server.domain.user.enums.ConsentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 동의 이력. 동의하거나 철회할 때마다 행을 새로 쌓고, 수정하지 않는다.
 * 현재 동의 상태는 종류별 가장 최근 행이다.
 */
@Getter
@Entity
@Table(name = "user_consents", indexes = @Index(name = "idx_user_consents_user_type", columnList = "user_id, type, created_at"))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ConsentType type;

	/** 동의한 약관 버전 (예: 2026-10-01) */
	@Column(nullable = false, length = 20)
	private String version;

	/** true 동의, false 철회 */
	@Column(nullable = false)
	private boolean agreed;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private UserConsent(User user, ConsentType type, String version, boolean agreed) {
		this.user = user;
		this.type = type;
		this.version = version;
		this.agreed = agreed;
	}

	public static UserConsent of(User user, ConsentType type, String version, boolean agreed) {
		return new UserConsent(user, type, version, agreed);
	}
}
