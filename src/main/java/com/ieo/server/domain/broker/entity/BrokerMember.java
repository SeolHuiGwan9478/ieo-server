package com.ieo.server.domain.broker.entity;

import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 주선사 담당자 프로필. users와 1:1이고 users.id를 그대로 PK로 쓴다.
 * 계정 상태(정지·탈퇴)는 User가 관리한다.
 * 내보내거나 스스로 나가면 행은 남기고(오더의 등록 담당자 기록) removedAt을 찍는다.
 * 계정당 한 행이라 나온 계정은 다른 회사에 다시 합류할 수 없다 (탈퇴 후 다시 가입).
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

	/** 회사에서 나온 시각. null이면 소속 중 */
	private Instant removedAt;

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

	public boolean isActive() {
		return removedAt == null;
	}

	/** 마지막 대표인지는 서비스가 먼저 확인한다 */
	public void changePosition(BrokerMemberPosition position) {
		requireActive();
		this.position = position;
	}

	/** 내보내기·스스로 나가기·탈퇴. 마지막 대표인지는 서비스가 먼저 확인한다 */
	public void remove(Instant now) {
		requireActive();
		this.removedAt = now;
	}

	private void requireActive() {
		if (!isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
	}
}
