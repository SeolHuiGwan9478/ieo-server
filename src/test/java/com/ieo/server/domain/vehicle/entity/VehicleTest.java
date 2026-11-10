package com.ieo.server.domain.vehicle.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.FuelEconomySource;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VehicleTest {

	private final Driver driver = Driver.of(User.create("01012345678", UserRole.DRIVER));
	private final Instant now = Instant.parse("2026-11-02T01:00:00Z");

	@ParameterizedTest
	@ValueSource(strings = {"경기82사1234", "경기 82사 1234", "123바4567", "12가3456", "서울-80-바-1234"})
	void 차량_번호는_띄어쓰기와_하이픈을_지우고_저장한다(String input) {
		Vehicle vehicle = Vehicle.create(driver, input, Tonnage.TON_5, BodyType.CARGO, null);

		assertThat(vehicle.getPlateNumber()).doesNotContain(" ", "-").matches(".*\\d{4}$");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "1234", "경기82사123", "ABC1234", "뉴욕82사1234", "82사12345", "12각3456", "12김3456", "경기123가4567", "1가3456"})
	void 형식이_틀린_차량_번호는_막는다(String input) {
		assertError(() -> Vehicle.create(driver, input, Tonnage.TON_5, BodyType.CARGO, null), ErrorCode.VEHICLE_INVALID_PLATE);
	}

	@Test
	void 높이는_비워_두거나_1_5에서_4_2m_사이() {
		assertThat(Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.WING, null).getHeightM()).isNull();
		assertThat(Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.WING, new BigDecimal("3.6")).getHeightM())
				.isEqualByComparingTo("3.60");

		assertError(() -> Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.WING, new BigDecimal("4.21")),
				ErrorCode.VEHICLE_INVALID_HEIGHT);
		assertError(() -> Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.WING, new BigDecimal("1.49")),
				ErrorCode.VEHICLE_INVALID_HEIGHT);
	}

	@Test
	void Pleos_연비_L_100km를_km_L로_바꿔_저장한다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.CARGO, null);

		vehicle.updateFuelEconomy(26.3f, now);

		assertThat(vehicle.getMeasuredKmPerLiter()).isEqualByComparingTo("3.80");
		assertThat(vehicle.getFuelEconomyUpdatedAt()).isEqualTo(now);
	}

	@Test
	void 집계_전이거나_말이_안_되는_연비는_무시한다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.updateFuelEconomy(26.3f, now);

		vehicle.updateFuelEconomy(0f, now.plusSeconds(60));
		vehicle.updateFuelEconomy(Float.NaN, now.plusSeconds(60));
		vehicle.updateFuelEconomy(0.5f, now.plusSeconds(60));

		assertThat(vehicle.getMeasuredKmPerLiter()).isEqualByComparingTo("3.80");
		assertThat(vehicle.getFuelEconomyUpdatedAt()).isEqualTo(now);
	}

	@Test
	void Pleos가_km_L로_주면_그대로_쓴다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_25, BodyType.CARGO, null);

		// 100 ÷ 4.2 = 23.8km/L는 25톤 트럭으로 말이 안 되니 값 그대로 km/L로 본다
		vehicle.updateFuelEconomy(4.2f, now);

		assertThat(vehicle.getMeasuredKmPerLiter()).isEqualByComparingTo("4.20");
	}

	@Test
	void 두_단위_모두_말이_되면_톤수_기본값에_가까운_쪽을_고른다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_1, BodyType.CARGO, null);

		// 1톤 기본 8.5: 12를 L/100km로 보면 8.33, km/L로 보면 12. 8.33이 더 가깝다
		vehicle.updateFuelEconomy(12f, now);

		assertThat(vehicle.getMeasuredKmPerLiter()).isEqualByComparingTo("8.33");
	}

	@Test
	void 연비는_Pleos_실측_차주_입력_톤수_기본값_순서로_쓴다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.CARGO, null);
		assertThat(vehicle.kmPerLiter()).isEqualTo(Tonnage.TON_5.getDefaultKmPerLiter());
		assertThat(vehicle.fuelEconomySource()).isEqualTo(FuelEconomySource.DEFAULT);

		vehicle.changeManualFuelEconomy(new BigDecimal("5.5"));
		assertThat(vehicle.kmPerLiter()).isEqualTo(5.5);
		assertThat(vehicle.fuelEconomySource()).isEqualTo(FuelEconomySource.MANUAL);

		vehicle.updateFuelEconomy(26.3f, now);
		assertThat(vehicle.kmPerLiter()).isEqualTo(3.8);
		assertThat(vehicle.fuelEconomySource()).isEqualTo(FuelEconomySource.PLEOS);
	}

	@Test
	void 차주_입력_연비는_비울_수_있고_범위를_벗어나면_막는다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.CARGO, null);
		vehicle.changeManualFuelEconomy(new BigDecimal("5.5"));

		vehicle.changeManualFuelEconomy(null);

		assertThat(vehicle.getManualKmPerLiter()).isNull();
		assertError(() -> vehicle.changeManualFuelEconomy(new BigDecimal("1.4")), ErrorCode.VEHICLE_INVALID_FUEL_ECONOMY);
		assertError(() -> vehicle.changeManualFuelEconomy(new BigDecimal("15.1")), ErrorCode.VEHICLE_INVALID_FUEL_ECONOMY);
	}

	@Test
	void 톤수_차종_높이를_고칠_수_있다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.CARGO, null);

		vehicle.changeSpec(Tonnage.TON_11, BodyType.WING, new BigDecimal("3.95"));

		assertThat(vehicle.getTonnage()).isEqualTo(Tonnage.TON_11);
		assertThat(vehicle.getBodyType()).isEqualTo(BodyType.WING);
		assertThat(vehicle.getHeightM()).isEqualByComparingTo("3.95");
	}

	@Test
	void 처음에는_기본_차량이_아니다() {
		Vehicle vehicle = Vehicle.create(driver, "12가3456", Tonnage.TON_5, BodyType.CARGO, null);
		assertThat(vehicle.isDefaultVehicle()).isFalse();

		vehicle.markDefault();
		assertThat(vehicle.isDefaultVehicle()).isTrue();
		vehicle.unmarkDefault();
		assertThat(vehicle.isDefaultVehicle()).isFalse();
	}

	private static void assertError(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(errorCode);
	}
}
