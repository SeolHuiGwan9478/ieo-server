package com.ieo.server.domain.site.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상하차지 평균 대기·작업 시간 (SITE-003, 010). 원본은 대기 기록(WaitRecord)이고 이 테이블은 캐시다.
 * 정보 카드·음성 안내·배차 추천이 자주 읽어서 미리 계산해 둔다.
 * 대기가 끝나거나 고쳐질 때 그 상하차지를 처음부터 다시 계산하고, 매일 새벽 전체를 다시 계산한다(최근 90일 기준).
 * TODO: 요일·시간대별 대기(byHourOfWeek)는 기록이 쌓이면 붙인다
 */
@Getter
@Entity
@Table(name = "site_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteStats {

	/** 이보다 기록이 적으면 평균을 믿기 어려워 비워 둔다 */
	public static final int MIN_SAMPLES = 3;

	@Id
	private Long siteId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id")
	private Site site;

	/** 평균 대기(분) = 진입 ~ 작업 시작. 기록이 3건 미만이면 null */
	private Integer avgWaitMinutes;

	/** 평균 작업(분) = 작업 시작 ~ 이탈. 기록이 3건 미만이면 null */
	private Integer avgWorkMinutes;

	/** 평균 대기 계산에 쓴 기록 수 */
	@Column(nullable = false)
	private int sampleCount;

	@Column(nullable = false)
	private Instant calculatedAt;

	private SiteStats(Site site) {
		this.site = site;
	}

	public static SiteStats of(Site site) {
		return new SiteStats(site);
	}

	/** 대기·작업 시간 목록으로 평균을 다시 낸다 */
	public void recalculate(long[] waitMinutes, long[] workMinutes, Instant now) {
		this.sampleCount = waitMinutes.length;
		this.avgWaitMinutes = average(waitMinutes);
		this.avgWorkMinutes = average(workMinutes);
		this.calculatedAt = now;
	}

	public boolean hasEnoughSamples() {
		return sampleCount >= MIN_SAMPLES;
	}

	private static Integer average(long[] minutes) {
		if (minutes.length < MIN_SAMPLES) {
			return null;
		}
		long sum = 0;
		for (long minute : minutes) {
			sum += minute;
		}
		return Math.round((float) sum / minutes.length);
	}
}
