package com.ieo.server.domain.trip.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.site.entity.Site;
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

class TripTest {

	private static final Instant T0 = Instant.parse("2026-11-02T00:00:00Z");

	private Driver driver;
	private Vehicle vehicle;
	private final Site pyeongtaek = Site.create("평택 포승 물류센터", "경기 평택시 포승읍", 36.98, 126.84, null);
	private final Site daegu = Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null);

	@BeforeEach
	void setUp() {
		User user = User.create("01090000701", UserRole.DRIVER);
		ReflectionTestUtils.setField(user, "id", 1L);
		driver = Driver.of(user);
		ReflectionTestUtils.setField(driver, "userId", 1L);
		vehicle = Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null);
	}

	@Test
	void 상차지와_하차지를_가진_예약_운행으로_만든다() {
		Trip trip = newTrip();

		assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
		assertThat(trip.getStops()).hasSize(2);
		assertThat(trip.pickup().getSite()).isSameAs(pyeongtaek);
		assertThat(trip.pickup().getPlannedAt()).isEqualTo(T0);
		assertThat(trip.dropoff().getSite()).isSameAs(daegu);
		assertThat(trip.isEtaNoticeEnabled()).isTrue();
		assertThat(trip.getBrokerName()).isEqualTo("한빛로지스");
	}

	@Test
	void 출발부터_완료까지_순서대로_간다() {
		Trip trip = newTrip();

		trip.start(T0);
		trip.arriveAtPickup();
		trip.departPickup();
		trip.arriveAtDropoff();
		trip.complete(T0.plusSeconds(7 * 3600));

		assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(trip.getStartedAt()).isEqualTo(T0);
		assertThat(trip.getCompletedAt()).isEqualTo(T0.plusSeconds(7 * 3600));
	}

	@Test
	void 순서를_건너뛰면_거부한다() {
		Trip trip = newTrip();

		assertThatThrownBy(trip::arriveAtDropoff)
				.extracting("errorCode").isEqualTo(ErrorCode.TRIP_INVALID_STATUS);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
	}

	@Test
	void 완료_전에는_어디서든_취소할_수_있고_완료나_취소_뒤에는_못_한다() {
		Trip moving = newTrip();
		moving.start(T0);
		moving.arriveAtPickup();
		moving.cancel(T0.plusSeconds(60));
		assertThat(moving.getStatus()).isEqualTo(TripStatus.CANCELLED);
		assertThat(moving.getCanceledAt()).isEqualTo(T0.plusSeconds(60));
		assertThatThrownBy(() -> moving.cancel(T0))
				.extracting("errorCode").isEqualTo(ErrorCode.TRIP_INVALID_STATUS);

		Trip done = newTrip();
		done.start(T0);
		done.arriveAtPickup();
		done.departPickup();
		done.arriveAtDropoff();
		done.complete(T0);
		assertThatThrownBy(() -> done.cancel(T0))
				.extracting("errorCode").isEqualTo(ErrorCode.TRIP_INVALID_STATUS);
	}

	@Test
	void 운임이_범위를_벗어나거나_남의_차량이면_거부한다() {
		assertThatThrownBy(() -> Trip.create(driver, vehicle, null, null, -1, PaymentType.RECEIPT, TripSource.MANUAL,
				pyeongtaek, null, daegu, null))
				.extracting("errorCode").isEqualTo(ErrorCode.TRIP_INVALID_FARE);

		User other = User.create("01090000702", UserRole.DRIVER);
		ReflectionTestUtils.setField(other, "id", 2L);
		Driver otherDriver = Driver.of(other);
		ReflectionTestUtils.setField(otherDriver, "userId", 2L);
		assertThatThrownBy(() -> Trip.create(otherDriver, vehicle, null, null, 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, pyeongtaek, null, daegu, null))
				.extracting("errorCode").isEqualTo(ErrorCode.VEHICLE_NOT_FOUND);
	}


	private Trip newTrip() {
		return Trip.create(driver, vehicle, null, " 한빛로지스 ", 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				pyeongtaek, T0, daegu, null);
	}
}
