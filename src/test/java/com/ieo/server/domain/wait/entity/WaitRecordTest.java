package com.ieo.server.domain.wait.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.wait.enums.WaitRounding;
import com.ieo.server.domain.wait.enums.WaitStatus;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WaitRecordTest {

	/** 무료 2시간, 30분당 1만 원, 상한 5만 원, 올림 */
	private final WaitPolicy policy = WaitPolicy.create(
			Broker.create("한빛로지스", "1234567891", "제2026-001호", null), 120, 30, 10_000, 50_000, WaitRounding.UP);

	private Driver driver;
	private Vehicle vehicle;
	private final Site site = Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null);

	@BeforeEach
	void setUp() {
		User user = User.create("01090000901", UserRole.DRIVER);
		ReflectionTestUtils.setField(user, "id", 1L);
		driver = Driver.of(user);
		ReflectionTestUtils.setField(driver, "userId", 1L);
		vehicle = Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null);
	}

	@Test
	void 일찍_와도_대기비는_예정_시각부터_센다() {
		WaitRecord record = WaitRecord.start(pickup("10:00"), at("07:00"), policy);
		record.startWork(at("12:30"));
		record.depart(at("13:10"));

		assertThat(record.status()).isEqualTo(WaitStatus.DONE);
		assertThat(record.chargeableFrom()).isEqualTo(at("10:00"));
		assertThat(record.waitMinutes()).isEqualTo(330);
		assertThat(record.chargeableWaitMinutes()).isEqualTo(150);
		assertThat(record.workMinutes()).isEqualTo(40);
		assertThat(record.stayMinutes()).isEqualTo(370);
		assertThat(record.chargeableAmount()).isEqualTo(10_000);
		assertThat(record.isEdited()).isFalse();
	}

	@Test
	void 늦게_오면_도착_시각부터_센다() {
		WaitRecord record = WaitRecord.start(pickup("10:00"), at("11:00"), policy);
		record.startWork(at("12:30"));

		assertThat(record.chargeableFrom()).isEqualTo(at("11:00"));
		assertThat(record.chargeableWaitMinutes()).isEqualTo(90);
		assertThat(record.chargeableAmount()).isZero();
	}

	@Test
	void 예정_시각이_없으면_도착_시각부터_센다() {
		WaitRecord record = WaitRecord.start(pickup(null), at("09:00"), policy);
		record.startWork(at("12:30"));

		assertThat(record.chargeableWaitMinutes()).isEqualTo(210);
		assertThat(record.chargeableAmount()).isEqualTo(30_000);
	}

	@Test
	void 작업_시작_없이_떠나면_체류_시간만_남고_대기비는_없다() {
		WaitRecord record = WaitRecord.start(pickup("10:00"), at("09:00"), policy);
		assertThat(record.status()).isEqualTo(WaitStatus.WAITING);

		record.depart(at("13:00"));

		assertThat(record.status()).isEqualTo(WaitStatus.DONE);
		assertThat(record.stayMinutes()).isEqualTo(240);
		assertThat(record.waitMinutes()).isNull();
		assertThat(record.chargeableAmount()).isNull();
	}

	@Test
	void 정책이_없으면_시간만_기록한다() {
		WaitRecord record = WaitRecord.start(pickup(null), at("09:00"), null);
		record.startWork(at("12:30"));

		assertThat(record.chargeableWaitMinutes()).isEqualTo(210);
		assertThat(record.chargeableAmount()).isNull();
	}

	@Test
	void 작업_시작을_나중에_넣어도_고친_기록으로_남는다() {
		WaitRecord record = WaitRecord.start(pickup(null), at("09:00"), policy);
		record.depart(at("13:10"));

		record.edit(at("09:00"), at("12:30"), at("13:10"), at("13:20"));
		record.edit(at("09:00"), at("12:40"), at("13:10"), at("13:30"));

		assertThat(record.isEdited()).isTrue();
		assertThat(record.getEditedAt()).isEqualTo(at("13:30"));
		assertThat(record.getOriginalWorkStartedAt()).isNull();
		assertThat(record.getOriginalArrivedAt()).isEqualTo(at("09:00"));
		assertThat(record.getOriginalDepartedAt()).isEqualTo(at("13:10"));
		assertThat(record.getWorkStartedAt()).isEqualTo(at("12:40"));
		assertThat(record.chargeableAmount()).isEqualTo(40_000);
	}

	@Test
	void 시각_순서가_어긋나거나_상태에_맞지_않으면_거부한다() {
		WaitRecord record = WaitRecord.start(pickup(null), at("09:00"), policy);

		assertThatThrownBy(() -> record.startWork(at("08:00")))
				.extracting("errorCode").isEqualTo(ErrorCode.WAIT_INVALID_TIME);
		assertThatThrownBy(() -> record.edit(at("09:00"), at("13:00"), at("12:00"), at("14:00")))
				.extracting("errorCode").isEqualTo(ErrorCode.WAIT_INVALID_TIME);

		record.startWork(at("10:00"));
		assertThatThrownBy(() -> record.startWork(at("10:30")))
				.extracting("errorCode").isEqualTo(ErrorCode.WAIT_INVALID_STATUS);
		record.depart(at("11:00"));
		assertThatThrownBy(() -> record.depart(at("11:30")))
				.extracting("errorCode").isEqualTo(ErrorCode.WAIT_INVALID_STATUS);
	}

	private TripStop pickup(String plannedAt) {
		Trip trip = Trip.create(driver, vehicle, null, null, 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				site, plannedAt == null ? null : at(plannedAt), site, null);
		return trip.pickup();
	}

	private static Instant at(String time) {
		return Instant.parse("2026-11-03T" + time + ":00+09:00");
	}
}
