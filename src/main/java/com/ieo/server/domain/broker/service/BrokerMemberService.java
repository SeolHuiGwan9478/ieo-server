package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.BrokerMemberResponse;
import com.ieo.server.domain.broker.dto.SentInvitationResponse;
import com.ieo.server.domain.broker.entity.BrokerInvitation;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.broker.enums.BrokerInvitationStatus;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerInvitationRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
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
 * 우리 회사 담당자 관리 (BRK-001 담당자 여러 명).
 * 목록과 보낸 초대는 담당자 모두 볼 수 있고, 초대·직책 변경·내보내기는 대표만 한다.
 * 대표는 여러 명 둘 수 있지만, 마지막 대표는 강등·내보내기·나가기·탈퇴할 수 없다.
 * 담당자 수는 플랜과 상관없이 무제한이다 (2026-11-14).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BrokerMemberService {

	private final BrokerRepository brokerRepository;
	private final BrokerMemberRepository memberRepository;
	private final BrokerInvitationRepository invitationRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<BrokerMemberResponse> getMembers(Long userId) {
		BrokerMember me = requireActiveMember(userId);
		return memberRepository.findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(me.getBroker().getId()).stream()
				.map(member -> BrokerMemberResponse.from(member, userId))
				.toList();
	}

	public BrokerMemberResponse changePosition(Long userId, Long memberUserId, BrokerMemberPosition position) {
		BrokerMember owner = requireOwnerAndLock(userId);
		BrokerMember target = findColleague(owner, memberUserId);
		if (target.isOwner() && position != BrokerMemberPosition.OWNER) {
			requireAnotherOwner(owner.getBroker().getId());
		}
		target.changePosition(position);
		return BrokerMemberResponse.from(target, userId);
	}

	/** 대표가 담당자를 내보낸다. 기록은 남기고 소속만 끊는다 */
	public void remove(Long userId, Long memberUserId) {
		BrokerMember owner = requireOwnerAndLock(userId);
		leaveCompany(findColleague(owner, memberUserId));
	}

	/** 스스로 회사를 나간다 */
	public void leave(Long userId) {
		BrokerMember me = requireActiveMember(userId);
		lockBroker(me);
		leaveCompany(me);
	}

	/** 탈퇴할 때 UserService가 부른다. 소속이 있으면 나가고, 마지막 대표면 탈퇴를 막는다 */
	public void leaveOnWithdraw(Long userId) {
		memberRepository.findById(userId)
				.filter(BrokerMember::isActive)
				.ifPresent(member -> {
					lockBroker(member);
					leaveCompany(member);
				});
	}

	@Transactional(readOnly = true)
	public List<SentInvitationResponse> getInvitations(Long userId) {
		BrokerMember me = requireActiveMember(userId);
		Instant now = clock.instant();
		return invitationRepository.findAllByBrokerIdAndStatusOrderByCreatedAtDesc(
						me.getBroker().getId(), BrokerInvitationStatus.PENDING).stream()
				.filter(invitation -> invitation.isPendingAt(now))
				.map(invitation -> SentInvitationResponse.from(invitation, now))
				.toList();
	}

	/**
	 * 번호로 초대한다. 그 번호가 가입했는지, 다른 회사 소속인지는 알려 주지 않는다 (수락할 때 막는다).
	 * 문자는 보내지 않는다. 초대받은 사람은 콘솔에 로그인하면 초대를 본다 (2026-11-13).
	 */
	public SentInvitationResponse invite(Long userId, String phone) {
		BrokerMember owner = requireOwnerAndLock(userId);
		Long brokerId = owner.getBroker().getId();
		Instant now = clock.instant();
		// 가입된 번호면 역할과 소속을 먼저 본다: 차주 계정은 담당자가 될 수 없고, 다른 회사에 소속돼 있으면 수락할 수 없으니 초대 단계에서 막는다
		userRepository.findByPhone(phone).ifPresent(user -> {
			if (user.getRole() != UserRole.BROKER) {
				throw new BusinessException(ErrorCode.BROKER_INVITATION_NOT_BROKER);
			}
			memberRepository.findById(user.getId()).filter(BrokerMember::isActive).ifPresent(member -> {
				throw new BusinessException(member.getBroker().getId().equals(brokerId)
						? ErrorCode.BROKER_INVITATION_ALREADY_MEMBER
						: ErrorCode.BROKER_INVITATION_OTHER_COMPANY);
			});
		});
		boolean alreadyInvited = invitationRepository
				.findAllByBrokerIdAndPhoneAndStatus(brokerId, phone, BrokerInvitationStatus.PENDING).stream()
				.anyMatch(invitation -> invitation.isPendingAt(now));
		if (alreadyInvited) {
			throw new BusinessException(ErrorCode.BROKER_INVITATION_DUPLICATE);
		}
		BrokerInvitation invitation = invitationRepository.save(BrokerInvitation.create(owner, phone));
		return SentInvitationResponse.from(invitation, now);
	}

	public void cancelInvitation(Long userId, Long invitationId) {
		BrokerMember owner = requireOwnerAndLock(userId);
		BrokerInvitation invitation = invitationRepository.findByIdForUpdate(invitationId)
				.filter(found -> found.belongsTo(owner.getBroker().getId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_INVITATION_NOT_FOUND));
		invitation.cancel(clock.instant());
	}

	private void leaveCompany(BrokerMember member) {
		if (member.isOwner()) {
			requireAnotherOwner(member.getBroker().getId());
		}
		member.remove(clock.instant());
	}

	/** 회사 행을 잠근 뒤 세므로, 동시에 들어온 강등·내보내기도 대표를 0명으로 만들지 못한다 */
	private void requireAnotherOwner(Long brokerId) {
		if (memberRepository.countByBrokerIdAndPositionAndRemovedAtIsNull(brokerId, BrokerMemberPosition.OWNER) <= 1) {
			throw new BusinessException(ErrorCode.BROKER_LAST_OWNER);
		}
	}

	private BrokerMember requireOwnerAndLock(Long userId) {
		BrokerMember member = requireActiveMember(userId);
		if (!member.isOwner()) {
			throw new BusinessException(ErrorCode.BROKER_OWNER_ONLY);
		}
		lockBroker(member);
		return member;
	}

	private void lockBroker(BrokerMember member) {
		brokerRepository.findByIdForUpdate(member.getBroker().getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NOT_FOUND));
	}

	/** 같은 회사에 지금 소속된 담당자. 다른 회사 담당자는 404로 숨긴다 */
	private BrokerMember findColleague(BrokerMember me, Long memberUserId) {
		return memberRepository.findById(memberUserId)
				.filter(member -> member.isActive() && member.getBroker().getId().equals(me.getBroker().getId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_MEMBER_NOT_FOUND));
	}

	private BrokerMember requireActiveMember(Long userId) {
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NO_MEMBERSHIP));
		if (member.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		return member;
	}
}
