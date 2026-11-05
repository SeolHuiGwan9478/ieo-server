package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConfirmMode;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.time.Instant;

/**
 * 오더. 화면 표기(label)를 같이 준다. open은 지금 요청할 수 있는지(OPEN이고 상차 시간이 남음).
 * 보는 쪽에 따라 채워지는 값: 차주 목록은 distanceM·myRequest, 주선사 목록은 pendingRequestCount
 */
public record OrderResponse(
		Long id,
		OrderStatus status,
		boolean open,
		Long brokerId,
		String brokerName,
		OrderSiteResponse pickup,
		OrderSiteResponse dropoff,
		Instant pickupFrom,
		Instant pickupUntil,
		Instant dropoffBy,
		CargoCategory cargoCategory,
		String cargoCategoryLabel,
		String cargoType,
		int weightKg,
		Tonnage tonnage,
		String tonnageLabel,
		BodyType bodyType,
		String bodyTypeLabel,
		LoadingMethod loadingMethod,
		String loadingMethodLabel,
		int fare,
		PaymentType paymentType,
		String paymentTypeLabel,
		int expectedPaymentDays,
		ConsignmentType consignmentType,
		ConfirmMode confirmMode,
		Long targetDriverId,
		Instant createdAt,
		Long distanceM,
		DispatchRequestResponse myRequest,
		Long pendingRequestCount) {

	public static OrderResponse of(Order order, Instant now) {
		return of(order, now, null, null, null);
	}

	public static OrderResponse of(Order order, Instant now, Long distanceM, DispatchRequestResponse myRequest,
			Long pendingRequestCount) {
		return new OrderResponse(
				order.getId(),
				order.getStatus(),
				order.isOpenAt(now),
				order.getBroker().getId(),
				order.getBroker().getName(),
				OrderSiteResponse.from(order.getPickupSite()),
				OrderSiteResponse.from(order.getDropoffSite()),
				order.getPickupFrom(),
				order.getPickupUntil(),
				order.getDropoffBy(),
				order.getCargoCategory(),
				order.getCargoCategory().getLabel(),
				order.getCargoType(),
				order.getWeightKg(),
				order.getTonnage(),
				order.getTonnage().getLabel(),
				order.getBodyType(),
				order.getBodyType().getLabel(),
				order.getLoadingMethod(),
				order.getLoadingMethod().getLabel(),
				order.getFare(),
				order.getPaymentType(),
				order.getPaymentType().getLabel(),
				order.getExpectedPaymentDays(),
				order.getConsignmentType(),
				order.getConfirmMode(),
				order.getTargetDriver() == null ? null : order.getTargetDriver().getUserId(),
				order.getCreatedAt(),
				distanceM,
				myRequest,
				pendingRequestCount);
	}
}
