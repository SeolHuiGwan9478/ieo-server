package com.ieo.server.domain.wait.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
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
import com.ieo.server.domain.wait.entity.WaitPolicy;
import com.ieo.server.domain.wait.entity.WaitProof;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.enums.WaitRounding;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
class WaitRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

	@Autowired
	private WaitRecordRepository recordRepository;

	@Autowired
	private WaitPolicyRepository policyRepository;

	@Autowired
	private WaitProofRepository proofRepository;

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
	private TripRepository tripRepository;

	@Autowired
	private EntityManager entityManager;

	private Broker broker;
	private Trip trip;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.create("01090000911", UserRole.DRIVER));
		Driver driver = driverRepository.save(Driver.of(user));
		Vehicle vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));
		Site site = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		broker = brokerRepository.save(Broker.create("한빛로지스", "1234567891", "제2026-001호", null));
		trip = tripRepository.save(Trip.create(driver, vehicle, broker, null, 450_000, PaymentType.RECEIPT,
				TripSource.MANUAL, site, NOW, site, null));
	}

	@Test
	void 주선사의_켜진_정책만_찾는다() {
		WaitPolicy old = WaitPolicy.create(broker, 60, 30, 5_000, 30_000, WaitRounding.UP);
		old.deactivate();
		policyRepository.save(old);
		WaitPolicy current = policyRepository.save(WaitPolicy.create(broker, 120, 30, 10_000, 50_000, WaitRounding.UP));

		assertThat(policyRepository.findFirstByBrokerIdAndActiveTrue(broker.getId()))
				.get().extracting(WaitPolicy::getId).isEqualTo(current.getId());
		assertThat(policyRepository.findAllByBrokerIdOrderByCreatedAtDesc(broker.getId())).hasSize(2);
	}

	@Test
	void 대기_기록과_증빙을_저장하고_찾는다() {
		WaitPolicy policy = policyRepository.save(WaitPolicy.create(broker, 120, 30, 10_000, 50_000, WaitRounding.UP));
		WaitRecord record = WaitRecord.start(trip.pickup(), NOW, policy);
		record.startWork(NOW.plusSeconds(150 * 60));
		recordRepository.save(record);
		WaitProof proof = proofRepository.save(WaitProof.create(record, null));
		entityManager.flush();
		entityManager.clear();

		WaitRecord found = recordRepository.findByTripStopId(trip.pickup().getId()).orElseThrow();
		assertThat(found.chargeableAmount()).isEqualTo(10_000);
		assertThat(recordRepository.existsByTripStopId(trip.dropoff().getId())).isFalse();
		assertThat(recordRepository.findAllByTripStopTripId(trip.getId())).hasSize(1);
		assertThat(proof.getShareToken()).hasSize(43);
		assertThat(proofRepository.findByShareToken(proof.getShareToken()))
				.get().extracting(p -> p.getWaitRecord().getId()).isEqualTo(found.getId());
		assertThat(proofRepository.findByWaitRecordId(found.getId())).isPresent();
	}

	@Test
	void 상하차지_방문_하나에_대기_기록은_하나뿐이다() {
		recordRepository.saveAndFlush(WaitRecord.start(trip.pickup(), NOW, null));

		assertThatThrownBy(() -> recordRepository.saveAndFlush(WaitRecord.start(trip.pickup(), NOW, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
