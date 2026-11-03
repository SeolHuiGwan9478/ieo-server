package com.ieo.server.domain.wait.service;

import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.wait.dto.WaitPolicyRequest;
import com.ieo.server.domain.wait.dto.WaitPolicyResponse;
import com.ieo.server.domain.wait.entity.WaitPolicy;
import com.ieo.server.domain.wait.repository.WaitPolicyRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사 대기비 정책 (WAIT-010). 바꿀 때는 이전 정책을 끄고 새로 만든다.
 * 돈이 걸린 설정이라 대표(OWNER)만 바꿀 수 있고, 담당자는 볼 수만 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WaitPolicyService {

	private final WaitPolicyRepository policyRepository;
	private final BrokerMemberRepository memberRepository;

	@Transactional(readOnly = true)
	public Optional<WaitPolicyResponse> getCurrent(Long userId) {
		BrokerMember member = requireMember(userId);
		return policyRepository.findFirstByBrokerIdAndActiveTrue(member.getBroker().getId())
				.map(WaitPolicyResponse::from);
	}

	public WaitPolicyResponse replace(Long userId, WaitPolicyRequest request) {
		BrokerMember member = requireMember(userId);
		if (!member.isOwner()) {
			throw new BusinessException(ErrorCode.WAIT_POLICY_FORBIDDEN);
		}
		WaitPolicy policy = WaitPolicy.create(member.getBroker(), request.freeMinutes(), request.unitMinutes(),
				request.unitAmount(), request.maxAmount(), request.rounding());
		policyRepository.findFirstByBrokerIdAndActiveTrue(member.getBroker().getId())
				.ifPresent(WaitPolicy::deactivate);
		return WaitPolicyResponse.from(policyRepository.save(policy));
	}

	private BrokerMember requireMember(Long userId) {
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.WAIT_POLICY_FORBIDDEN));
		if (member.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		return member;
	}
}
