package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.BrokerMetricsResponse;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.entity.BrokerMetrics;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerMetricsRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주선사 신뢰 지표 읽기 (BRK-030~032). 계산은 BrokerMetricsCalculator */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerMetricsService {

	private final BrokerMetricsRepository metricsRepository;
	private final BrokerMemberRepository memberRepository;

	/** 오더 카드용: 여러 회사를 한 번에. 계산한 적 없는 회사도 빈 지표로 채운다 */
	public Map<Long, BrokerMetricsResponse> getAll(Collection<Long> brokerIds) {
		Map<Long, BrokerMetrics> found = metricsRepository.findAllById(brokerIds).stream()
				.collect(Collectors.toMap(BrokerMetrics::getBrokerId, Function.identity()));
		return brokerIds.stream().distinct()
				.collect(Collectors.toMap(Function.identity(), id -> BrokerMetricsResponse.from(found.get(id))));
	}

	/** 주선사 콘솔: 우리 회사 지표. 차주에게 보이는 것과 같다. 담당자 누구나 */
	public BrokerMetricsResponse getMine(Long userId) {
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NO_MEMBERSHIP));
		if (member.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		return BrokerMetricsResponse.from(metricsRepository.findById(member.getBroker().getId()).orElse(null));
	}
}
