package com.ieo.server.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TruckSpecTest {

	@ParameterizedTest
	@CsvSource({
			"TON_1, 1", "TON_1_4, 1",
			"TON_2_5, 2", "TON_3_5, 2", "TON_5, 2",
			"TON_8, 3",
			"TON_5_AXLE, 4", "TON_11, 4", "TON_14, 4", "TON_18, 4",
			"TON_25, 5",
	})
	void 톤수별로_통행료_차종을_고른다(Tonnage tonnage, int carType) {
		Vehicle vehicle = mock(Vehicle.class);
		given(vehicle.getTonnage()).willReturn(tonnage);
		given(vehicle.getAxleCount()).willReturn(null);

		assertThat(TruckSpec.of(vehicle).carType()).isEqualTo(carType);
	}

	@ParameterizedTest
	@CsvSource({
			"TON_1, 2, 1", "TON_5, 2, 2", "TON_8, 2, 3", "TON_11, 2, 3", "TON_25, 2, 3",
			"TON_5, 3, 4", "TON_18, 3, 4", "TON_25, 3, 4",
			"TON_11, 4, 5", "TON_25, 5, 5",
	})
	void 축_수를_입력하면_톤수보다_먼저_따른다(Tonnage tonnage, int axleCount, int carType) {
		Vehicle vehicle = mock(Vehicle.class);
		given(vehicle.getTonnage()).willReturn(tonnage);
		given(vehicle.getAxleCount()).willReturn(axleCount);

		assertThat(TruckSpec.of(vehicle).carType()).isEqualTo(carType);
	}
}
