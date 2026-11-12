package com.ieo.server.domain.delivery.entity;

import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 종이 인수증 사진 한 장. 파일은 저장소에, 여기는 key만 */
@Getter
@Entity
@Table(name = "proof_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProofPhoto extends BaseTimeEntity {

	/** 인수증 하나에 사진은 이 수까지 (여러 장짜리 인수증) */
	public static final int MAX_PER_PROOF = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "delivery_proof_id")
	private DeliveryProof deliveryProof;

	@Column(nullable = false, length = 200)
	private String fileKey;

	private ProofPhoto(DeliveryProof deliveryProof, String fileKey) {
		this.deliveryProof = deliveryProof;
		this.fileKey = fileKey;
	}

	public static ProofPhoto of(DeliveryProof deliveryProof, String fileKey) {
		return new ProofPhoto(deliveryProof, fileKey);
	}
}
