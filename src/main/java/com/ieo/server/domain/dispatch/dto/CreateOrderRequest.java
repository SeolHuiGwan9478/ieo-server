package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.enums.ConfirmMode;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * 오더 등록 (DSP-001). 필수 항목 검사·값 범위는 Order가 한다 (DSP-002).
 * confirmMode를 비우면 SELECT, targetDriverId가 있으면 그 차주에게만 보인다
 */
public record CreateOrderRequest(
		@NotNull @Valid OrderSiteRequest pickup,
		@NotNull @Valid OrderSiteRequest dropoff,
		@NotNull Instant pickupFrom,
		@NotNull Instant pickupUntil,
		@NotNull Instant dropoffBy,
		String cargoType,
		Integer weightKg,
		Tonnage tonnage,
		BodyType bodyType,
		LoadingMethod loadingMethod,
		Integer fare,
		PaymentType paymentType,
		Integer expectedPaymentDays,
		ConsignmentType consignmentType,
		ConfirmMode confirmMode,
		Long targetDriverId) {
}
