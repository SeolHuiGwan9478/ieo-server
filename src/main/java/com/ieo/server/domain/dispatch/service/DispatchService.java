package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.broker.dto.BrokerMetricsResponse;
import com.ieo.server.domain.broker.service.BrokerMetricsService;
import com.ieo.server.domain.dispatch.dto.CreateDispatchRequestRequest;
import com.ieo.server.domain.dispatch.dto.CreateOrderReportRequest;
import com.ieo.server.domain.dispatch.dto.DispatchRequestResponse;
import com.ieo.server.domain.dispatch.dto.OrderReportResponse;
import com.ieo.server.domain.dispatch.dto.OrderResponse;
import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.entity.OrderReport;
import com.ieo.server.domain.dispatch.enums.ConfirmMode;
import com.ieo.server.domain.dispatch.event.DispatchRequested;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.dispatch.repository.OrderReportRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차주의 오더 탐색과 배차 요청 (DSP-010, 020, 023, 024). 차주 계정만 쓸 수 있다.
 * 목록에는 내 차량 중 하나라도 맡을 수 있는 오더만 보인다(차종 같고 톤수가 오더 이상).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DispatchService {

	/** 대기 중인 요청은 3건까지 (DSP-024) */
	static final int MAX_PENDING_REQUESTS = 3;
	static final int DEFAULT_RADIUS_M = 50_000;
	static final int MAX_RADIUS_M = 300_000;
	/** 거리·차량 조건으로 거르기 전에 DB에서 읽는 최대 수 */
	private static final int CANDIDATE_LIMIT = 500;

	private final OrderRepository orderRepository;
	private final DispatchRequestRepository requestRepository;
	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;
	private final TripRepository tripRepository;
	private final DispatchConfirmer confirmer;
	private final OrderReportRepository reportRepository;
	private final BrokerMetricsService metricsService;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	/** 모집 중 오더. 상차가 빠른 순서. 좌표를 주면 상차지까지 직선거리로 거르고 distanceM을 붙인다 */
	@Transactional(readOnly = true)
	public List<OrderResponse> getOpenOrders(Long userId, Double lat, Double lng, Integer radiusM, Integer size) {
		requireDriver(userId);
		if ((lat == null) != (lng == null)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "위치는 위도와 경도를 함께 보내 주세요.");
		}
		if (lat != null) {
			Site.validateLocation(lat, lng);
		}
		int radius = radiusM == null ? DEFAULT_RADIUS_M : radiusM;
		if (radius < 1_000 || radius > MAX_RADIUS_M) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "검색 반경은 1km에서 300km 사이로 정해 주세요.");
		}
		int limit = size == null ? 20 : Math.clamp(size, 1, 50);
		Instant now = clock.instant();
		List<Vehicle> vehicles = vehicleRepository.findAllByDriverUserIdOrderByCreatedAtAsc(userId);

		List<Order> orders = orderRepository.findOpenFor(userId, now, Pageable.ofSize(CANDIDATE_LIMIT)).stream()
				.filter(order -> vehicles.stream().anyMatch(order::fits))
				.filter(order -> lat == null || order.getPickupSite().isWithin(lat, lng, radius))
				.limit(limit)
				.toList();
		Map<Long, DispatchRequest> mine = myRequestsByOrder(userId, orders);
		Map<Long, BrokerMetricsResponse> metrics = metricsService.getAll(
				orders.stream().map(order -> order.getBroker().getId()).toList());
		return orders.stream()
				.map(order -> OrderResponse.of(order, now,
						lat == null ? null : Math.round(order.getPickupSite().distanceMeters(lat, lng)),
						toResponse(mine.get(order.getId()), now), null, metrics.get(order.getBroker().getId())))
				.toList();
	}

	/** 오더 상세. 지정 배차 오더는 그 차주에게만 보인다 */
	@Transactional(readOnly = true)
	public OrderResponse getOrder(Long userId, Long orderId) {
		requireDriver(userId);
		Order order = findVisible(userId, orderId);
		Instant now = clock.instant();
		DispatchRequest mine = requestRepository.findByOrderIdAndDriverUserId(orderId, userId).orElse(null);
		return OrderResponse.of(order, now, null, toResponse(mine, now), null,
				metricsService.getAll(List.of(order.getBroker().getId())).get(order.getBroker().getId()));
	}

	/**
	 * 배차 요청 (DSP-020). 차량을 비우면 기본 차량.
	 * 자동 확정(FIRST) 오더면 바로 확정되고 운행이 생긴다
	 */
	public DispatchRequestResponse request(Long userId, Long orderId, CreateDispatchRequestRequest body) {
		Driver driver = requireDriver(userId);
		Order order = findVisible(userId, orderId);
		Vehicle vehicle = resolveVehicle(userId, body == null ? null : body.vehicleId());
		Instant now = clock.instant();

		order.checkRequestable(driver, vehicle, now);
		if (requestRepository.findByOrderIdAndDriverUserId(orderId, userId).isPresent()) {
			throw new BusinessException(ErrorCode.DISPATCH_ALREADY_REQUESTED);
		}
		long pending = requestRepository.countByDriverUserIdAndStatusAndCreatedAtAfter(
				userId, DispatchRequestStatus.PENDING, now.minus(DispatchRequest.TTL));
		if (pending >= MAX_PENDING_REQUESTS) {
			throw new BusinessException(ErrorCode.DISPATCH_REQUEST_LIMIT);
		}

		DispatchRequest request;
		try {
			request = requestRepository.saveAndFlush(DispatchRequest.create(order, driver, vehicle));
		} catch (DataIntegrityViolationException e) {
			// 같은 오더에 동시에 두 번 요청한 경우. (order, driver) 유니크 제약이 하나만 남긴다
			throw new BusinessException(ErrorCode.DISPATCH_ALREADY_REQUESTED);
		}
		Long tripId = null;
		if (order.getConfirmMode() == ConfirmMode.FIRST) {
			tripId = confirmer.confirm(order, request, now).getId();
		} else {
			events.publishEvent(new DispatchRequested(request.getId(), request.expiresAt()));
		}
		return DispatchRequestResponse.of(request, now, tripId);
	}

	/** 요청 철회. 대기 중일 때만 */
	public DispatchRequestResponse withdraw(Long userId, Long requestId) {
		requireDriver(userId);
		DispatchRequest request = requestRepository.findById(requestId)
				.filter(found -> found.isRequestedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.DISPATCH_REQUEST_NOT_FOUND));
		Instant now = clock.instant();
		request.withdraw(now);
		return DispatchRequestResponse.of(request, now, null);
	}

	/**
	 * 오더 신고 (DSP-042): 허위, 과적 요구, 운임 미지급, 정보 불일치. 오더 하나에 한 번만.
	 * 운임 미지급처럼 끝난 오더도 신고할 수 있게 상태는 따지지 않는다.
	 * TODO: 운영자 확인·이용 제한(DSP-043)은 운영자 화면을 만들 때 붙인다
	 */
	public OrderReportResponse report(Long userId, Long orderId, CreateOrderReportRequest request) {
		Driver driver = requireDriver(userId);
		Order order = findVisible(userId, orderId);
		if (reportRepository.existsByOrderIdAndReporterUserId(orderId, userId)) {
			throw new BusinessException(ErrorCode.ORDER_ALREADY_REPORTED);
		}
		try {
			return OrderReportResponse.from(
					reportRepository.saveAndFlush(OrderReport.of(order, driver, request.type(), request.memo())));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.ORDER_ALREADY_REPORTED);
		}
	}

	/** 내 요청. 최근 것 먼저. 확정되거나 확정 뒤 취소된 요청은 생긴 운행 id를 붙인다 */
	@Transactional(readOnly = true)
	public List<DispatchRequestResponse> getMyRequests(Long userId) {
		requireDriver(userId);
		Instant now = clock.instant();
		return requestRepository.findAllByDriverUserIdOrderByCreatedAtDesc(userId).stream()
				.map(request -> toResponse(request, now))
				.toList();
	}

	private DispatchRequestResponse toResponse(DispatchRequest request, Instant now) {
		if (request == null) {
			return null;
		}
		boolean hasTrip = request.getStatus() == DispatchRequestStatus.CONFIRMED
				|| request.getStatus() == DispatchRequestStatus.CANCELED;
		Long tripId = hasTrip
				? tripRepository.findFirstByOrderIdAndDriverUserIdOrderByCreatedAtDesc(
						request.getOrder().getId(), request.getDriver().getUserId()).map(Trip::getId).orElse(null)
				: null;
		return DispatchRequestResponse.of(request, now, tripId);
	}

	private Map<Long, DispatchRequest> myRequestsByOrder(Long userId, List<Order> orders) {
		if (orders.isEmpty()) {
			return Map.of();
		}
		List<Long> orderIds = orders.stream().map(Order::getId).toList();
		return requestRepository.findAllByDriverUserIdAndOrderIdIn(userId, orderIds).stream()
				.collect(Collectors.toMap(request -> request.getOrder().getId(), Function.identity()));
	}

	/** 남에게 지정된 오더는 있는지도 알려 주지 않도록 404 */
	private Order findVisible(Long userId, Long orderId) {
		return orderRepository.findById(orderId)
				.filter(order -> order.getTargetDriver() == null || order.getTargetDriver().getUserId().equals(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	private Vehicle resolveVehicle(Long userId, Long vehicleId) {
		if (vehicleId == null) {
			return vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId)
					.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NO_VEHICLE));
		}
		return vehicleRepository.findById(vehicleId)
				.filter(vehicle -> vehicle.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
	}

	private Driver requireDriver(Long userId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DISPATCH_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return driver;
	}
}
