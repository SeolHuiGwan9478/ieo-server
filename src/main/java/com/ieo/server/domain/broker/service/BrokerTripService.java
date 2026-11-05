package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.BrokerTripPageResponse;
import com.ieo.server.domain.broker.dto.BrokerTripResponse;
import com.ieo.server.domain.broker.dto.BrokerTripStopResponse;
import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerTripTab;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.enums.ProofPhotoType;
import com.ieo.server.domain.delivery.repository.DeliveryProofRepository;
import com.ieo.server.domain.delivery.repository.ProofPhotoRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.wait.entity.WaitProof;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitProofRepository;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사 콘솔의 진행 중 운행 (BRK-020, 021). 담당자 누구나 볼 수 있다.
 *
 * 우리 회사 오더로 배차된 운행(IEO_DISPATCH)만 보여 준다. 차주가 직접 등록하면서 우리 회사를 고른 운행은
 * 차주의 개인 장부이고 아무 회사나 고를 수 있어서 빼 둔다 (06 문서 6장 열린 질문).
 * 다른 회사 운행은 있는지도 알려 주지 않는다 (404).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerTripService {

	private static final int MAX_PAGE_SIZE = 100;

	private final BrokerMemberRepository memberRepository;
	private final BrokerDriverLinkRepository linkRepository;
	private final TripRepository tripRepository;
	private final WaitRecordRepository waitRecordRepository;
	private final WaitProofRepository waitProofRepository;
	private final DeliveryProofRepository deliveryProofRepository;
	private final ProofPhotoRepository photoRepository;

	/** 우리 회사 운행. 탭 기본은 진행 중, 최근 배차 순, page는 0부터 */
	public BrokerTripPageResponse getTrips(Long userId, BrokerTripTab tab, Integer page, Integer size) {
		BrokerMember member = requireActiveMember(userId);
		Long brokerId = member.getBroker().getId();
		PageRequest pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
				size == null ? 20 : Math.clamp(size, 1, MAX_PAGE_SIZE));
		Page<Trip> trips = tripRepository.findAllByBrokerIdAndSourceAndStatusInOrderByCreatedAtDesc(
				brokerId, TripSource.IEO_DISPATCH, (tab == null ? BrokerTripTab.IN_PROGRESS : tab).statuses(), pageable);
		return new BrokerTripPageResponse(toResponses(brokerId, trips.getContent()),
				trips.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
	}

	public BrokerTripResponse getTrip(Long userId, Long tripId) {
		BrokerMember member = requireActiveMember(userId);
		Long brokerId = member.getBroker().getId();
		Trip trip = tripRepository.findById(tripId)
				.filter(found -> found.getSource() == TripSource.IEO_DISPATCH)
				.filter(found -> found.getBroker() != null && found.getBroker().getId().equals(brokerId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
		return toResponses(brokerId, List.of(trip)).getFirst();
	}

	/** 대기 기록·증빙·인수증을 운행 수와 관계없이 몇 번의 조회로 모은다 */
	private List<BrokerTripResponse> toResponses(Long brokerId, List<Trip> trips) {
		if (trips.isEmpty()) {
			return List.of();
		}
		List<Long> tripIds = trips.stream().map(Trip::getId).toList();
		Set<Long> linkedDriverIds = linkRepository
				.findAllByBrokerIdAndStatusOrderByLinkedAtDesc(brokerId, DriverLinkStatus.LINKED).stream()
				.map(BrokerDriverLink::getDriver)
				.map(driver -> driver.getUserId())
				.collect(Collectors.toSet());

		List<WaitRecord> records = waitRecordRepository.findAllByTripStopTripIdIn(tripIds);
		Map<Long, WaitRecord> recordByStop = records.stream()
				.collect(Collectors.toMap(record -> record.getTripStop().getId(), Function.identity()));
		Map<Long, WaitProof> proofByRecord = records.isEmpty() ? Map.of()
				: waitProofRepository.findAllByWaitRecordIdIn(records.stream().map(WaitRecord::getId).toList()).stream()
						.collect(Collectors.toMap(proof -> proof.getWaitRecord().getId(), Function.identity()));

		Map<Long, DeliveryProof> receiptByTrip = deliveryProofRepository.findAllByTripIdIn(tripIds).stream()
				.collect(Collectors.toMap(proof -> proof.getTrip().getId(), Function.identity()));
		Set<Long> paperProofIds = receiptByTrip.isEmpty() ? Set.of()
				: new HashSet<>(photoRepository.findProofIdsHavingType(
						receiptByTrip.values().stream().map(DeliveryProof::getId).toList(), ProofPhotoType.PAPER));

		return trips.stream()
				.map(trip -> {
					DeliveryProof receipt = receiptByTrip.get(trip.getId());
					return BrokerTripResponse.of(trip,
							linkedDriverIds.contains(trip.getDriver().getUserId()),
							stop(trip.pickup(), recordByStop, proofByRecord),
							stop(trip.dropoff(), recordByStop, proofByRecord),
							receipt,
							receipt != null && paperProofIds.contains(receipt.getId()));
				})
				.toList();
	}

	private static BrokerTripStopResponse stop(TripStop stop, Map<Long, WaitRecord> recordByStop,
			Map<Long, WaitProof> proofByRecord) {
		WaitRecord record = recordByStop.get(stop.getId());
		return BrokerTripStopResponse.of(stop, record, record == null ? null : proofByRecord.get(record.getId()));
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
