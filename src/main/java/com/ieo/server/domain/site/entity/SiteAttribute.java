package com.ieo.server.domain.site.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.site.enums.SiteAttributeKey;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주가 남긴 상하차지 정보 한 건 (SITE-004).
 * 같은 항목도 덮어쓰지 않고 쌓는다. 화면에는 항목별로 가장 최근 값을 보여 준다.
 */
@Getter
@Entity
@Table(name = "site_attributes",
		indexes = @Index(name = "idx_site_attributes_site_key", columnList = "site_id, attribute_key, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteAttribute extends BaseTimeEntity {

	private static final BigDecimal MIN_HEIGHT_LIMIT_M = new BigDecimal("1.50");
	private static final BigDecimal MAX_HEIGHT_LIMIT_M = new BigDecimal("6.00");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id")
	private Site site;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "attribute_key", nullable = false, length = 20)
	private SiteAttributeKey key;

	/** 높이 제한은 "3.50", 지게차·주차는 "true" / "false" */
	@Column(name = "attribute_value", nullable = false, length = 20)
	private String value;

	/** 남긴 차주. 탈퇴하면 비운다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reported_by")
	private Driver reportedBy;

	private SiteAttribute(Site site, SiteAttributeKey key, String value, Driver reportedBy) {
		this.site = site;
		this.key = key;
		this.value = value;
		this.reportedBy = reportedBy;
	}

	public static SiteAttribute create(Site site, SiteAttributeKey key, String value, Driver reportedBy) {
		if (key == null) {
			throw new BusinessException(ErrorCode.SITE_INVALID_ATTRIBUTE);
		}
		return new SiteAttribute(site, key, normalize(key, value), reportedBy);
	}

	/** 높이 제한 값(m). 높이 제한 항목이 아니면 null (VEH-002) */
	public BigDecimal heightLimitM() {
		return key == SiteAttributeKey.HEIGHT_LIMIT ? new BigDecimal(value) : null;
	}

	/** 지게차·주차 항목의 값. 그 외 항목이면 null */
	public Boolean flag() {
		return key == SiteAttributeKey.HEIGHT_LIMIT ? null : Boolean.valueOf(value);
	}

	public void detachReporter() {
		this.reportedBy = null;
	}

	private static String normalize(SiteAttributeKey key, String value) {
		String trimmed = value == null ? "" : value.strip();
		return switch (key) {
			case HEIGHT_LIMIT -> normalizeHeight(trimmed);
			case FORKLIFT, PARKING -> normalizeFlag(trimmed);
		};
	}

	private static String normalizeHeight(String value) {
		BigDecimal height;
		try {
			height = new BigDecimal(value);
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.SITE_INVALID_ATTRIBUTE);
		}
		if (height.compareTo(MIN_HEIGHT_LIMIT_M) < 0 || height.compareTo(MAX_HEIGHT_LIMIT_M) > 0) {
			throw new BusinessException(ErrorCode.SITE_INVALID_ATTRIBUTE, "높이 제한은 1.5m에서 6m 사이로 입력해 주세요.");
		}
		return height.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private static String normalizeFlag(String value) {
		if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
			throw new BusinessException(ErrorCode.SITE_INVALID_ATTRIBUTE);
		}
		return value.toLowerCase();
	}
}
