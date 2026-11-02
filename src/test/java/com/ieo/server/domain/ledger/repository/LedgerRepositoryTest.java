package com.ieo.server.domain.ledger.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.entity.Expense;
import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.ledger.enums.ExpenseCategory;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerRepositoryTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

	@Autowired
	private LedgerEntryRepository entryRepository;

	@Autowired
	private ExtraIncomeRepository extraIncomeRepository;

	@Autowired
	private ExpenseRepository expenseRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private WaitRecordRepository waitRecordRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private SiteRepository siteRepository;

	private Driver driver;
	private Trip trip;

	@BeforeEach
	void setUp() {
		driver = driverRepository.save(Driver.of(userRepository.save(User.create("01090001601", UserRole.DRIVER))));
		Vehicle vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));
		Site site = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		trip = tripRepository.save(Trip.create(driver, vehicle, null, "한빛로지스", 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, site, null, site, null));
	}

	@Test
	void 기간_장부와_미수금과_부가_수입과_경비를_찾는다() {
		LedgerEntry auto = entryRepository.save(LedgerEntry.fromTrip(trip, DAY, 15));
		LedgerEntry paid = LedgerEntry.manual(driver, DAY.minusDays(1), "전화로 받은 일", 300_000, PaymentType.COLLECT, null, null);
		paid.markPaid(Instant.now());
		entryRepository.save(paid);
		entryRepository.save(LedgerEntry.manual(driver, DAY.minusDays(40), "지난달 일", 200_000, PaymentType.PREPAID, null, null));
		WaitRecord wait = waitRecordRepository.save(WaitRecord.start(trip.pickup(), Instant.now(), null));
		extraIncomeRepository.save(ExtraIncome.waitFee(auto, wait, 10_000));
		expenseRepository.save(Expense.create(driver, trip, DAY, ExpenseCategory.FUEL, 150_000, null));
		expenseRepository.save(Expense.create(driver, null, DAY.minusDays(1), ExpenseCategory.MEAL, 9_000, null));

		Long userId = driver.getUserId();
		assertThat(entryRepository.findAllByDriverUserIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(userId, DAY.minusDays(7), DAY))
				.extracting(LedgerEntry::getDescription)
				.containsExactly("평택 포승 물류센터 → 평택 포승 물류센터", "전화로 받은 일");
		assertThat(entryRepository.findAllByDriverUserIdAndPaidAtIsNullOrderByExpectedPaymentDateAsc(userId))
				.extracting(LedgerEntry::getDescription)
				.containsExactly("지난달 일", "평택 포승 물류센터 → 평택 포승 물류센터");
		assertThat(entryRepository.findByTripId(trip.getId())).isPresent();
		assertThat(extraIncomeRepository.findAllByLedgerEntryIdIn(List.of(auto.getId()))).singleElement()
				.extracting(ExtraIncome::getAmount).isEqualTo(10_000);
		assertThat(extraIncomeRepository.existsByWaitRecordId(wait.getId())).isTrue();
		assertThat(expenseRepository.findAllByDriverUserIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(userId, DAY, DAY))
				.singleElement().extracting(Expense::getAmount).isEqualTo(150_000);
		assertThat(expenseRepository.findAllByTripId(trip.getId())).hasSize(1);
	}

	@Test
	void 운행_하나에_장부는_한_줄뿐이다() {
		entryRepository.saveAndFlush(LedgerEntry.fromTrip(trip, DAY, 15));

		assertThatThrownBy(() -> entryRepository.saveAndFlush(LedgerEntry.fromTrip(trip, DAY, 15)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
