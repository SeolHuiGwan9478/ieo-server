package com.ieo.server.domain.dispatch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DispatchRepositoryTest {

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private DispatchRequestRepository requestRepository;

	@Autowired
	private TripRepository tripRepository;

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

	@Autowired
	private EntityManager entityManager;

	private Broker broker;
	private BrokerMember member;
	private Driver driver;
	private Driver other;
	private Vehicle vehicle;
	private Site pickup;
	private Site dropoff;

	@BeforeEach
	void setUp() {
		broker = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		member = brokerMemberRepository.save(BrokerMember.of(
				userRepository.save(User.create("01090001301", UserRole.BROKER)), broker, BrokerMemberPosition.OWNER));
		driver = driverRepository.save(Driver.of(userRepository.save(User.create("01090001302", UserRole.DRIVER))));
		other = driverRepository.save(Driver.of(userRepository.save(User.create("01090001303", UserRole.DRIVER))));
		vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.WING, null));
		pickup = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		dropoff = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
	}

	@Test
	void 차주에게는_모집_중이고_자기에게_보이는_오더만_상차_순으로_보인다() {
		Instant now = Instant.now();
		Order later = orderRepository.save(order(now.plus(Duration.ofDays(2)), null));
		Order sooner = orderRepository.save(order(now.plus(Duration.ofDays(1)), null));
		Order mine = orderRepository.save(order(now.plus(Duration.ofDays(3)), driver));
		orderRepository.save(order(now.plus(Duration.ofDays(1)), other));
		Order closed = order(now.plus(Duration.ofDays(1)), null);
		closed.close();
		orderRepository.save(closed);

		assertThat(orderRepository.findOpenFor(driver.getUserId(), now, Pageable.ofSize(20)))
				.extracting(Order::getId)
				.containsExactly(sooner.getId(), later.getId(), mine.getId());
		assertThat(orderRepository.findAllByBrokerIdOrderByCreatedAtDesc(broker.getId(), PageRequest.of(0, 2)).getTotalElements())
				.isEqualTo(5);
	}

	@Test
	void 요청과_운행을_저장하고_찾는다() {
		Order order = orderRepository.save(order(Instant.now().plus(Duration.ofDays(1)), null));
		DispatchRequest request = requestRepository.save(DispatchRequest.create(order, driver, vehicle));
		Instant since = Instant.now().minus(DispatchRequest.TTL);

		assertThat(requestRepository.countByDriverUserIdAndStatusAndCreatedAtAfter(
				driver.getUserId(), DispatchRequestStatus.PENDING, since)).isEqualTo(1);
		assertThat(requestRepository.findFirstByOrderIdAndDriverUserIdOrderByIdDesc(order.getId(), driver.getUserId())).isPresent();
		assertThat(requestRepository.findAllByOrderIdOrderByCreatedAtAsc(order.getId())).hasSize(1);

		request.confirm(Instant.now());
		order.assign(Instant.now());
		tripRepository.save(Trip.fromDispatch(request));
		entityManager.flush();
		assertThat(tripRepository.existsByOrderId(order.getId())).isTrue();
		assertThat(tripRepository.findFirstByOrderIdAndDriverUserIdOrderByCreatedAtDesc(order.getId(), driver.getUserId()))
				.isPresent();
	}

	@Test
	void 같은_오더에_대기_중_요청은_하나뿐이다() {
		Order order = orderRepository.save(order(Instant.now().plus(Duration.ofDays(1)), null));
		requestRepository.saveAndFlush(DispatchRequest.create(order, driver, vehicle));

		assertThatThrownBy(() -> requestRepository.saveAndFlush(DispatchRequest.create(order, driver, vehicle)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 다른_담당자가_먼저_바꾼_오더를_저장하면_실패한다() {
		Order saved = orderRepository.saveAndFlush(order(Instant.now().plus(Duration.ofDays(1)), null));
		entityManager.createQuery("update Order o set o.version = o.version + 1 where o.id = :id")
				.setParameter("id", saved.getId())
				.executeUpdate();

		saved.assign(Instant.now());
		assertThatThrownBy(() -> orderRepository.saveAndFlush(saved))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private Order order(Instant pickupFrom, Driver target) {
		return Order.draft()
				.createdBy(member)
				.pickupSite(pickup)
				.dropoffSite(dropoff)
				.pickupFrom(pickupFrom)
				.pickupUntil(pickupFrom.plus(Duration.ofHours(2)))
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
				.targetDriver(target)
				.create();
	}
}
