package com.ieo.server.domain.trip.service;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.site.service.SiteService;
import com.ieo.server.domain.trip.dto.CreateTripRequest;
import com.ieo.server.domain.trip.dto.TripResponse;
import com.ieo.server.domain.trip.dto.TripStopRequest;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.domain.wait.service.WaitService;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운행 등록·조회와 출발·완료·취소 (TRIP-001, 002, 010). 차주 계정만 쓸 수 있다.
 * 상하차지 진입·이탈에 따른 상태 전환(AT_PICKUP, IN_TRANSIT, AT_DROPOFF, 하차지 이탈 → COMPLETED)은 WaitService가 맡는다.
 * 출발한 운행은 차주당 하나만 둔다: 차량 앱이 지오펜스를 걸 운행이 하나로 정해져야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TripService {

	static final int MAX_LIST_SIZE = 50;

	private final TripRepository tripRepository;
	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;
	private final BrokerRepository brokerRepository;
	private final SiteRepository siteRepository;
	private final SiteService siteService;
	private final WaitService waitService;
	private final Clock clock;

	/** 직접 등록. 예약 상태로 만든다 */
	public TripResponse register(Long userId, CreateTripRequest request) {
		Driver driver = requireDriver(userId);
		Vehicle vehicle = resolveVehicle(userId, request.vehicleId());
		Broker broker = request.brokerId() == null ? null : brokerRepository.findById(request.brokerId())
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NOT_FOUND));
		Site pickup = resolveSite(request.pickup(), driver);
		Site dropoff = resolveSite(request.dropoff(), driver);

		Trip trip = Trip.create(driver, vehicle, broker, request.brokerName(), request.fare(), request.paymentType(),
				TripSource.MANUAL, pickup, request.pickup().plannedAt(), dropoff, request.dropoff().plannedAt());
		return TripResponse.from(tripRepository.save(trip));
	}

	/** 내 운행 목록. 최근 등록한 순서, 최대 50개 */
	@Transactional(readOnly = true)
	public List<TripResponse> getMyTrips(Long userId, Integer size) {
		requireDriver(userId);
		int limit = size == null ? 20 : Math.clamp(size, 1, MAX_LIST_SIZE);
		return tripRepository.findAllByDriverUserIdOrderByCreatedAtDesc(userId, Pageable.ofSize(limit)).stream()
				.map(TripResponse::from)
				.toList();
	}

	/** 출발해서 진행 중인 운행. 없으면 비어 있다 */
	@Transactional(readOnly = true)
	public Optional<TripResponse> getCurrent(Long userId) {
		requireDriver(userId);
		return tripRepository.findFirstByDriverUserIdAndStatusInOrderByCreatedAtDesc(userId, TripStatus.IN_PROGRESS)
				.map(TripResponse::from);
	}

	@Transactional(readOnly = true)
	public TripResponse getTrip(Long userId, Long tripId) {
		return TripResponse.from(findMine(userId, tripId));
	}

	/** 출발: 상차지로 이동. 이미 진행 중인 운행이 있으면 409 */
	public TripResponse start(Long userId, Long tripId) {
		Trip trip = findMine(userId, tripId);
		if (trip.getStatus() == TripStatus.SCHEDULED
				&& tripRepository.existsByDriverUserIdAndStatusIn(userId, TripStatus.IN_PROGRESS)) {
			throw new BusinessException(ErrorCode.TRIP_ALREADY_IN_PROGRESS);
		}
		trip.start(clock.instant());
		return TripResponse.from(trip);
	}

	/** 하차 완료. 하차지에 도착한 운행만. 하차지 대기 기록이 남아 있으면 지금 시각으로 이탈 처리한다 */
	public TripResponse complete(Long userId, Long tripId) {
		Trip trip = findMine(userId, tripId);
		Instant now = clock.instant();
		trip.complete(now);
		waitService.departDropoffIfOpen(trip, now);
		return TripResponse.from(trip);
	}

	public TripResponse cancel(Long userId, Long tripId) {
		Trip trip = findMine(userId, tripId);
		trip.cancel(clock.instant());
		return TripResponse.from(trip);
	}

	/** 차량을 고르지 않으면 기본 차량. 차량이 하나도 없으면 먼저 등록하게 한다 */
	private Vehicle resolveVehicle(Long userId, Long vehicleId) {
		if (vehicleId == null) {
			return vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId)
					.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NO_VEHICLE));
		}
		return vehicleRepository.findById(vehicleId)
				.filter(vehicle -> vehicle.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
	}

	private Site resolveSite(TripStopRequest stop, Driver driver) {
		if (stop.siteId() != null) {
			return siteRepository.findById(stop.siteId())
					.orElseThrow(() -> new BusinessException(ErrorCode.SITE_NOT_FOUND));
		}
		if (stop.site() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "상하차지를 골라 주세요.");
		}
		return siteService.findOrCreate(stop.site(), driver).site();
	}

	/** 차주 프로필이 없으면 차주가 아닌 계정이다. 탈퇴한 계정은 없는 계정으로 본다 */
	private Driver requireDriver(Long userId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return driver;
	}

	/** 남의 운행이면 있는지도 알려 주지 않도록 404 */
	private Trip findMine(Long userId, Long tripId) {
		requireDriver(userId);
		return tripRepository.findById(tripId)
				.filter(trip -> trip.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
	}
}
