package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.DriverLinkResponse;
import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
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
	private final DriverRepository driverRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	/** 내 번호로 온, 수락을 기다리는 요청 */
	@Transactional(readOnly = true)
	public List<DriverLinkResponse> getInvitations(Long userId) {
		Driver driver = requireDriver(userId);
		Instant now = clock.instant();
		return linkRepository.findAllByPhoneAndStatusOrderByCreatedAtDesc(
						driver.getUser().getPhone(), DriverLinkStatus.PENDING).stream()
				.filter(link -> link.isPendingAt(now))
				.map(link -> DriverLinkResponse.from(link, now))
				.toList();
	}

	/** 내가 단골로 연결된 회사 */
	@Transactional(readOnly = true)
	public List<DriverLinkResponse> getLinks(Long userId) {
		requireDriver(userId);
		Instant now = clock.instant();
		return linkRepository.findAllByDriverUserIdAndStatusOrderByLinkedAtDesc(userId, DriverLinkStatus.LINKED).stream()
				.map(link -> DriverLinkResponse.from(link, now))
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
