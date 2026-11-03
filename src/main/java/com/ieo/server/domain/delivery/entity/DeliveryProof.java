package com.ieo.server.domain.delivery.entity;

import com.ieo.server.domain.delivery.enums.DeliveryProofStatus;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
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
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전자인수증 (04 인수증). 운행마다 하나. 수하인은 로그인 없이 링크(/delivery-proofs/{token})로 열어 서명한다.
 * 상태는 저장하지 않는다: 서명 시각, 종이 인수증 사진, 주선사 전달 시각으로 계산한다.
 * 링크는 하차 완료(운행 완료) 7일 뒤 만료된다 (POD-001). 완료 전에는 열려 있다.
 * TODO: PDF(POD-007)와 문서 해시(POD-010, P3)는 나중에 붙인다
 */
@Getter
@Entity
@Table(name = "delivery_proofs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryProof extends BaseTimeEntity {

	public static final Duration LINK_TTL_AFTER_COMPLETION = Duration.ofDays(7);
	private static final int MAX_NAME = 30;
	private static final int MAX_NOTE = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", unique = true)
	private Trip trip;

	/** 링크용 무작위 토큰. 추측할 수 없게 32바이트 */
	@Column(nullable = false, unique = true, length = 43)
	private String token;

	/** 링크를 발급할 때 차량 위치. 차량 앱이 보낸다 (POD-006) */
	private Double vehicleLat;

	private Double vehicleLng;

	/** 서명한 사람 이름 (선택) */
	@Column(length = MAX_NAME)
	private String signerName;

	/** 서명 이미지의 저장소 key */
	@Column(length = 200)
	private String signatureKey;

	private Instant signedAt;

	/** 서명 위치. 수하인 브라우저가 위치 동의를 했을 때만 */
	private Double signerLat;

	private Double signerLng;

	/** 이상 사항 메모 (파손, 수량 부족, POD-005) */
	@Column(length = MAX_NOTE)
	private String issueNote;

	/** 주선사에게 전달한 시각. TODO: 09 알림을 만든 뒤 채운다 */
	private Instant sentAt;

	private DeliveryProof(Trip trip, String token, Double vehicleLat, Double vehicleLng) {
		this.trip = trip;
		this.token = token;
		this.vehicleLat = vehicleLat;
		this.vehicleLng = vehicleLng;
	}

	public static DeliveryProof issue(Trip trip, Double vehicleLat, Double vehicleLng) {
		return new DeliveryProof(trip, RandomTokens.urlSafe(), vehicleLat, vehicleLng);
	}

	/** 수하인 서명. 한 번만 */
	public void sign(String signerName, String signatureKey, String issueNote, Double lat, Double lng, Instant now) {
		if (isSigned()) {
			throw new BusinessException(ErrorCode.DELIVERY_ALREADY_SIGNED);
		}
		this.signerName = trim(signerName, MAX_NAME);
		this.signatureKey = signatureKey;
		this.issueNote = trim(issueNote, MAX_NOTE);
		this.signerLat = lat;
		this.signerLng = lng;
		this.signedAt = now;
	}

	public void markSent(Instant now) {
		this.sentAt = now;
	}

	public boolean isSigned() {
		return signedAt != null;
	}

	/** hasPaperPhoto: 종이 인수증 사진이 있는지 (사진 테이블에서 서비스가 확인) */
	public DeliveryProofStatus status(boolean hasPaperPhoto) {
		if (sentAt != null) {
			return DeliveryProofStatus.DELIVERED;
		}
		if (isSigned()) {
			return DeliveryProofStatus.SIGNED;
		}
		return hasPaperPhoto ? DeliveryProofStatus.PHOTO : DeliveryProofStatus.PENDING;
	}

	/** 링크 만료 시각. 운행이 끝나기 전이면 null(열려 있음) */
	public Instant expiresAt() {
		Instant completedAt = trip.getCompletedAt();
		return completedAt == null ? null : completedAt.plus(LINK_TTL_AFTER_COMPLETION);
	}

	public boolean isExpired(Instant now) {
		Instant expiresAt = expiresAt();
		return expiresAt != null && !now.isBefore(expiresAt);
	}

	private static String trim(String text, int max) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String stripped = text.strip();
		return stripped.length() > max ? stripped.substring(0, max) : stripped;
	}
}
