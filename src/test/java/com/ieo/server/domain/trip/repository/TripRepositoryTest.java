package com.ieo.server.domain.trip.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.StopType;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-11-02T00:00:00Z");

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripStopRepository tripStopRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private SiteRepository siteRepository;

	@Autowired
	private EntityManager entityManager;

	private Driver driver;
	private Vehicle vehicle;
	private Site pickup;
	private Site dropoff;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.create("01090000711", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
		vehicle = vehicleRepository.save(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));
		pickup = siteRepository.save(Site.create("평택 포승 물류센터", "경기 평택시", 36.98, 126.84, null));
		dropoff = siteRepository.save(Site.create("대구 성서 공단", "대구 달서구", 35.84, 128.49, null));
	}

	@Test
	void 운행을_저장하면_상하차지도_같이_저장된다() {
		Trip trip = tripRepository.save(newTrip());
		entityManager.flush();
		entityManager.clear();

		Trip found = tripRepository.findById(trip.getId()).orElseThrow();
		assertThat(found.getStops()).hasSize(2);
		assertThat(found.pickup().getSite().getName()).isEqualTo("평택 포승 물류센터");
		assertThat(tripStopRepository.findByTripIdAndType(trip.getId(), StopType.DROPOFF))
				.get().extracting(stop -> stop.getSite().getName()).isEqualTo("대구 성서 공단");
	}

	@Test
	void 진행_중인_운행과_차량_사용_여부를_찾는다() {
		Trip scheduled = tripRepository.save(newTrip());
		Trip moving = newTrip();
		moving.start(NOW);
		tripRepository.save(moving);
		Long userId = driver.getUserId();

		assertThat(tripRepository.findFirstByDriverUserIdAndStatusInOrderByCreatedAtDesc(userId, TripStatus.IN_PROGRESS))
				.get().extracting(Trip::getId).isEqualTo(moving.getId());
		assertThat(tripRepository.existsByVehicleIdAndStatusIn(vehicle.getId(), TripStatus.ACTIVE)).isTrue();
		assertThat(tripRepository.existsByVehicleId(vehicle.getId())).isTrue();
		assertThat(tripRepository.findAllByDriverUserIdOrderByCreatedAtDesc(userId, Pageable.ofSize(10)))
				.extracting(Trip::getId)
				.containsExactlyInAnyOrder(scheduled.getId(), moving.getId());

		moving.cancel(NOW);
		tripRepository.flush();
		assertThat(tripRepository.existsByDriverUserIdAndStatusIn(userId, TripStatus.IN_PROGRESS)).isFalse();
	}

	@Test
	void 운행_하나에_같은_종류의_상하차지는_하나뿐이다() {
		Trip trip = tripRepository.saveAndFlush(newTrip());

		assertThatThrownBy(() -> {
			entityManager.createNativeQuery(
					"insert into trip_stops (trip_id, stop_type, site_id, created_at, updated_at) values (?, 'PICKUP', ?, now(), now())")
					.setParameter(1, trip.getId())
					.setParameter(2, pickup.getId())
					.executeUpdate();
		}).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
	}

	private Trip newTrip() {
		return Trip.create(driver, vehicle, null, null, 450_000, PaymentType.RECEIPT, TripSource.MANUAL,
				pickup, null, dropoff, null);
	}
}
