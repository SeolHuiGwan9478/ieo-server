package com.ieo.server.domain.notification.dto;

import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.notification.entity.Notification;
import com.ieo.server.domain.notification.enums.NotificationType;
import java.time.Instant;

/**
 * 앱에 보여 주고 읽어 줄 알림. message는 화면과 음성에 그대로 쓴다
 */
public record NotificationResponse(
		Long id,
		NotificationType type,
		String message,
		Long dispatchRequestId,
		Long orderId,
		Instant createdAt) {

	public static NotificationResponse of(Notification notification) {
		DispatchRequest request = notification.getDispatchRequest();
		Order order = request.getOrder();
		return new NotificationResponse(notification.getId(), notification.getType(),
				message(notification.getType(), order), request.getId(), order.getId(), notification.getCreatedAt());
	}

	private static String message(NotificationType type, Order order) {
		String route = order.getPickupSite().getName() + "에서 " + order.getDropoffSite().getName();
		return switch (type) {
			case DISPATCH_CONFIRMED -> order.getBroker().getName() + "에서 배차를 확정했어요. " + route + "까지, 내 배차에 넣었어요";
			case DISPATCH_DECLINED -> route + "까지 가는 배차는 마감됐어요";
			case DISPATCH_EXPIRED -> route + "까지 가는 배차 요청이 10분 안에 확정되지 않았어요";
		};
	}
}
