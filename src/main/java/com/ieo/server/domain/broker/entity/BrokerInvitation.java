package com.ieo.server.domain.broker.entity;

import com.ieo.server.domain.broker.enums.BrokerInvitationStatus;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대표가 휴대폰 번호로 보낸 담당자 초대 (BRK-001 담당자 여러 명).
 * 그 번호로 로그인한 주선사 계정만 수락할 수 있고, 수락하면 담당자(STAFF)가 된다.
 * 7일 안에 수락하지 않으면 만료로 본다(저장하지 않고 createdAt으로 판단).
 */
@Getter
@Entity
@Table(name = "broker_invitations", indexes = {
		@Index(name = "idx_broker_invitations_phone_status", columnList = "phone, status"),
		@Index(name = "idx_broker_invitations_broker_status", columnList = "broker_id, status")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerInvitation extends BaseTimeEntity {

	public static final Duration TTL = Duration.ofDays(7);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	/** 초대받은 번호. 숫자만 (예: 01012345678) */
	@Column(nullable = false, length = 11)
	private String phone;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invited_by")
	private BrokerMember invitedBy;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private BrokerInvitationStatus status;

	/** 수락·거절·취소한 시각 */
	private Instant respondedAt;

	private BrokerInvitation(BrokerMember invitedBy, String phone) {
		this.broker = invitedBy.getBroker();
		this.invitedBy = invitedBy;
		this.phone = phone;
		this.status = BrokerInvitationStatus.PENDING;
	}

	/** 대표인지, 같은 번호로 대기 중인 초대가 없는지는 서비스가 먼저 확인한다 */
	public static BrokerInvitation create(BrokerMember invitedBy, String phone) {
		return new BrokerInvitation(invitedBy, phone);
	}

	/** 만료 시각. 저장 전에는 createdAt이 없어 null */
	public Instant expiresAt() {
		return getCreatedAt() == null ? null : getCreatedAt().plus(TTL);
	}

	public BrokerInvitationStatus statusAt(Instant now) {
		Instant expiresAt = expiresAt();
		if (status == BrokerInvitationStatus.PENDING && expiresAt != null && !now.isBefore(expiresAt)) {
			return BrokerInvitationStatus.EXPIRED;
		}
		return status;
	}

	public boolean isPendingAt(Instant now) {
		return statusAt(now) == BrokerInvitationStatus.PENDING;
	}

	public boolean belongsTo(Long brokerId) {
		return broker.getId().equals(brokerId);
	}

	public void accept(Instant now) {
		respond(BrokerInvitationStatus.ACCEPTED, now);
	}

	public void decline(Instant now) {
		respond(BrokerInvitationStatus.DECLINED, now);
	}

	public void cancel(Instant now) {
		respond(BrokerInvitationStatus.CANCELED, now);
	}

	private void respond(BrokerInvitationStatus status, Instant now) {
		if (!isPendingAt(now)) {
			throw new BusinessException(ErrorCode.BROKER_INVITATION_CLOSED);
		}
		this.status = status;
		this.respondedAt = now;
	}
}
