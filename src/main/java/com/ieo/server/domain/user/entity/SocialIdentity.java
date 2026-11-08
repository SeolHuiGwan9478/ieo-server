package com.ieo.server.domain.user.entity;

import com.ieo.server.domain.user.enums.SocialProvider;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 소셜 계정과 이에오 계정의 연결. 공급자가 주는 사용자 ID(Google sub, 카카오 id)로 어느 계정인지 찾는다.
 * 계정 하나에 공급자별로 하나씩 붙을 수 있다. 탈퇴하면 지운다.
 */
@Getter
@Entity
@Table(name = "social_identities", uniqueConstraints = @UniqueConstraint(name = "uk_social_identities_provider_user", columnNames = {"provider", "provider_user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialIdentity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 100)
	private String providerUserId;

	/** 공급자가 알려 준 이메일. 없을 수 있다 (카카오는 동의해야 준다) */
	@Column(length = 200)
	private String email;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	private SocialIdentity(SocialProvider provider, String providerUserId, String email, User user) {
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.email = email;
		this.user = user;
	}

	public static SocialIdentity link(User user, SocialProvider provider, String providerUserId, String email) {
		return new SocialIdentity(provider, providerUserId, email, user);
	}
}
