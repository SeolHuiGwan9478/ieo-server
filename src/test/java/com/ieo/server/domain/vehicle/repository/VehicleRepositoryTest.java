package com.ieo.server.domain.vehicle.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.math.BigDecimal;
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
class VehicleRepositoryTest {

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DriverRepository driverRepository;

	private Driver driver;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.create("01090000401", UserRole.DRIVER));
		driver = driverRepository.save(Driver.of(user));
	}

	@Test
	void 내_차량_목록과_기본_차량을_찾는다() {
		Vehicle first = Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, new BigDecimal("3.60"));
		first.markDefault();
		first.linkPleos("vin-hash");
		vehicleRepository.save(first);
		vehicleRepository.save(Vehicle.create(driver, "123바4567", Tonnage.TON_11, BodyType.WING, null));
		Long userId = driver.getUserId();

		assertThat(vehicleRepository.findAllByDriverUserIdOrderByCreatedAtAsc(userId))
				.extracting(Vehicle::getPlateNumber)
				.containsExactly("경기82사1234", "123바4567");
		assertThat(vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId))
				.get().extracting(Vehicle::getPlateNumber).isEqualTo("경기82사1234");
		assertThat(vehicleRepository.countByDriverUserId(userId)).isEqualTo(2);
		assertThat(vehicleRepository.existsByPlateNumber("123바4567")).isTrue();
		assertThat(vehicleRepository.findByVinHash("vin-hash")).isPresent();
	}

	@Test
	void 같은_차량_번호는_두_번_등록할_수_없다() {
		vehicleRepository.saveAndFlush(Vehicle.create(driver, "경기82사1234", Tonnage.TON_5, BodyType.CARGO, null));

		assertThatThrownBy(() -> vehicleRepository.saveAndFlush(
				Vehicle.create(driver, "경기 82사 1234", Tonnage.TON_5, BodyType.CARGO, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
