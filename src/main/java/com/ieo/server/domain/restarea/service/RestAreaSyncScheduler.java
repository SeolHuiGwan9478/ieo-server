package com.ieo.server.domain.restarea.service;

import com.ieo.server.domain.restarea.repository.RestAreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 휴게소를 일주일에 한 번(월요일 새벽 4시) 갱신한다. 휴게소·편의시설은 실시간으로 바뀌지 않는다.
 * 표가 비어 있으면(처음 띄운 서버) 뜨자마자 한 번 받는다. 켜고 끄는 건 ieo.rest-area.auto-sync (테스트는 끔).
 * 서버 여러 대에서 같이 돌아도 휴게소 코드가 유일해서 한쪽만 넣는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ieo.rest-area.auto-sync", havingValue = "true", matchIfMissing = true)
public class RestAreaSyncScheduler {

	private final RestAreaSyncService syncService;
	private final RestAreaRepository repository;

	@Scheduled(cron = "${ieo.rest-area.sync-cron:0 0 4 * * MON}", zone = "Asia/Seoul")
	public void weekly() {
		run();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void fillIfEmpty() {
		if (repository.count() == 0) {
			// 서버가 뜨는 것을 막지 않게 따로 돈다
			Thread.ofVirtual().name("rest-area-first-sync").start(this::run);
		}
	}

	private void run() {
		try {
			syncService.sync();
		} catch (RuntimeException e) {
			log.warn("휴게소 갱신 실패: {}", e.getClass().getSimpleName());
		}
	}
}
