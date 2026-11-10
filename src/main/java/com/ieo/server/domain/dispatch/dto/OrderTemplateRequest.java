package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 노선 저장 (DSP-004). 오더 등록과 같은 모양에서 시간·거래처·지정 차주만 뺐다 */
public record OrderTemplateRequest(
		String name,
		@NotNull @Valid OrderSiteRequest pickup,
		@NotNull @Valid OrderSiteRequest dropoff,
		CargoCategory cargoCategory,
		String cargoType,
		Integer weightKg,
		Tonnage tonnage,
		BodyType bodyType,
		LoadingMethod loadingMethod,
		Integer fare,
		PaymentType paymentType,
		Integer expectedPaymentDays,
		ConsignmentType consignmentType) {
}
