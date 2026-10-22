package com.ieo.server.domain.broker.entity;

import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 주선사 담당자 프로필. users와 1:1이고 users.id를 그대로 PK로 쓴다.
 * 계정 상태(정지·탈퇴)는 User가 관리한다.
 */
@Getter
@Entity
@Table(name = "broker_members", indexes = @Index(name = "idx_broker_members_broker", columnList = "broker_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerMember extends BaseTimeEntity {

	@Id
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private BrokerMemberPosition position;

	private BrokerMember(User user, Broker broker, BrokerMemberPosition position) {
		this.user = user;
		this.broker = broker;
		this.position = position;
	}

	/** 주선사 역할 계정에만 담당자 프로필을 만들 수 있다. */
	public static BrokerMember of(User user, Broker broker, BrokerMemberPosition position) {
		if (user.getRole() != UserRole.BROKER) {
			throw new IllegalArgumentException("주선사 역할 계정이 아닙니다: " + user.getRole());
		}
		return new BrokerMember(user, broker, position);
	}

	public boolean isOwner() {
		return position == BrokerMemberPosition.OWNER;
	}
}
