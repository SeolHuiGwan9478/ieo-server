package com.ieo.server.domain.notification.service;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.global.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 주선사에게 문자를 보낸다 (09 알림). 받는 사람은 그 회사에서 가장 먼저 가입한 담당자다 */
@Service
@RequiredArgsConstructor
public class BrokerNotifier {

	private final BrokerMemberRepository memberRepository;
	private final SmsSender smsSender;

	public void send(Broker broker, String message) {
		memberRepository.findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(broker.getId()).stream()
				.findFirst()
				.ifPresent(member -> smsSender.send(member.getUser().getPhone(), message));
	}
}
