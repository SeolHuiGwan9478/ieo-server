package com.ieo.server.domain.wait.service;

import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.entity.SiteStats;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.site.repository.SiteStatsRepository;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상하차지 통계(SiteStats)를 대기 기록으로 다시 계산한다 (WAIT-023 → SITE-003).
 * 더해 가는 방식은 수정 반영이 어렵고 오차가 쌓이므로, 늘 처음부터 다시 계산한다.
 * 기록이 90일 기간에서 빠지는 것을 반영하려고 매일 새벽 전체를 다시 계산한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteStatsCalculator {

	/** 현장 상황이 바뀌었을 수 있어 최근 90일만 쓴다 */
	static final Duration WINDOW = Duration.ofDays(90);

	private final WaitRecordRepository recordRepository;
	private final SiteStatsRepository statsRepository;
	private final SiteRepository siteRepository;
	private final Clock clock;

	/** 대기가 끝나거나(작업 시작·이탈) 고쳐질 때 그 상하차지만 다시 계산한다 */
	@Transactional
	public void recalculate(Site site) {
		Instant now = clock.instant();
		List<WaitRecord> records = recordRepository.findAllForSiteStats(site.getId(), now.minus(WINDOW));
		long[] waits = records.stream().map(WaitRecord::waitMinutes).filter(Objects::nonNull)
				.mapToLong(Long::longValue).toArray();
		long[] works = records.stream().map(WaitRecord::workMinutes).filter(Objects::nonNull)
				.mapToLong(Long::longValue).toArray();

		SiteStats stats = statsRepository.findById(site.getId()).orElseGet(() -> SiteStats.of(site));
		stats.recalculate(waits, works, now);
		statsRepository.save(stats);
	}

	/**
	 * 매일 새벽: 통계가 있거나 최근 기록이 있는 상하차지를 모두 다시 계산한다.
	 * TODO: 지금은 자동 실행을 꺼 두었다. 켤 때 아래 주석을 풀고, 서버를 여러 대 띄우면 ShedLock 같은 잠금을 붙인다
	 */
	// @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
	@Transactional
	public void recalculateAll() {
		Set<Long> siteIds = new HashSet<>(statsRepository.findAllSiteIds());
		siteIds.addAll(recordRepository.findSiteIdsArrivedSince(clock.instant().minus(WINDOW)));
		siteRepository.findAllById(siteIds).forEach(this::recalculate);
		log.info("상하차지 통계 다시 계산: {}곳", siteIds.size());
	}
}
