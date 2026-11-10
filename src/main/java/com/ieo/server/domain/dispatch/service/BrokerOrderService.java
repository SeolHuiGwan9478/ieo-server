package com.ieo.server.domain.dispatch.service;

import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.document.business.ReviewProperties;
import com.ieo.server.domain.document.service.BrokerDocumentService;
import com.ieo.server.domain.dispatch.dto.ApplicantResponse;
import com.ieo.server.domain.dispatch.dto.CancelOrderRequest;
import com.ieo.server.domain.dispatch.dto.CreateOrderRequest;
import com.ieo.server.domain.dispatch.dto.OrderPageResponse;
import com.ieo.server.domain.dispatch.dto.OrderResponse;
import com.ieo.server.domain.dispatch.dto.OrderSiteRequest;
import com.ieo.server.domain.dispatch.entity.Cancellation;
import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.dispatch.repository.CancellationRepository;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.site.service.SiteService;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사의 오더 관리 (DSP-001, 002, 021, 022). 그 주선사 담당자만 쓸 수 있고, 다른 회사 오더는 404.
 * 확정·내리기는 매일 하는 일이라 대표가 아닌 담당자도 할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BrokerOrderService {

	static final int MAX_PAGE_SIZE = 100;
	/** 같은 상하차지로 보는 거리. SiteService.findOrCreate의 SAME_SITE_METERS와 같다 */
	private static final double SAME_SITE_METERS = 100;

	private final OrderRepository orderRepository;
	private final DispatchRequestRepository requestRepository;
	private final BrokerMemberRepository memberRepository;
	private final DriverRepository driverRepository;
	private final SiteRepository siteRepository;
	private final SiteService siteService;
	private final TripRepository tripRepository;
	private final DispatchConfirmer confirmer;
	private final DispatchCanceller canceller;
	private final CancellationRepository cancellationRepository;
	private final BrokerDriverLinkRepository linkRepository;
	private final BrokerDocumentService brokerDocumentService;
	private final ReviewProperties reviewProperties;
	private final Clock clock;

	public OrderResponse register(Long userId, CreateOrderRequest request) {
		BrokerMember member = requireMember(userId);
		requireVerified(member);
		Driver target = request.targetDriverId() == null ? null : requireLinkedDriver(member, request.targetDriverId());

		Order order = Order.draft()
				.createdBy(member)
				.pickupSite(resolveSite(request.pickup()))
				.dropoffSite(resolveSite(request.dropoff()))
				.pickupFrom(request.pickupFrom())
				.pickupUntil(request.pickupUntil())
				.dropoffBy(request.dropoffBy())
				.cargoCategory(request.cargoCategory())
				.cargoType(request.cargoType())
				.weightKg(request.weightKg())
				.tonnage(request.tonnage())
				.bodyType(request.bodyType())
				.loadingMethod(request.loadingMethod())
				.fare(request.fare())
				.billedFare(request.billedFare() != null ? request.billedFare()
						: Order.billedFareFor(request.fare(), request.feeRate()))
				.customerName(request.customerName())
				.paymentType(request.paymentType())
				// 비우면 회사 정산 주기 (BRK-003). 오더마다 다르게 하려면 직접 넣는다
				.expectedPaymentDays(request.expectedPaymentDays() != null
						? request.expectedPaymentDays() : member.getBroker().getSettlementDays())
				.consignmentType(request.consignmentType())
				.confirmMode(request.confirmMode())
				.targetDriver(target)
				.create();
		return OrderResponse.forBroker(orderRepository.save(order), clock.instant(), 0L);
	}

	/**
	 * 오더 수정 (BRK-010).
	 * - 모집 중: 전부 고칠 수 있다. 상차 시간이 지난 오더도 시간을 고쳐 다시 모집할 수 있다.
	 *   단, 대기 중 요청이 있으면 차주에게 보이는 조건은 못 바꾼다 (차주는 그 조건을 보고 요청했고, 한 오더에 다시 요청할 수 없다).
	 *   요청은 10분 안에 확정되거나 만료되므로 그 뒤에 고치면 된다. 거래처·청구운임은 언제나 된다
	 * - 배차 확정 뒤: 거래처·청구운임만. 조건을 바꾸려면 배차를 취소하고 다시 모집한다
	 * - 내린 오더: 못 고친다. 복사해서 새로 올린다
	 * 다른 담당자가 먼저 바꾼 오더면(@Version) 409
	 */
	public OrderResponse update(Long userId, Long orderId, CreateOrderRequest request) {
		BrokerMember member = requireMember(userId);
		Order order = findMine(member, orderId);
		if (order.getStatus() == OrderStatus.CLOSED) {
			throw new BusinessException(ErrorCode.ORDER_NOT_OPEN);
		}
		Instant now = clock.instant();
		Integer billedFare = request.billedFare() != null ? request.billedFare()
				: Order.billedFareFor(request.fare(), request.feeRate());
		Integer paymentDays = request.expectedPaymentDays() != null
				? request.expectedPaymentDays() : member.getBroker().getSettlementDays();
		long pending = pendingCounts(List.of(order), now).getOrDefault(orderId, 0L);

		if (order.getStatus() == OrderStatus.ASSIGNED || pending > 0) {
			if (!sameTermsAs(order, request, paymentDays)) {
				throw new BusinessException(order.getStatus() == OrderStatus.ASSIGNED ? ErrorCode.ORDER_LOCKED : ErrorCode.ORDER_HAS_REQUESTS);
			}
			order.changeBilling(billedFare, request.customerName());
		} else {
			Driver target = request.targetDriverId() == null ? null : requireLinkedDriver(member, request.targetDriverId());
			order.change(resolveSite(request.pickup()), resolveSite(request.dropoff()),
					request.pickupFrom(), request.pickupUntil(), request.dropoffBy(),
					request.cargoCategory(), request.cargoType(), request.weightKg(), request.tonnage(), request.bodyType(),
					request.loadingMethod(), request.fare(), billedFare, request.customerName(), request.paymentType(), paymentDays,
					request.consignmentType(), request.confirmMode(), target);
		}
		try {
			orderRepository.flush();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new BusinessException(ErrorCode.ORDER_CONFLICT);
		}
		return OrderResponse.forBroker(order, now, pending);
	}

	/** 차주에게 보이는 조건이 요청과 같은지. 상하차지는 새로 만들지 않고 같은 곳인지만 본다 */
	private static boolean sameTermsAs(Order order, CreateOrderRequest request, Integer paymentDays) {
		return order.hasSameTerms(
				siteIdIfSame(request.pickup(), order.getPickupSite()), siteIdIfSame(request.dropoff(), order.getDropoffSite()),
				request.pickupFrom(), request.pickupUntil(), request.dropoffBy(),
				request.cargoCategory(), request.cargoType(), request.weightKg(), request.tonnage(), request.bodyType(),
				request.loadingMethod(), request.fare(), request.paymentType(), paymentDays, request.consignmentType(),
				request.confirmMode(), request.targetDriverId());
	}

	/** 수정 요청의 상하차지가 지금 것과 같은 곳이면 그 id, 아니면 null. SiteService.findOrCreate와 같은 100m 기준 */
	private static Long siteIdIfSame(OrderSiteRequest request, Site current) {
		if (request.siteId() != null) {
			return request.siteId();
		}
		if (request.site() == null) {
			return null;
		}
		return current.isWithin(request.site().lat(), request.site().lng(), SAME_SITE_METERS) ? current.getId() : null;
	}

	/**
	 * 지정 배차는 우리 회사 단골 차주에게만 (BRK-002). 없는 차주와 단골 아닌 차주를 구분하지 않는다.
	 * 이미 등록된 지정 오더는 해제해도 그대로 둔다
	 */
	/** 회사 서류(사업자등록증·운송주선사업 허가증)가 모두 승인된 회사만 오더를 올린다 (07 인계서 13장). 고치기·확정·취소는 막지 않는다 */
	private void requireVerified(BrokerMember member) {
		if (reviewProperties.gateBrokerOrders() && !brokerDocumentService.isVerified(member.getBroker().getId())) {
			throw new BusinessException(ErrorCode.BROKER_NOT_VERIFIED);
		}
	}

	private Driver requireLinkedDriver(BrokerMember member, Long driverUserId) {
		if (!linkRepository.existsByBrokerIdAndDriverUserIdAndStatus(
				member.getBroker().getId(), driverUserId, DriverLinkStatus.LINKED)) {
			throw new BusinessException(ErrorCode.ORDER_TARGET_NOT_LINKED);
		}
		return driverRepository.findById(driverUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_TARGET_NOT_LINKED));
	}

	/** 우리 회사 오더. 최근 등록한 순서, page는 0부터. 오더마다 대기 중 요청 수를 붙인다 */
	@Transactional(readOnly = true)
	public OrderPageResponse getOrders(Long userId, OrderStatus status, Integer page, Integer size) {
		BrokerMember member = requireMember(userId);
		PageRequest pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
				size == null ? 20 : Math.clamp(size, 1, MAX_PAGE_SIZE));
		Long brokerId = member.getBroker().getId();
		Page<Order> orders = status == null
				? orderRepository.findAllByBrokerIdOrderByCreatedAtDesc(brokerId, pageable)
				: orderRepository.findAllByBrokerIdAndStatusOrderByCreatedAtDesc(brokerId, status, pageable);

		Instant now = clock.instant();
		Map<Long, Long> pending = pendingCounts(orders.getContent(), now);
		List<OrderResponse> items = orders.getContent().stream()
				.map(order -> OrderResponse.forBroker(order, now, pending.getOrDefault(order.getId(), 0L)))
				.toList();
		return new OrderPageResponse(items, orders.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
	}

	@Transactional(readOnly = true)
	public OrderResponse getOrder(Long userId, Long orderId) {
		Order order = findMine(requireMember(userId), orderId);
		Instant now = clock.instant();
		return OrderResponse.forBroker(order, now, pendingCounts(List.of(order), now).getOrDefault(orderId, 0L));
	}

	/** 요청한 차주 목록 (DSP-021). 먼저 요청한 순서. 이행 기록은 Ieo에서 완료한 운행 수와 늦은 취소 수 (DSP-031) */
	@Transactional(readOnly = true)
	public List<ApplicantResponse> getApplicants(Long userId, Long orderId) {
		Order order = findMine(requireMember(userId), orderId);
		Instant now = clock.instant();
		return requestRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
				.map(request -> {
					Long driverId = request.getDriver().getUserId();
					long completed = tripRepository.countByDriverUserIdAndStatus(driverId, TripStatus.COMPLETED);
					long lateCancels = cancellationRepository.findAllByCanceledById(driverId).stream()
							.filter(Cancellation::isLate)
							.count();
					return ApplicantResponse.of(request, now, completed, lateCancels);
				})
				.toList();
	}

	/** 확정 (DSP-022): 오더 배차 완료, 나머지 요청 마감, 차주 운행 생성 */
	public OrderResponse confirm(Long userId, Long orderId, Long requestId) {
		Order order = findMine(requireMember(userId), orderId);
		DispatchRequest request = requestRepository.findById(requestId)
				.filter(found -> found.getOrder().getId().equals(order.getId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.DISPATCH_REQUEST_NOT_FOUND));
		Instant now = clock.instant();
		confirmer.confirm(order, request, now);
		return OrderResponse.forBroker(order, now, 0L);
	}

	/**
	 * 확정된 배차 취소 (DSP-030). 끝나지 않은 운행을 취소하고, 오더는 다시 모집하거나(reopen 기본) 내린다.
	 * 상차 예정 2시간 이내 취소도 기록에 남는다
	 */
	public OrderResponse cancelDispatch(Long userId, Long orderId, CancelOrderRequest request) {
		BrokerMember member = requireMember(userId);
		Order order = findMine(member, orderId);
		Trip trip = tripRepository.findFirstByOrderIdAndStatusIn(orderId, TripStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_ASSIGNED));
		Instant now = clock.instant();
		canceller.cancel(trip, member.getUser(), request.reason(), request.memo(),
				request.reopen() == null || request.reopen(), now);
		return OrderResponse.forBroker(order, now, 0L);
	}

	/** 오더 내리기. 모집 중일 때만. 대기 중 요청은 마감한다 */
	public OrderResponse close(Long userId, Long orderId) {
		Order order = findMine(requireMember(userId), orderId);
		order.close();
		Instant now = clock.instant();
		confirmer.declineWaiting(orderId, null, now);
		return OrderResponse.forBroker(order, now, 0L);
	}

	private Map<Long, Long> pendingCounts(List<Order> orders, Instant now) {
		if (orders.isEmpty()) {
			return Map.of();
		}
		List<Long> orderIds = orders.stream().map(Order::getId).toList();
		return requestRepository.countPendingByOrderIds(orderIds, now.minus(DispatchRequest.TTL)).stream()
				.collect(Collectors.toMap(DispatchRequestRepository.OrderCount::getOrderId,
						DispatchRequestRepository.OrderCount::getCount));
	}

	private Site resolveSite(OrderSiteRequest request) {
		if (request.siteId() != null) {
			return siteRepository.findById(request.siteId())
					.orElseThrow(() -> new BusinessException(ErrorCode.SITE_NOT_FOUND));
		}
		if (request.site() == null) {
			throw new BusinessException(ErrorCode.ORDER_INVALID, "상하차지를 골라 주세요.");
		}
		return siteService.findOrCreate(request.site(), null).site();
	}

	/** 다른 회사 오더는 있는지도 알려 주지 않도록 404 */
	private Order findMine(BrokerMember member, Long orderId) {
		return orderRepository.findById(orderId)
				.filter(order -> order.belongsTo(member.getBroker().getId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	private BrokerMember requireMember(Long userId) {
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_BROKER));
		if (member.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		return member;
	}
}
