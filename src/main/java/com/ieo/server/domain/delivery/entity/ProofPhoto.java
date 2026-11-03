package com.ieo.server.domain.delivery.entity;

import com.ieo.server.domain.delivery.enums.ProofPhotoType;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 인수증에 붙은 사진: 수하인의 이상 사항 사진 또는 차주의 종이 인수증 사진. 파일은 저장소에, 여기는 key만 */
@Getter
@Entity
@Table(name = "proof_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProofPhoto extends BaseTimeEntity {

	/** 인수증 하나에 사진은 이 수까지 */
	public static final int MAX_PER_PROOF = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "delivery_proof_id")
	private DeliveryProof deliveryProof;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ProofPhotoType type;

	@Column(nullable = false, length = 200)
	private String fileKey;

	private ProofPhoto(DeliveryProof deliveryProof, ProofPhotoType type, String fileKey) {
		this.deliveryProof = deliveryProof;
		this.type = type;
		this.fileKey = fileKey;
	}

	public static ProofPhoto of(DeliveryProof deliveryProof, ProofPhotoType type, String fileKey) {
		return new ProofPhoto(deliveryProof, type, fileKey);
	}
}
