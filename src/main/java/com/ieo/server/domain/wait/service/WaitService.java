package com.ieo.server.domain.wait.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.service.LedgerRecorder;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.wait.dto.EditWaitRequest;
import com.ieo.server.domain.wait.dto.WaitProofLinkResponse;
import com.ieo.server.domain.wait.dto.WaitProofResponse;
import com.ieo.server.domain.wait.dto.WaitRecordResponse;
import com.ieo.server.domain.wait.entity.WaitPolicy;
import com.ieo.server.domain.wait.entity.WaitProof;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.enums.WaitStatus;
import com.ieo.server.domain.wait.repository.WaitPolicyRepository;
import com.ieo.server.domain.wait.repository.WaitProofRepository;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기 기록 (02 대기). 정차·이탈 판단은 차량 앱이 하고(속도·기어·지오펜스), 서버는 판단한 시각을 받아 기록한다.
 * 진입·이탈은 운행 상태도 함께 바꾼다:
 * 상차지 진입 → AT_PICKUP, 상차지 이탈 → IN_TRANSIT, 하차지 진입 → AT_DROPOFF, 하차지 이탈 → COMPLETED.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WaitService {

	/** 앱과 서버 시계 차이를 이 정도까지는 받아 준다 */
	private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);
	/** 증빙 링크는 30일 동안 열린다 */
	static final Duration PROOF_TTL = Duration.ofDays(30);

	private final WaitRecordRepository recordRepository;
	private final WaitPolicyRepository policyRepository;
	private final WaitProofRepository proofRepository;
	private final TripRepository tripRepository;
	private final DriverRepository driverRepository;
	private final SiteStatsCalculator siteStatsCalculator;
	private final LedgerRecorder ledgerRecorder;
	private final Clock clock;

	public record Arrival(WaitRecordResponse record, boolean created) {
	}

	/**
	 * 진입 (지오펜스 안 3분 정차, WAIT-001). 운행 주선사의 지금 정책을 붙인다.
	 * 통신이 끊겨 다시 보내도 기록이 둘이 되지 않도록, 이미 진입했으면 그 기록을 돌려준다
	 */
	public Arrival arrive(Long userId, Long tripId, StopType type, Instant at) {
		Trip trip = findMine(userId, tripId);
		TripStop stop = stopOf(trip, type);
		Instant arrivedAt = resolveTime(at);
		Optional<WaitRecord> existing = recordRepository.findByTripStopId(stop.getId());
		if (existing.isPresent()) {
			return new Arrival(WaitRecordResponse.from(existing.get()), false);
		}

		if (type == StopType.PICKUP) {
			trip.arriveAtPickup();
		} else {
			trip.arriveAtDropoff();
		}
		WaitPolicy policy = trip.getBroker() == null ? null
				: policyRepository.findFirstByBrokerIdAndActiveTrue(trip.getBroker().getId()).orElse(null);
		WaitRecord record = recordRepository.save(WaitRecord.start(stop, arrivedAt, policy));
		return new Arrival(WaitRecordResponse.from(record), true);
	}

	/** 작업 시작 ("상차 시작"·"하차 시작", WAIT-004) */
	public WaitRecordResponse startWork(Long userId, Long tripId, StopType type, Instant at) {
		WaitRecord record = findRecord(userId, tripId, type);
		record.startWork(resolveTime(at));
		siteStatsCalculator.recalculate(record.getTripStop().getSite());
		return WaitRecordResponse.from(record);
	}

	/** 이탈 (WAIT-005). 상차지면 운송 중으로, 하차지면 운행 완료로 바꾸고 장부 한 줄을 만든다 */
	public WaitRecordResponse depart(Long userId, Long tripId, StopType type, Instant at) {
		Trip trip = findMine(userId, tripId);
		WaitRecord record = recordRepository.findByTripStopId(stopOf(trip, type).getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.WAIT_NOT_FOUND));
		Instant departedAt = resolveTime(at);

		record.depart(departedAt);
		if (type == StopType.PICKUP) {
			trip.departPickup();
		} else {
			trip.complete(departedAt);
			ledgerRecorder.recordTrip(trip);
		}
		siteStatsCalculator.recalculate(record.getTripStop().getSite());
		return WaitRecordResponse.from(record);
	}

	/** 시각 고치기 (WAIT-006). 원래 값은 처음 고칠 때 남는다 */
	public WaitRecordResponse edit(Long userId, Long tripId, StopType type, EditWaitRequest request) {
		WaitRecord record = findRecord(userId, tripId, type);
		Instant latest = clock.instant().plus(CLOCK_SKEW);
		for (Instant time : new Instant[] {request.arrivedAt(), request.workStartedAt(), request.departedAt()}) {
			if (time != null && time.isAfter(latest)) {
				throw new BusinessException(ErrorCode.WAIT_INVALID_TIME);
			}
		}
		record.edit(request.arrivedAt(), request.workStartedAt(), request.departedAt(), clock.instant());
		siteStatsCalculator.recalculate(record.getTripStop().getSite());
		return WaitRecordResponse.from(record);
	}

	/** 운행의 대기 기록. 상차 먼저 */
	@Transactional(readOnly = true)
	public List<WaitRecordResponse> getWaits(Long userId, Long tripId) {
		Trip trip = findMine(userId, tripId);
		return recordRepository.findAllByTripStopTripId(trip.getId()).stream()
				.sorted(Comparator.comparing(record -> record.getTripStop().getType()))
				.map(WaitRecordResponse::from)
				.toList();
	}

	/**
	 * 증빙 링크를 만든다 (WAIT-020). 이탈까지 끝난 기록만. 이미 있으면 같은 링크를 돌려준다.
	 * TODO: 주선사에게 보내기(sentAt)는 09 알림을 만든 뒤 붙인다 (WAIT-022)
	 */
	public WaitProofLinkResponse createProof(Long userId, Long tripId, StopType type) {
		WaitRecord record = findRecord(userId, tripId, type);
		if (record.status() != WaitStatus.DONE) {
			throw new BusinessException(ErrorCode.WAIT_INVALID_STATUS, "이탈까지 기록된 대기만 증빙을 만들 수 있습니다.");
		}
		WaitProof proof = proofRepository.findByWaitRecordId(record.getId())
				.orElseGet(() -> proofRepository.save(WaitProof.create(record, clock.instant().plus(PROOF_TTL))));
		return WaitProofLinkResponse.from(proof);
	}

	/** 주선사가 링크로 보는 증빙. 로그인 없이 열린다. 없거나 만료되면 404 */
	@Transactional(readOnly = true)
	public WaitProofResponse getProof(String shareToken) {
		WaitProof proof = proofRepository.findByShareToken(shareToken)
				.filter(found -> !found.isExpired(clock.instant()))
				.orElseThrow(() -> new BusinessException(ErrorCode.WAIT_PROOF_NOT_FOUND));
		return WaitProofResponse.of(proof.getWaitRecord(), proof.getCreatedAt());
	}

	/** 하차지에서 대기 기록이 남아 있는데 운행을 직접 완료하면 그 기록도 이탈로 끝낸다 */
	public void departDropoffIfOpen(Trip trip, Instant now) {
		recordRepository.findByTripStopId(trip.dropoff().getId())
				.filter(record -> record.status() != WaitStatus.DONE)
				.ifPresent(record -> {
					record.depart(now);
					siteStatsCalculator.recalculate(record.getTripStop().getSite());
				});
	}

	/** 비우면 지금. 앱 시계가 앞서 있어도 1분까지는 받는다 */
	private Instant resolveTime(Instant at) {
		Instant now = clock.instant();
		if (at == null) {
			return now;
		}
		if (at.isAfter(now.plus(CLOCK_SKEW))) {
			throw new BusinessException(ErrorCode.WAIT_INVALID_TIME, "아직 오지 않은 시각은 기록할 수 없습니다.");
		}
		return at;
	}

	private WaitRecord findRecord(Long userId, Long tripId, StopType type) {
		Trip trip = findMine(userId, tripId);
		return recordRepository.findByTripStopId(stopOf(trip, type).getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.WAIT_NOT_FOUND));
	}

	private TripStop stopOf(Trip trip, StopType type) {
		return type == StopType.PICKUP ? trip.pickup() : trip.dropoff();
	}

	/** 남의 운행이면 있는지도 알려 주지 않도록 404 */
	private Trip findMine(Long userId, Long tripId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return tripRepository.findById(tripId)
				.filter(trip -> trip.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
	}
}
