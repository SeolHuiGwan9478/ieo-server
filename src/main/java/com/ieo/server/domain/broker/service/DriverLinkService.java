package com.ieo.server.domain.broker.service;

import java.util.Map;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.broker.dto.BrokerMetricsResponse;
import com.ieo.server.domain.broker.dto.DriverLinkResponse;
import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.enums.ConsentType;
import com.ieo.server.domain.user.service.ConsentService;
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
 * 차주 쪽 단골 연결 (BRK-002). 내 번호로 온 요청을 수락·거절하고, 연결된 회사를 보고 해제한다.
 * 수락이 곧 그 회사에 대한 위치·운행 정보 공유 동의다 (그 회사 일을 하는 동안만, 해제하면 끝).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DriverLinkService {

	private final BrokerDriverLinkRepository linkRepository;
	private final ConsentService consentService;
	private final DriverRepository driverRepository;
	private final UserRepository userRepository;
	private final Clock clock;
	private final BrokerMetricsService metricsService;
	private final TripRepository tripRepository;

	/** 내 번호로 온, 수락을 기다리는 요청 */
	@Transactional(readOnly = true)
	public List<DriverLinkResponse> getInvitations(Long userId) {
		Driver driver = requireDriver(userId);
		Instant now = clock.instant();
		List<BrokerDriverLink> pending = linkRepository.findAllByPhoneAndStatusOrderByCreatedAtDesc(
						driver.getUser().getPhone(), DriverLinkStatus.PENDING).stream()
				.filter(link -> link.isPendingAt(now))
				.toList();
		return withRecord(userId, pending, now);
	}

	/** 내가 단골로 연결된 회사 */
	@Transactional(readOnly = true)
	public List<DriverLinkResponse> getLinks(Long userId) {
		requireDriver(userId);
		Instant now = clock.instant();
		return withRecord(userId, linkRepository.findAllByDriverUserIdAndStatusOrderByLinkedAtDesc(userId, DriverLinkStatus.LINKED), now);
	}

	/** 회사마다 신뢰 지표와 이 기사님과 끝낸 운행 수를 붙인다. 지표는 한 번에 모아 읽는다 */
	private List<DriverLinkResponse> withRecord(Long userId, List<BrokerDriverLink> links, Instant now) {
		Map<Long, BrokerMetricsResponse> metrics = metricsService.getAll(
				links.stream().map(link -> link.getBroker().getId()).distinct().toList());
		return links.stream()
				.map(link -> DriverLinkResponse.from(link, now, metrics.get(link.getBroker().getId()),
						tripRepository.countByDriverUserIdAndBrokerIdAndStatus(userId, link.getBroker().getId(), TripStatus.COMPLETED)))
				.toList();
	}

	public DriverLinkResponse accept(Long userId, Long linkId) {
		Driver driver = requireDriver(userId);
		BrokerDriverLink link = findInvitedToMe(driver, linkId);
		if (linkRepository.existsByBrokerIdAndDriverUserIdAndStatus(
				link.getBroker().getId(), userId, DriverLinkStatus.LINKED)) {
			throw new BusinessException(ErrorCode.DRIVER_LINK_ALREADY_LINKED);
		}
		Instant now = clock.instant();
		link.accept(driver, now);
		// 수락 화면에서 위치·도착 시각 공유를 안내하고 받으므로 공유 동의로 기록한다
		consentService.change(userId, ConsentType.BROKER_SHARE, true);
		return DriverLinkResponse.from(link, now);
	}

	public void decline(Long userId, Long linkId) {
		Driver driver = requireDriver(userId);
		findInvitedToMe(driver, linkId).decline(clock.instant());
	}

	/** 해제하면 그 회사에 위치 공유가 끝나고 새 지정 배차도 오지 않는다. 진행 중인 운행은 그대로 */
	public void unlink(Long userId, Long linkId) {
		requireDriver(userId);
		BrokerDriverLink link = linkRepository.findByIdForUpdate(linkId)
				.filter(found -> found.isLinkedTo(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_LINK_NOT_FOUND));
		link.unlink(clock.instant());
	}

	/** 내 번호로 온 요청이 아니면 있는지도 알려 주지 않는다 */
	private BrokerDriverLink findInvitedToMe(Driver driver, Long linkId) {
		return linkRepository.findByIdForUpdate(linkId)
				.filter(link -> link.getPhone().equals(driver.getUser().getPhone()))
				.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_LINK_NOT_FOUND));
	}

	private Driver requireDriver(Long userId) {
		User user = userRepository.findById(userId)
				.filter(found -> found.getStatus() != UserStatus.WITHDRAWN)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (user.getRole() != UserRole.DRIVER) {
			throw new BusinessException(ErrorCode.DRIVER_LINK_NOT_DRIVER);
		}
		return driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DRIVER_LINK_NOT_DRIVER));
	}
}
