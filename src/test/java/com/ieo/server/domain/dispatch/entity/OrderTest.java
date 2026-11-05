package com.ieo.server.domain.dispatch.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConfirmMode;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.DispatchRequestStatus;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderTest {

	private static final Instant NOW = Instant.parse("2026-11-03T05:00:00Z");
	private static final Instant PICKUP_FROM = Instant.parse("2026-11-04T00:00:00Z");

	private BrokerMember member;
	private Driver driver;
	private Vehicle wing5;
	private final Site pyeongtaek = Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null);
	private final Site daegu = Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null);

	@BeforeEach
	void setUp() {
		Broker broker = Broker.create("한빛로지스", "1234567891", "제2026-001호", null);
		ReflectionTestUtils.setField(broker, "id", 10L);
		member = BrokerMember.of(User.create("01090001201", UserRole.BROKER), broker, BrokerMemberPosition.STAFF);
		driver = driver(1L);
		wing5 = Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.WING, null);
	}

	@Test
	void 필수_항목을_갖춘_오더는_모집_중으로_등록되고_기본은_선택_확정이다() {
		Order order = draft().create();

		assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
		assertThat(order.getConfirmMode()).isEqualTo(ConfirmMode.SELECT);
		assertThat(order.getBroker().getName()).isEqualTo("한빛로지스");
		assertThat(order.getCargoCategory()).isEqualTo(CargoCategory.GENERAL);
		assertThat(order.getCargoType()).isEqualTo("음료 파레트");
		assertThat(order.isOpenAt(NOW)).isTrue();
		assertThat(order.isOpenAt(PICKUP_FROM.plusSeconds(2 * 3600))).isFalse();
		assertThat(order.belongsTo(10L)).isTrue();
	}

	@Test
	void 필수_항목이_빠지거나_시간_순서가_틀리면_등록되지_않는다() {
		assertThatThrownBy(() -> draft().cargoType(" ").create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);
		assertThatThrownBy(() -> draft().cargoCategory(null).create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);
		assertThatThrownBy(() -> draft().loadingMethod(null).create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);
		assertThatThrownBy(() -> draft().pickupUntil(PICKUP_FROM).create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);
		assertThatThrownBy(() -> draft().dropoffBy(PICKUP_FROM.minusSeconds(60)).create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);
		assertThatThrownBy(() -> draft().weightKg(0).create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);
		assertThatThrownBy(() -> draft().expectedPaymentDays(null).create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);
	}

	@Test
	void 차종이_같고_톤수가_오더_이상이며_모집_중이면_요청할_수_있다() {
		Order order = draft().create();
		order.checkRequestable(driver, wing5, NOW);

		Vehicle wing11 = Vehicle.create(driver, "경기82사5678", Tonnage.TON_11, BodyType.WING, null);
		order.checkRequestable(driver, wing11, NOW);

		Vehicle cargo5 = Vehicle.create(driver, "123바4567", Tonnage.TON_5, BodyType.CARGO, null);
		assertThatThrownBy(() -> order.checkRequestable(driver, cargo5, NOW))
				.extracting("errorCode").isEqualTo(ErrorCode.DISPATCH_VEHICLE_MISMATCH);
		Vehicle wing2 = Vehicle.create(driver, "123바0000", Tonnage.TON_2_5, BodyType.WING, null);
		assertThatThrownBy(() -> order.checkRequestable(driver, wing2, NOW))
				.extracting("errorCode").isEqualTo(ErrorCode.DISPATCH_VEHICLE_MISMATCH);
		assertThatThrownBy(() -> order.checkRequestable(driver, wing5, PICKUP_FROM.plusSeconds(3 * 3600)))
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_OPEN);
	}

	@Test
	void 지정_배차_오더는_그_차주만_요청할_수_있다() {
		Order order = draft().targetDriver(driver).create();
		Driver other = driver(2L);
		Vehicle otherWing = Vehicle.create(other, "경기82사9999", Tonnage.TON_5, BodyType.WING, null);

		order.checkRequestable(driver, wing5, NOW);
		assertThatThrownBy(() -> order.checkRequestable(other, otherWing, NOW))
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
	}

	@Test
	void 확정하면_배차_완료가_되고_다시_확정하거나_내릴_수_없다() {
		Order order = draft().create();
		order.assign(NOW);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
		assertThatThrownBy(() -> order.assign(NOW)).extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_OPEN);
		assertThatThrownBy(order::close).extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_OPEN);
	}

	@Test
	void 요청은_10분이_지나면_만료되고_만료된_요청은_확정할_수_없다() {
		DispatchRequest request = DispatchRequest.create(draft().create(), driver, wing5);
		ReflectionTestUtils.setField(request, "createdAt", NOW);

		assertThat(request.statusAt(NOW.plusSeconds(9 * 60))).isEqualTo(DispatchRequestStatus.PENDING);
		assertThat(request.statusAt(NOW.plusSeconds(10 * 60))).isEqualTo(DispatchRequestStatus.EXPIRED);
		assertThatThrownBy(() -> request.confirm(NOW.plusSeconds(10 * 60)))
				.extracting("errorCode").isEqualTo(ErrorCode.DISPATCH_REQUEST_NOT_PENDING);

		request.confirm(NOW.plusSeconds(5 * 60));
		assertThat(request.getStatus()).isEqualTo(DispatchRequestStatus.CONFIRMED);
		request.decline();
		assertThat(request.getStatus()).isEqualTo(DispatchRequestStatus.CONFIRMED);
	}

	@Test
	void 확정된_요청으로_오더_내용을_옮긴_예약_운행을_만든다() {
		Order order = draft().create();
		DispatchRequest request = DispatchRequest.create(order, driver, wing5);

		Trip trip = Trip.fromDispatch(request);

		assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
		assertThat(trip.getSource()).isEqualTo(TripSource.IEO_DISPATCH);
		assertThat(trip.getOrder()).isSameAs(order);
		assertThat(trip.getBroker()).isSameAs(order.getBroker());
		assertThat(trip.getFare()).isEqualTo(450_000);
		assertThat(trip.getVehicle()).isSameAs(wing5);
		assertThat(trip.pickup().getSite()).isSameAs(pyeongtaek);
		assertThat(trip.pickup().getPlannedAt()).isEqualTo(PICKUP_FROM);
		assertThat(trip.dropoff().getPlannedAt()).isEqualTo(order.getDropoffBy());
	}

	@Test
	void 냉동_냉장_화물은_냉동_냉장_차로만_올릴_수_있다() {
		assertThatThrownBy(() -> draft().cargoCategory(CargoCategory.COLD).bodyType(BodyType.WING).create())
				.extracting("errorCode").isEqualTo(ErrorCode.ORDER_INVALID);

		Order cold = draft().cargoCategory(CargoCategory.COLD).bodyType(BodyType.REFRIGERATED).create();
		Order generalInFridge = draft().bodyType(BodyType.REFRIGERATED).create();

		assertThat(cold.getCargoCategory()).isEqualTo(CargoCategory.COLD);
		assertThat(generalInFridge.getCargoCategory()).isEqualTo(CargoCategory.GENERAL);
	}

	private Order.OrderBuilder draft() {
		return Order.draft()
				.createdBy(member)
				.pickupSite(pyeongtaek)
				.dropoffSite(daegu)
				.pickupFrom(PICKUP_FROM)
				.pickupUntil(PICKUP_FROM.plusSeconds(2 * 3600))
				.dropoffBy(PICKUP_FROM.plusSeconds(8 * 3600))
				.cargoCategory(CargoCategory.GENERAL)
				.cargoType(" 음료 파레트 ")
				.weightKg(4500)
				.tonnage(Tonnage.TON_5)
				.bodyType(BodyType.WING)
				.loadingMethod(LoadingMethod.FORKLIFT)
				.fare(450_000)
				.paymentType(PaymentType.RECEIPT)
				.expectedPaymentDays(15)
				.consignmentType(ConsignmentType.DIRECT);
	}

	private static Driver driver(Long id) {
		User user = User.create("0109000120" + id, UserRole.DRIVER);
		ReflectionTestUtils.setField(user, "id", id);
		Driver driver = Driver.of(user);
		ReflectionTestUtils.setField(driver, "userId", id);
		return driver;
	}
}
