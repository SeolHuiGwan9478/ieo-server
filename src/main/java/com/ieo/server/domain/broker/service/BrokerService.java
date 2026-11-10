package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.dto.MyBrokerResponse;
import com.ieo.server.domain.broker.dto.UpdateBrokerRequest;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.enums.BrokerPlan;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.document.service.BrokerDocumentService;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사 회사 등록·조회·수정 (BRK-001, BRK-003).
 * 회사를 등록한 계정이 대표(OWNER)가 된다. 이미 등록된 사업자등록번호면 새 회사를 만들지 않고,
 * 그 회사의 대표에게 초대를 받아 합류한다 (초대는 담당자 관리에서 만든다).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BrokerService {

	private final BrokerRepository brokerRepository;
	private final BrokerMemberRepository memberRepository;
	private final UserRepository userRepository;
	private final BrokerPlanService planService;
	private final BrokerDocumentService brokerDocumentService;

	public MyBrokerResponse register(Long userId, CreateBrokerRequest request) {
		User user = requireBrokerUser(userId);
		memberRepository.findById(userId).ifPresent(member -> {
			throw new BusinessException(
					member.isActive() ? ErrorCode.BROKER_ALREADY_MEMBER : ErrorCode.BROKER_MEMBER_REMOVED);
		});
		Broker broker = Broker.create(request.name(), request.businessNumber(), request.licenseNumber(),
				request.representativeName(), request.settlementDays(), request.defaultFeeRate());
		if (brokerRepository.existsByBusinessNumber(broker.getBusinessNumber())) {
			throw new BusinessException(ErrorCode.BROKER_DUPLICATE_BUSINESS_NUMBER);
		}
		try {
			brokerRepository.saveAndFlush(broker);
		} catch (DataIntegrityViolationException e) {
			// 같은 사업자등록번호로 동시에 등록한 경우. brokers.business_number 유니크 제약이 하나만 남긴다
			throw new BusinessException(ErrorCode.BROKER_DUPLICATE_BUSINESS_NUMBER);
		}
		BrokerMember owner = memberRepository.save(BrokerMember.of(user, broker, BrokerMemberPosition.OWNER));
		return toResponse(owner);
	}

	/**
	 * 소속이 없으면 404. 콘솔은 이때 받은 초대 목록과 회사 등록 화면을 보여 준다.
	 * 회사에서 나온 계정은 403 BROKER_403_REMOVED
	 */
	@Transactional(readOnly = true)
	public MyBrokerResponse getMine(Long userId) {
		return toResponse(requireActiveMember(userId));
	}

	/**
	 * 회사 정보·정산 주기 수정 (BRK-001, BRK-003). 대표만. 보낸 항목만 바꾼다.
	 * 정산 주기를 바꿔도 이미 지급 예정일이 계산된 장부는 그대로 둔다.
	 */
	public MyBrokerResponse update(Long userId, UpdateBrokerRequest request) {
		BrokerMember member = requireActiveMember(userId);
		if (!member.isOwner()) {
			throw new BusinessException(ErrorCode.BROKER_OWNER_ONLY);
		}
		Broker broker = member.getBroker();
		if (request.name() != null) {
			broker.rename(request.name());
		}
		if (request.licenseNumber() != null) {
			broker.changeLicenseNumber(request.licenseNumber());
		}
		if (request.representativeName() != null) {
			broker.changeRepresentativeName(request.representativeName());
		}
		if (request.settlementDays() != null) {
			broker.changeSettlementDays(request.settlementDays());
		}
		if (request.defaultFeeRate() != null) {
			broker.changeDefaultFeeRate(request.defaultFeeRate());
		}
		return toResponse(member);
	}

	/** 플랜 바꾸기 (대표만). 결제가 없어 바로 적용된다 (BILL-007) */
	public MyBrokerResponse changePlan(Long userId, BrokerPlan plan) {
		BrokerMember member = requireActiveMember(userId);
		if (!member.isOwner()) {
			throw new BusinessException(ErrorCode.BROKER_OWNER_ONLY);
		}
		planService.change(member.getBroker(), plan);
		return toResponse(member);
	}

	private MyBrokerResponse toResponse(BrokerMember member) {
		Long brokerId = member.getBroker().getId();
		return MyBrokerResponse.from(member, planService.getPlan(brokerId), brokerDocumentService.isVerified(brokerId));
	}

	private BrokerMember requireActiveMember(Long userId) {
		requireBrokerUser(userId);
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NO_MEMBERSHIP));
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		return member;
	}

	private User requireBrokerUser(Long userId) {
		User user = userRepository.findById(userId)
				.filter(found -> found.getStatus() != UserStatus.WITHDRAWN)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (user.getRole() != UserRole.BROKER) {
			throw new BusinessException(ErrorCode.BROKER_NOT_BROKER);
		}
		return user;
	}
}
