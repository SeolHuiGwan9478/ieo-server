package com.ieo.server.domain.site.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 차주가 상하차지에 남긴 자유 메모 (SITE-005, 008, 009).
 * 다른 차주에게도 보이지만 작성자는 익명으로 표시한다. 음성 파일(audioUrl)은 저장소를 정한 뒤에 넣는다.
 */
@Getter
@Entity
@Table(name = "site_notes", indexes = @Index(name = "idx_site_notes_site_created", columnList = "site_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteNote extends BaseTimeEntity {

	public static final int MAX_TEXT_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id")
	private Site site;

	/** 작성자. 화면에는 보여 주지 않는다. 탈퇴하면 비운다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	@Column(nullable = false, length = MAX_TEXT_LENGTH)
	private String text;

	@Column(nullable = false)
	private int helpfulCount;

	/** 신고가 쌓이면 목록에서 뒤로 미룬다 */
	@Column(nullable = false)
	private int reportedCount;

	private SiteNote(Site site, Driver driver, String text) {
		this.site = site;
		this.driver = driver;
		this.text = text;
	}

	public static SiteNote create(Site site, Driver driver, String text) {
		String trimmed = text == null ? "" : text.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT_LENGTH) {
			throw new BusinessException(ErrorCode.SITE_INVALID_NOTE);
		}
		return new SiteNote(site, driver, trimmed);
	}

	public boolean isWrittenBy(Long userId) {
		return driver != null && driver.getUserId().equals(userId);
	}

	public void markHelpful() {
		this.helpfulCount++;
	}

	public void report() {
		this.reportedCount++;
	}

	public void detachAuthor() {
		this.driver = null;
	}
}
