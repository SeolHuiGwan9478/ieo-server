package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.driver.service.DriverRecordService;
import com.ieo.server.domain.broker.dto.LinkedDriverResponse;
import com.ieo.server.domain.broker.dto.SentDriverInvitationResponse;
import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우리 회사 단골 차주 관리 (BRK-002). 매일 하는 일이라 대표가 아닌 담당자도 할 수 있다.
 * 연결 요청은 번호로 보내고, 그 번호가 가입했는지·차주인지는 알려 주지 않는다 (차주가 수락할 때 확인한다).
 * 단골 관리는 정회원 기능이지만 요금제(12)를 만든 뒤 제한을 붙인다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BrokerDriverService {

	private final BrokerRepository brokerRepository;
	private final BrokerMemberRepository memberRepository;
	private final BrokerDriverLinkRepository linkRepository;
	private final UserRepository userRepository;
	private final VehicleRepository vehicleRepository;
	private final Clock clock;
	private final DriverRecordService driverRecordService;

	/** 우리 회사 단골 차주. 최근 연결한 순서. 기본 차량을 같이 보여 준다 (지정 배차할 때 차를 보고 고른다) */
	@Transactional(readOnly = true)
	public List<LinkedDriverResponse> getDrivers(Long userId) {
		BrokerMember me = requireActiveMember(userId);
		return linkRepository.findAllByBrokerIdAndStatusOrderByLinkedAtDesc(me.getBroker().getId(), DriverLinkStatus.LINKED)
				.stream()
				.map(link -> LinkedDriverResponse.from(link,
						vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(link.getDriver().getUserId()).orElse(null),
						driverRecordService.of(link.getDriver().getUserId())))
				.toList();
	}

	/** 수락을 기다리는 요청. 만료된 것은 빼고 최근 순서 */
	@Transactional(readOnly = true)
	public List<SentDriverInvitationResponse> getInvitations(Long userId) {
		BrokerMember me = requireActiveMember(userId);
		Instant now = clock.instant();
		return linkRepository.findAllByBrokerIdAndStatusOrderByCreatedAtDesc(me.getBroker().getId(), DriverLinkStatus.PENDING)
				.stream()
				.filter(link -> link.isPendingAt(now))
				.map(link -> SentDriverInvitationResponse.from(link, now))
				.toList();
	}

	/**
	 * 번호로 단골 연결을 요청한다. 미가입 번호여도 저장하고, 그 번호로 가입한 차주에게 보인다.
	 * 이미 우리 단골이거나 같은 번호로 대기 중인 요청이 있으면 409. 문자는 보내지 않는다. 차주는 차량 앱에서 요청을 본다 (2026-11-13).
	 */
	public SentDriverInvitationResponse invite(Long userId, String phone) {
		BrokerMember me = requireActiveMember(userId);
		Long brokerId = me.getBroker().getId();
		// 같은 번호로 동시에 두 번 요청해도 하나만 남게 회사 행을 잠근다
		brokerRepository.findByIdForUpdate(brokerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NOT_FOUND));
		Instant now = clock.instant();

		// 가입된 번호인데 차주가 아니면(주선사 담당자·운영자) 요청해 봐야 수락할 수 없으니 바로 막는다. 아직 가입 안 한 번호는 받는다
		userRepository.findByPhone(phone)
				.filter(user -> user.getRole() != UserRole.DRIVER)
				.ifPresent(user -> {
					throw new BusinessException(ErrorCode.DRIVER_LINK_NOT_DRIVER_PHONE);
				});
		boolean alreadyLinked = userRepository.findByPhone(phone)
				.filter(user -> user.getRole() == UserRole.DRIVER)
				.filter(user -> linkRepository.existsByBrokerIdAndDriverUserIdAndStatus(
						brokerId, user.getId(), DriverLinkStatus.LINKED))
				.isPresent();
		if (alreadyLinked) {
			throw new BusinessException(ErrorCode.DRIVER_LINK_ALREADY_LINKED);
		}
		boolean alreadyRequested = linkRepository
				.findAllByBrokerIdAndPhoneAndStatus(brokerId, phone, DriverLinkStatus.PENDING).stream()
				.anyMatch(link -> link.isPendingAt(now));
		if (alreadyRequested) {
			throw new BusinessException(ErrorCode.DRIVER_LINK_DUPLICATE);
		}

		BrokerDriverLink link = linkRepository.save(BrokerDriverLink.request(me, phone));
		return SentDriverInvitationResponse.from(link, now);
	}

	public void cancelInvitation(Long userId, Long linkId) {
		BrokerMember me = requireActiveMember(userId);
		findOurs(me, linkId).cancel(clock.instant());
	}

	/** 단골 해제. 이미 확정된 배차와 운행은 그대로 두고, 새 지정 배차만 막힌다 */
	public void unlink(Long userId, Long linkId) {
		BrokerMember me = requireActiveMember(userId);
		findOurs(me, linkId).unlink(clock.instant());
	}

	/** 다른 회사의 요청·연결은 있는지도 알려 주지 않는다 */
	private BrokerDriverLink findOurs(BrokerMember me, Long linkId) {
		return linkRepository.findByIdForUpdate(linkId)
				.filter(link -> link.belongsTo(me.getBroker().getId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_LINK_NOT_FOUND));
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
