package com.ieo.server.domain.document.entity;

import com.ieo.server.domain.document.enums.DocumentKind;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.enums.ReviewSubject;
import com.ieo.server.domain.driver.entity.Driver;
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

/** 차주가 낸 서류 한 건 (07 인계서 5장). 공통 동작은 ReviewDocument에 있다 */
@Getter
@Entity
@Table(name = "driver_documents", indexes = @Index(name = "idx_driver_documents_driver", columnList = "driver_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverDocument extends ReviewDocument {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private DocumentType type;

	private DriverDocument(Driver driver, DocumentType type, String fileKey, String contentType, BusinessCheck businessCheck) {
		super(fileKey, contentType, businessCheck);
		this.driver = driver;
		this.type = type;
	}

	/** businessCheck는 사업자등록증에만, 그리고 꼭 있어야 한다 */
	public static DriverDocument submit(Driver driver, DocumentType type, String fileKey, String contentType, BusinessCheck businessCheck) {
		if ((type == DocumentType.BUSINESS_REGISTRATION) != (businessCheck != null)) {
			throw new IllegalArgumentException("사업자 정보는 사업자등록증에만, 그리고 꼭 있어야 합니다: " + type);
		}
		return new DriverDocument(driver, type, fileKey, contentType, businessCheck);
	}

	@Override
	public DocumentKind kind() {
		return type;
	}

	@Override
	public ReviewSubject subject() {
		return ReviewSubject.DRIVER;
	}
}
