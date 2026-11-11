package com.ieo.server.domain.dispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 몇 분마다 OrderExpirer를 돌린다. 켜고 끄는 건 ieo.dispatch.auto-expire (기본 켜짐), 주기는 ieo.dispatch.expire-interval.
 * 테스트(application-test.yml)는 꺼 둔다: 테스트 DB의 과거 날짜 오더를 중간에 마감해 버리면 다른 테스트가 흔들린다.
 * 서버 여러 대에서 같이 돌아도 Order.version으로 한쪽만 저장되고, 다음 주기에 다시 보므로 결과는 같다
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ieo.dispatch.auto-expire", havingValue = "true", matchIfMissing = true)
public class OrderExpireScheduler {

	private final OrderExpirer expirer;

	@Scheduled(initialDelayString = "${ieo.dispatch.expire-initial-delay:PT30S}",
			fixedDelayString = "${ieo.dispatch.expire-interval:PT5M}")
	public void run() {
		expirer.expireOverdue();
	}
}
