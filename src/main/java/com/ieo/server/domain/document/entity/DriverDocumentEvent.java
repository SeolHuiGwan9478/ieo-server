package com.ieo.server.domain.document.entity;

import com.ieo.server.domain.document.enums.DocumentEventType;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.global.entity.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 서류 처리 이력: 제출, 열람, 승인, 반려 (07 인계서 6.5, 8장). 수정·삭제 기능은 두지 않는다.
 * 열람은 운영자가 서류 사진을 열 때마다 남긴다. 누가 언제 무엇을 봤는지 남기기 위해서다.
 */
@Getter
@Entity
@Table(name = "driver_document_events", indexes = @Index(name = "idx_driver_document_events_driver", columnList = "driver_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverDocumentEvent extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id")
	private DriverDocument document;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private DocumentEventType type;

	/** 한 사람 (users.id). 제출은 차주, 열람·승인·반려는 운영자 */
	@Column(nullable = false)
	private Long actorUserId;

	/** 반려 문구 같은 보충 설명 */
	@Column(length = 250)
	private String detail;

	private DriverDocumentEvent(DriverDocument document, DocumentEventType type, Long actorUserId, String detail) {
		this.driver = document.getDriver();
		this.document = document;
		this.type = type;
		this.actorUserId = actorUserId;
		this.detail = detail;
	}

	public static DriverDocumentEvent of(DriverDocument document, DocumentEventType type, Long actorUserId, String detail) {
		return new DriverDocumentEvent(document, type, actorUserId, detail);
	}
}
