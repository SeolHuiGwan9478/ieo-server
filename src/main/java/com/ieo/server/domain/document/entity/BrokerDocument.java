package com.ieo.server.domain.document.entity;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.document.enums.BrokerDocumentType;
import com.ieo.server.domain.document.enums.DocumentKind;
import com.ieo.server.domain.document.enums.ReviewSubject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 주선사 회사가 낸 서류 한 건 (07 인계서 13장). 담당자 누구나 낼 수 있고 회사 단위로 심사한다. 공통 동작은 ReviewDocument에 있다 */
@Getter
@Entity
@Table(name = "broker_documents", indexes = @Index(name = "idx_broker_documents_broker", columnList = "broker_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerDocument extends ReviewDocument {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broker_id")
	private Broker broker;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private BrokerDocumentType type;

	private BrokerDocument(Broker broker, BrokerDocumentType type, String fileKey, String contentType, BusinessCheck businessCheck) {
		super(fileKey, contentType, businessCheck);
		this.broker = broker;
		this.type = type;
	}

	/** businessCheck는 사업자등록증에만, 그리고 꼭 있어야 한다 */
	public static BrokerDocument submit(Broker broker, BrokerDocumentType type, String fileKey, String contentType, BusinessCheck businessCheck) {
		if ((type == BrokerDocumentType.BUSINESS_REGISTRATION) != (businessCheck != null)) {
			throw new IllegalArgumentException("사업자 정보는 사업자등록증에만, 그리고 꼭 있어야 합니다: " + type);
		}
		return new BrokerDocument(broker, type, fileKey, contentType, businessCheck);
	}

	@Override
	public DocumentKind kind() {
		return type;
	}

	@Override
	public ReviewSubject subject() {
		return ReviewSubject.BROKER;
	}
}
