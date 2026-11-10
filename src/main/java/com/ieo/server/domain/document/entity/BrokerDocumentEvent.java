package com.ieo.server.domain.document.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.document.enums.DocumentEventType;
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

/** 주선사 서류 처리 이력: 제출, 국세청 조회, 열람, 승인, 반려. DriverDocumentEvent와 같은 규칙이다 (수정·삭제 없음, 열람도 남긴다) */
@Getter
@Entity
@Table(name = "broker_document_events", indexes = @Index(name = "idx_broker_document_events_broker", columnList = "broker_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerDocumentEvent extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id")
	private BrokerDocument document;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private DocumentEventType type;

	/** 한 사람 (users.id). 제출은 주선사 담당자, 열람·승인·반려는 운영자 */
	@Column(nullable = false)
	private Long actorUserId;

	/** 반려 문구, 국세청 조회 요약 같은 보충 설명 */
	@Column(length = 250)
	private String detail;

	private BrokerDocumentEvent(BrokerDocument document, DocumentEventType type, Long actorUserId, String detail) {
		this.broker = document.getBroker();
		this.document = document;
		this.type = type;
		this.actorUserId = actorUserId;
		this.detail = detail;
	}

	public static BrokerDocumentEvent of(BrokerDocument document, DocumentEventType type, Long actorUserId, String detail) {
		return new BrokerDocumentEvent(document, type, actorUserId, detail);
	}
}
