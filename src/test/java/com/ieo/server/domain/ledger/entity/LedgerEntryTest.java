package com.ieo.server.domain.ledger.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.ledger.enums.ExpenseCategory;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.global.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LedgerEntryTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

	private Driver driver;
	private Vehicle vehicle;
	private final Site pyeongtaek = Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null);
	private final Site daegu = Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null);
	private final Broker broker = Broker.create("한빛로지스", "1234567890", "제2026-001호", null);

	@BeforeEach
	void setUp() {
		User user = User.create("01090001501", UserRole.DRIVER);
		ReflectionTestUtils.setField(user, "id", 1L);
		driver = Driver.of(user);
		ReflectionTestUtils.setField(driver, "userId", 1L);
		vehicle = Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null);
	}

	@Test
	void 끝난_운행에서_운임과_상하차지를_복사해_장부_한_줄을_만든다() {
		Trip trip = trip(broker, null, PaymentType.RECEIPT);

		LedgerEntry entry = LedgerEntry.fromTrip(trip, DAY, 15);

		assertThat(entry.getTrip()).isSameAs(trip);
		assertThat(entry.getDescription()).isEqualTo("평택 포승 물류센터 → 대구 성서 공단");
		assertThat(entry.getFare()).isEqualTo(450_000);
		assertThat(entry.getBroker()).isSameAs(broker);
		assertThat(entry.getBrokerName()).isNull();
		assertThat(entry.brokerDisplayName()).isEqualTo("한빛로지스");
		assertThat(entry.getExpectedPaymentDate()).isEqualTo(DAY.plusDays(15));
		assertThat(entry.isPaid()).isFalse();
	}

	@Test
	void 지급_예정일은_선불_착불이면_운행일이고_인수증이면_정산_일수를_더한다() {
		assertThat(LedgerEntry.expectedPaymentDate(PaymentType.PREPAID, DAY, 30)).isEqualTo(DAY);
		assertThat(LedgerEntry.expectedPaymentDate(PaymentType.COLLECT, DAY, 30)).isEqualTo(DAY);
		assertThat(LedgerEntry.expectedPaymentDate(PaymentType.RECEIPT, DAY, 30)).isEqualTo(DAY.plusDays(30));

		LedgerEntry fromNamed = LedgerEntry.fromTrip(trip(null, "동네물류", PaymentType.COLLECT), DAY, 30);
		assertThat(fromNamed.brokerDisplayName()).isEqualTo("동네물류");
		assertThat(fromNamed.getExpectedPaymentDate()).isEqualTo(DAY);
	}

	@Test
	void 직접_추가한_줄은_지급_예정일을_비우면_30일_뒤로_잡는다() {
		LedgerEntry entry = LedgerEntry.manual(driver, DAY, " 전화로 받은 일 ", 300_000, PaymentType.RECEIPT, " 동네물류 ", null);

		assertThat(entry.getTrip()).isNull();
		assertThat(entry.getDescription()).isEqualTo("전화로 받은 일");
		assertThat(entry.getBrokerName()).isEqualTo("동네물류");
		assertThat(entry.getExpectedPaymentDate()).isEqualTo(DAY.plusDays(30));
		assertThatThrownBy(() -> LedgerEntry.manual(driver, DAY, " ", 1, PaymentType.PREPAID, null, null))
				.extracting("errorCode").isEqualTo(ErrorCode.LEDGER_INVALID);
		assertThatThrownBy(() -> LedgerEntry.manual(driver, DAY, "a", -1, PaymentType.PREPAID, null, null))
				.extracting("errorCode").isEqualTo(ErrorCode.LEDGER_INVALID);
	}

	@Test
	void 지급_예정일이_지나도_받지_못하면_미수_경고다() {
		LedgerEntry entry = LedgerEntry.fromTrip(trip(broker, null, PaymentType.RECEIPT), DAY, 10);

		assertThat(entry.isOverdue(DAY.plusDays(10))).isFalse();
		assertThat(entry.isOverdue(DAY.plusDays(11))).isTrue();
		entry.markPaid(Instant.parse("2026-11-20T00:00:00Z"));
		assertThat(entry.isOverdue(DAY.plusDays(11))).isFalse();
		entry.unmarkPaid();
		assertThat(entry.isPaid()).isFalse();
	}

	@Test
	void 운임과_결제_방식을_고칠_수_있다() {
		LedgerEntry entry = LedgerEntry.fromTrip(trip(broker, null, PaymentType.RECEIPT), DAY, 10);
		entry.edit(470_000, PaymentType.COLLECT, DAY);
		entry.changeDistance(312);

		assertThat(entry.getFare()).isEqualTo(470_000);
		assertThat(entry.getPaymentType()).isEqualTo(PaymentType.COLLECT);
		assertThat(entry.getDistanceKm()).isEqualTo(312);
		assertThatThrownBy(() -> entry.changeDistance(-1))
				.extracting("errorCode").isEqualTo(ErrorCode.LEDGER_INVALID);
	}

	@Test
	void 경비는_1원에서_1천만_원까지_받는다() {
		Expense expense = Expense.create(driver, null, DAY, ExpenseCategory.FUEL, 150_000, " 영동고속도로 주유소 ");

		assertThat(expense.getMemo()).isEqualTo("영동고속도로 주유소");
		assertThat(expense.getCategory().getLabel()).isEqualTo("주유");
		assertThatThrownBy(() -> Expense.create(driver, null, DAY, ExpenseCategory.FUEL, 0, null))
				.extracting("errorCode").isEqualTo(ErrorCode.LEDGER_INVALID);
		assertThatThrownBy(() -> Expense.create(driver, null, DAY, null, 1000, null))
				.extracting("errorCode").isEqualTo(ErrorCode.LEDGER_INVALID);
	}

	private Trip trip(Broker tripBroker, String brokerName, PaymentType paymentType) {
		return Trip.create(driver, vehicle, tripBroker, brokerName, 450_000, paymentType, TripSource.MANUAL,
				pyeongtaek, null, daegu, null);
	}
}
