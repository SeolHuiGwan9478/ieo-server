package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.CreateBrokerRequest;
import com.ieo.server.domain.broker.dto.MyBrokerResponse;
import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
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
 * 주선사 회사 등록과 내 회사 조회 (BRK-001).
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

	public MyBrokerResponse register(Long userId, CreateBrokerRequest request) {
		User user = requireBrokerUser(userId);
		if (memberRepository.existsById(userId)) {
			throw new BusinessException(ErrorCode.BROKER_ALREADY_MEMBER);
		}
		Broker broker = Broker.create(request.name(), request.businessNumber(), request.licenseNumber(),
				request.representativeName());
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
		return MyBrokerResponse.from(owner);
	}

	/** 소속이 없으면 404. 콘솔은 이때 회사 등록 화면으로 보낸다 */
	@Transactional(readOnly = true)
	public MyBrokerResponse getMine(Long userId) {
		requireBrokerUser(userId);
		return memberRepository.findById(userId)
				.map(MyBrokerResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NO_MEMBERSHIP));
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
