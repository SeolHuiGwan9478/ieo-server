package com.ieo.server.domain.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.dispatch.enums.OrderCloseReason;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.dispatch.repository.DispatchRequestRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 상차 시간이 지난 모집 중 오더 자동 마감. 스케줄러는 테스트 설정에서 꺼져 있어 직접 부른다 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderExpirerTest {

	@Autowired
	private OrderExpirer expirer;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private DispatchRequestRepository requestRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private SiteRepository siteRepository;

	@Autowired
	private BrokerRepository brokerRepository;

	@Autowired
	private BrokerMemberRepository brokerMemberRepository;

	private BrokerMember member;
	private Driver driver;
	private Vehicle vehicle;
	private Site pickup;
	private Site dropoff;

	@BeforeEach
	void setUp() {
		Broker broker = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		member = brokerMemberRepository.save(BrokerMember.of(
				userRepository.save(User.create("01090001401", UserRole.BROKER)), broker, BrokerMemberPosition.OWNER));
		driver = driverRepository.save(Driver.of(userRepository.save(User.create("01090001402", UserRole.DRIVER))));
		vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.WING, null));
		pickup = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		dropoff = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
	}

	@Test
	void 상차_시간이_지난_모집_중_오더만_마감한다() {
		Instant now = Instant.now();
		Order overdue = orderRepository.save(order(now.minus(Duration.ofHours(3))));
		Order stillOpen = orderRepository.save(order(now.plus(Duration.ofHours(1))));
		Order assigned = order(now.minus(Duration.ofHours(3)));
		Order closedByBroker = order(now.minus(Duration.ofHours(3)));
		closedByBroker.close();
		orderRepository.save(closedByBroker);
		assignedRequestFor(assigned, now);

		int count = expirer.expireOverdue();

		assertThat(count).isEqualTo(1);
		Order expired = orderRepository.findById(overdue.getId()).orElseThrow();
		assertThat(expired.getStatus()).isEqualTo(OrderStatus.CLOSED);
		assertThat(expired.getCloseReason()).isEqualTo(OrderCloseReason.EXPIRED);
		assertThat(expired.isOpenAt(now)).isFalse();
		assertThat(orderRepository.findById(stillOpen.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.OPEN);
		assertThat(orderRepository.findById(assigned.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.ASSIGNED);
		assertThat(orderRepository.findById(closedByBroker.getId()).orElseThrow().getCloseReason())
				.isEqualTo(OrderCloseReason.BROKER);
	}

	@Test
	void 마감한_오더의_대기_중_요청은_마감됨이_되고_두_번_돌려도_다시_세지_않는다() {
		Instant now = Instant.now();
		Order overdue = orderRepository.save(order(now.minus(Duration.ofMinutes(5))));
		DispatchRequest waiting = requestRepository.save(DispatchRequest.create(overdue, driver, vehicle));

		assertThat(expirer.expireOverdue()).isEqualTo(1);
		assertThat(requestRepository.findById(waiting.getId()).orElseThrow().getStatus())
				.isEqualTo(DispatchRequestStatus.DECLINED);
		assertThat(expirer.expireOverdue()).isZero();
	}

	/** 배차 확정까지 간 오더. 상차 시간이 지나도 자동 마감 대상이 아니다 */
	private void assignedRequestFor(Order order, Instant now) {
		orderRepository.save(order);
		DispatchRequest request = requestRepository.save(DispatchRequest.create(order, driver, vehicle));
		request.confirm(now.minus(Duration.ofHours(4)));
		order.assign(now.minus(Duration.ofHours(4)));
	}

	private Order order(Instant pickupUntil) {
		Instant pickupFrom = pickupUntil.minus(Duration.ofHours(2));
		return Order.draft()
				.createdBy(member)
				.pickupSite(pickup)
				.dropoffSite(dropoff)
				.pickupFrom(pickupFrom)
				.pickupUntil(pickupUntil)
				.dropoffBy(pickupFrom.plus(Duration.ofHours(8)))
				.cargoCategory(CargoCategory.GENERAL)
				.cargoType("음료 파레트")
				.weightKg(4500)
				.tonnage(Tonnage.TON_5)
				.bodyType(BodyType.WING)
				.loadingMethod(LoadingMethod.FORKLIFT)
				.fare(450_000)
				.paymentType(PaymentType.RECEIPT)
				.expectedPaymentDays(15)
				.consignmentType(ConsignmentType.DIRECT)
				.create();
	}
}
