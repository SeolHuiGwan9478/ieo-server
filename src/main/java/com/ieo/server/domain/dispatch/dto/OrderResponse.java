package com.ieo.server.domain.dispatch.dto;

import com.ieo.server.domain.broker.dto.BrokerMetricsResponse;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.dispatch.enums.CargoCategory;
import com.ieo.server.domain.dispatch.enums.ConfirmMode;
import com.ieo.server.domain.dispatch.enums.ConsignmentType;
import com.ieo.server.domain.dispatch.enums.LoadingMethod;
import com.ieo.server.domain.dispatch.enums.OrderStatus;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 오더. 화면 표기(label)를 같이 준다. open은 지금 요청할 수 있는지(OPEN이고 상차 시간이 남음).
 * 보는 쪽에 따라 채워지는 값: 차주 목록은 distanceM·myRequest, 주선사 목록은 pendingRequestCount.
 * brokerMetrics(주선사 신뢰 지표, BRK-031·DSP-011)는 차주가 볼 때만 채운다.
 * billing(거래처·청구운임·수수료)은 주선사가 볼 때만 채운다 (forBroker). 차주에게는 절대 나가지 않는다
 */
public record OrderResponse(
		Long id,
		OrderStatus status,
		boolean open,
		Long brokerId,
		String brokerName,
		BrokerMetricsResponse brokerMetrics,
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
		Long pendingRequestCount,
		Billing billing) {

	/**
	 * 화주 쪽 숫자. 주선사 콘솔만 본다. billedFare는 청구운임, fee는 수수료(청구 − 지급), feeRate는 수수료율(%, 청구운임 기준).
	 * 청구운임을 안 적었으면 셋 다 null
	 */
	public record Billing(String customerName, Integer billedFare, Integer fee, BigDecimal feeRate) {

		static Billing of(Order order) {
			return new Billing(order.getCustomerName(), order.getBilledFare(), order.fee(), order.feeRate());
		}
	}

	public static OrderResponse of(Order order, Instant now) {
		return of(order, now, null, null, null);
	}

	/** 주선사가 보는 오더: 청구·수수료를 같이 준다 */
	public static OrderResponse forBroker(Order order, Instant now, Long pendingRequestCount) {
		return of(order, now, null, null, pendingRequestCount, null, Billing.of(order));
	}

	public static OrderResponse of(Order order, Instant now, Long distanceM, DispatchRequestResponse myRequest,
			Long pendingRequestCount) {
		return of(order, now, distanceM, myRequest, pendingRequestCount, null);
	}

	public static OrderResponse of(Order order, Instant now, Long distanceM, DispatchRequestResponse myRequest,
			Long pendingRequestCount, BrokerMetricsResponse brokerMetrics) {
		return of(order, now, distanceM, myRequest, pendingRequestCount, brokerMetrics, null);
	}

	private static OrderResponse of(Order order, Instant now, Long distanceM, DispatchRequestResponse myRequest,
			Long pendingRequestCount, BrokerMetricsResponse brokerMetrics, Billing billing) {
		return new OrderResponse(
				order.getId(),
				order.getStatus(),
				order.isOpenAt(now),
				order.getBroker().getId(),
				order.getBroker().getName(),
				brokerMetrics,
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
				pendingRequestCount,
				billing);
	}
}
