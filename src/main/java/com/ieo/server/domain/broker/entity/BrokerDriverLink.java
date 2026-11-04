package com.ieo.server.domain.broker.entity;

import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.driver.entity.Driver;
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
 * 주선사와 단골 차주의 연결 (BRK-002). 주선사가 휴대폰 번호로 요청하고, 그 번호의 차주가 수락하면 연결된다.
 * 미가입 번호에도 요청을 저장해 두고, 그 번호로 가입한 차주에게 보인다 (가입 여부를 주선사에게 알려 주지 않는다).
 * 차주의 수락(linkedAt)이 곧 이 회사에 대한 위치·운행 정보 공유 동의다. 해제하면 공유도 끝난다.
 * 연결된 차주에게만 지정 배차할 수 있다. 해제해도 이미 확정된 배차와 운행은 그대로 둔다.
 */
@Getter
@Entity
@Table(name = "broker_driver_links", indexes = {
		@Index(name = "idx_broker_driver_links_phone_status", columnList = "phone, status"),
		@Index(name = "idx_broker_driver_links_broker_status", columnList = "broker_id, status"),
		@Index(name = "idx_broker_driver_links_driver_status", columnList = "driver_id, status")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerDriverLink extends BaseTimeEntity {

	public static final Duration TTL = Duration.ofDays(7);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	/** 요청받은 번호. 숫자만 (예: 01012345678) */
	@Column(nullable = false, length = 11)
	private String phone;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invited_by")
	private BrokerMember invitedBy;

	/** 수락한 차주. 수락 전에는 null */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private DriverLinkStatus status;

	/** 차주가 수락한 시각 = 이 회사에 위치·운행 정보 공유를 동의한 시각 */
	private Instant linkedAt;

	/** 거절·취소한 시각 */
	private Instant respondedAt;

	private Instant unlinkedAt;

	private BrokerDriverLink(BrokerMember invitedBy, String phone) {
		this.broker = invitedBy.getBroker();
		this.invitedBy = invitedBy;
		this.phone = phone;
		this.status = DriverLinkStatus.PENDING;
	}

	/** 같은 번호로 대기 중인 요청이나 연결이 없는지는 서비스가 먼저 확인한다 */
	public static BrokerDriverLink request(BrokerMember invitedBy, String phone) {
		return new BrokerDriverLink(invitedBy, phone);
	}

	/** 만료 시각. 저장 전에는 createdAt이 없어 null */
	public Instant expiresAt() {
		return getCreatedAt() == null ? null : getCreatedAt().plus(TTL);
	}

	public DriverLinkStatus statusAt(Instant now) {
		Instant expiresAt = expiresAt();
		if (status == DriverLinkStatus.PENDING && expiresAt != null && !now.isBefore(expiresAt)) {
			return DriverLinkStatus.EXPIRED;
		}
		return status;
	}

	public boolean isPendingAt(Instant now) {
		return statusAt(now) == DriverLinkStatus.PENDING;
	}

	public boolean isLinked() {
		return status == DriverLinkStatus.LINKED;
	}

	public boolean belongsTo(Long brokerId) {
		return broker.getId().equals(brokerId);
	}

	public boolean isLinkedTo(Long driverUserId) {
		return isLinked() && driver.getUserId().equals(driverUserId);
	}

	/** 번호가 맞는 차주인지는 서비스가 확인한다 */
	public void accept(Driver driver, Instant now) {
		requirePending(now);
		this.driver = driver;
		this.status = DriverLinkStatus.LINKED;
		this.linkedAt = now;
	}

	public void decline(Instant now) {
		requirePending(now);
		this.status = DriverLinkStatus.DECLINED;
		this.respondedAt = now;
	}

	public void cancel(Instant now) {
		requirePending(now);
		this.status = DriverLinkStatus.CANCELED;
		this.respondedAt = now;
	}

	/** 주선사나 차주 누구든 해제할 수 있다 */
	public void unlink(Instant now) {
		if (!isLinked()) {
			throw new BusinessException(ErrorCode.DRIVER_LINK_CLOSED);
		}
		this.status = DriverLinkStatus.UNLINKED;
		this.unlinkedAt = now;
	}

	private void requirePending(Instant now) {
		if (!isPendingAt(now)) {
			throw new BusinessException(ErrorCode.DRIVER_LINK_CLOSED);
		}
	}
}
