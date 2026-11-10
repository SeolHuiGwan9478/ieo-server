package com.ieo.server.domain.document.entity;

import com.ieo.server.domain.document.business.BusinessLookup;
import com.ieo.server.domain.document.enums.DocumentStatus;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.enums.RejectReason;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주가 낸 서류 한 건 (07 인계서 5장). 파일은 저장소에 두고 여기는 key만 둔다.
 * 다시 내면 덮어쓰지 않고 새 건을 만든다. 누가 무엇을 왜 반려했는지 남기기 위해서다.
 * 서류 파일은 /api/public 아래로 내보내지 않는다. 운영자와 본인만 로그인한 API로 본다.
 * 사업자등록증에는 입력값과 국세청 진위확인 결과(businessCheck)가 붙는다. 결과는 참고용이고 승인·반려는 운영자가 정한다.
 */
@Getter
@Entity
@Table(name = "driver_documents", indexes = @Index(name = "idx_driver_documents_driver", columnList = "driver_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverDocument extends BaseTimeEntity {

	public static final int MAX_NOTE_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private DocumentType type;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private DocumentStatus status;

	@Column(nullable = false, length = 200)
	private String fileKey;

	@Column(nullable = false, length = 50)
	private String contentType;

	private Instant reviewedAt;

	/** 승인·반려한 운영자 (users.id) */
	private Long reviewerId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 30)
	private RejectReason rejectReason;

	/** 반려 사유 보충. 기타(OTHER)면 필수이고 이 글이 차주에게 보인다 */
	@Column(length = MAX_NOTE_LENGTH)
	private String rejectNote;

	/** 사업자등록증일 때만: 입력한 사업자번호·개업일·대표자와 국세청 진위확인 결과. 다른 서류는 null */
	@Embedded
	private BusinessCheck businessCheck;

	/** 두 운영자가 같은 건을 동시에 처리하면 늦은 쪽이 409를 받는다 */
	@Version
	private Long version;

	private DriverDocument(Driver driver, DocumentType type, String fileKey, String contentType, BusinessCheck businessCheck) {
		this.driver = driver;
		this.type = type;
		this.status = DocumentStatus.PENDING;
		this.fileKey = fileKey;
		this.contentType = contentType;
		this.businessCheck = businessCheck;
	}

	/** businessCheck는 사업자등록증에만, 그리고 꼭 있어야 한다 */
	public static DriverDocument submit(Driver driver, DocumentType type, String fileKey, String contentType, BusinessCheck businessCheck) {
		if ((type == DocumentType.BUSINESS_REGISTRATION) != (businessCheck != null)) {
			throw new IllegalArgumentException("사업자 정보는 사업자등록증에만, 그리고 꼭 있어야 합니다: " + type);
		}
		return new DriverDocument(driver, type, fileKey, contentType, businessCheck);
	}

	/** 국세청 진위확인 결과를 기록한다. 심사 대기인 사업자등록증만 */
	public void recordBusinessCheck(BusinessLookup lookup, Instant now) {
		if (businessCheck == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "사업자등록증만 국세청 조회를 할 수 있어요.");
		}
		requirePending();
		businessCheck.record(lookup, now);
	}

	public void approve(Long reviewerId, Instant now) {
		requirePending();
		this.status = DocumentStatus.APPROVED;
		this.reviewedAt = now;
		this.reviewerId = reviewerId;
	}

	public void reject(Long reviewerId, RejectReason reason, String note, Instant now) {
		requirePending();
		String trimmed = note == null || note.isBlank() ? null : note.strip();
		if (reason == null || (reason.requiresNote() && trimmed == null)) {
			throw new BusinessException(ErrorCode.DOCUMENT_REASON_REQUIRED);
		}
		if (trimmed != null && trimmed.length() > MAX_NOTE_LENGTH) {
			throw new BusinessException(ErrorCode.DOCUMENT_REASON_REQUIRED, "반려 사유는 " + MAX_NOTE_LENGTH + "자까지 적을 수 있습니다.");
		}
		this.status = DocumentStatus.REJECTED;
		this.reviewedAt = now;
		this.reviewerId = reviewerId;
		this.rejectReason = reason;
		this.rejectNote = trimmed;
	}

	/** 차주에게 보이는 반려 문구. 기타는 운영자가 적은 글이 그대로 보인다 */
	public String rejectMessage() {
		if (rejectReason == null) {
			return null;
		}
		if (rejectReason.requiresNote()) {
			return rejectNote;
		}
		return rejectNote == null ? rejectReason.getMessage() : rejectReason.getMessage() + " (" + rejectNote + ")";
	}

	public boolean isPending() {
		return status == DocumentStatus.PENDING;
	}

	public boolean isApproved() {
		return status == DocumentStatus.APPROVED;
	}

	private void requirePending() {
		if (status != DocumentStatus.PENDING) {
			throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_REVIEWED);
		}
	}
}
