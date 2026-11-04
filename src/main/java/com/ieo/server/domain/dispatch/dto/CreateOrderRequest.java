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
 * confirmMode를 비우면 SELECT, targetDriverId가 있으면 그 차주에게만 보인다 (지정 배차, DSP-006).
 * 지정은 우리 회사 단골 차주에게만 할 수 있다 (BRK-002, 아니면 400 ORDER_400_TARGET). 비우면 오더 마켓에 공개.
 * expectedPaymentDays(인수증 후 지급까지 일수)를 비우면 회사 정산 주기로 채운다 (BRK-003)
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
