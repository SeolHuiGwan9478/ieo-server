package com.ieo.server.domain.delivery.entity;

import com.ieo.server.domain.delivery.enums.DeliveryProofStatus;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
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
 * 인수증 (04 인수증). 결제 방식이 인수증인 운행마다 하나.
 * 차주가 종이 인수증 사진을 휴대폰으로 올리고 [주선사에게 보내기]를 누르면 보낸 시각이 남는다 (POD-009, 011).
 * 주선사는 콘솔에서 로그인해 사진을 본다. 공개 링크는 없다
 */
@Getter
@Entity
@Table(name = "delivery_proofs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryProof extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", unique = true)
	private Trip trip;

	/** 주선사에게 보낸 시각. 보낸 뒤에는 사진을 바꿀 수 없다 */
	private Instant sentAt;

	private DeliveryProof(Trip trip) {
		this.trip = trip;
	}

	public static DeliveryProof create(Trip trip) {
		return new DeliveryProof(trip);
	}

	public void send(Instant now) {
		requireNotSent();
		this.sentAt = now;
	}

	public void requireNotSent() {
		if (isSent()) {
			throw new BusinessException(ErrorCode.DELIVERY_ALREADY_SENT);
		}
	}

	public boolean isSent() {
		return sentAt != null;
	}

	public DeliveryProofStatus status(int photoCount) {
		if (isSent()) {
			return DeliveryProofStatus.SENT;
		}
		return photoCount > 0 ? DeliveryProofStatus.PENDING : DeliveryProofStatus.NONE;
	}
}
