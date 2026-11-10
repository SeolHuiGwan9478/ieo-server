package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.entity.OrderTemplate;
import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.time.Instant;

/** 저장한 노선. 오더 등록 폼에 그대로 채우고, 상하차지는 siteId로 다시 보낸다 */
public record OrderTemplateResponse(Long id, String name, OrderSiteResponse pickup, OrderSiteResponse dropoff,
		CargoCategory cargoCategory, String cargoType, Integer weightKg, Tonnage tonnage, BodyType bodyType,
		LoadingMethod loadingMethod, Integer fare, PaymentType paymentType, Integer expectedPaymentDays,
		ConsignmentType consignmentType, Instant createdAt) {

	public static OrderTemplateResponse from(OrderTemplate t) {
		return new OrderTemplateResponse(t.getId(), t.getName(), OrderSiteResponse.from(t.getPickupSite()),
				OrderSiteResponse.from(t.getDropoffSite()), t.getCargoCategory(), t.getCargoType(), t.getWeightKg(),
				t.getTonnage(), t.getBodyType(), t.getLoadingMethod(), t.getFare(), t.getPaymentType(),
				t.getExpectedPaymentDays(), t.getConsignmentType(), t.getCreatedAt());
	}
}
