package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.MyBrokerResponse;
import com.ieo.server.domain.broker.dto.ReceivedInvitationResponse;
import com.ieo.server.domain.broker.entity.BrokerInvitation;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerInvitationStatus;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerInvitationRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 번호로 온 담당자 초대를 보고 수락·거절한다.
 * 번호 하나가 계정 하나이고 역할도 하나라서, 차주로 가입된 번호는 담당자가 될 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BrokerInvitationService {

	private final BrokerInvitationRepository invitationRepository;
	private final BrokerMemberRepository memberRepository;
	private final UserRepository userRepository;
	private final BrokerPlanService planService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<ReceivedInvitationResponse> getMine(Long userId) {
		User user = requireBrokerUser(userId);
		Instant now = clock.instant();
		return invitationRepository.findAllByPhoneAndStatusOrderByCreatedAtDesc(
						user.getPhone(), BrokerInvitationStatus.PENDING).stream()
				.filter(invitation -> invitation.isPendingAt(now))
				.map(ReceivedInvitationResponse::from)
				.toList();
	}

	/** 수락하면 그 회사의 담당자(STAFF)가 된다 */
	public MyBrokerResponse accept(Long userId, Long invitationId) {
		User user = requireBrokerUser(userId);
		BrokerInvitation invitation = findMine(user, invitationId);
		memberRepository.findById(userId).ifPresent(member -> {
			throw new BusinessException(
					member.isActive() ? ErrorCode.BROKER_ALREADY_MEMBER : ErrorCode.BROKER_MEMBER_REMOVED);
		});
		planService.requireMemberRoomForAccept(invitation.getBroker().getId());
		invitation.accept(clock.instant());
		BrokerMember member = memberRepository.save(
				BrokerMember.of(user, invitation.getBroker(), BrokerMemberPosition.STAFF));
		return MyBrokerResponse.from(member, planService.getPlan(member.getBroker().getId()));
	}

	public void decline(Long userId, Long invitationId) {
		User user = requireBrokerUser(userId);
		findMine(user, invitationId).decline(clock.instant());
	}

	/** 내 번호로 온 초대가 아니면 있는지도 알려 주지 않는다 */
	private BrokerInvitation findMine(User user, Long invitationId) {
		return invitationRepository.findByIdForUpdate(invitationId)
				.filter(invitation -> invitation.getPhone().equals(user.getPhone()))
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_INVITATION_NOT_FOUND));
	}

	private User requireBrokerUser(Long userId) {
		User user = userRepository.findById(userId)
				.filter(found -> found.getStatus() != UserStatus.WITHDRAWN)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (user.getRole() != UserRole.BROKER) {
			throw new BusinessException(ErrorCode.BROKER_NOT_BROKER, "차주로 가입된 번호는 주선사 담당자가 될 수 없습니다.");
		}
		return user;
	}
}
