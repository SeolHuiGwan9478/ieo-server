package com.ieo.server.domain.notification.entity;

import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.dispatch.entity.DispatchRequest;
import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.notification.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주에게 보낸 앱 알림 한 건 (NOTI-004). 앱은 SSE로 바로 받고, 끊겼던 동안의 알림은 id 순서로 이어 받는다.
 * 받는 사람과 문장은 배차 요청에서 나오므로 저장하지 않는다. 같은 요청·같은 종류는 한 번만 (NOTI-003)
 */
@Getter
@Entity
@Table(name = "notifications",
		uniqueConstraints = @UniqueConstraint(name = "uk_notifications_request_type", columnNames = {"dispatch_request_id", "type"}),
		indexes = @Index(name = "idx_notifications_user", columnList = "user_id, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 받는 사람. 요청한 차주의 사용자 id. 내 알림만 id 순서로 찾으려고 둔다 */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	/** 배차 알림이면 그 배차 요청. 단골 요청 알림이면 null */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "dispatch_request_id")
	private DispatchRequest dispatchRequest;

	/** 지정 오더 알림이면 그 오더. 다른 알림이면 null */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private Order order;

	/** 단골 요청 알림이면 그 요청. 배차 알림이면 null */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "driver_link_id")
	private BrokerDriverLink driverLink;

	@Column(nullable = false)
	private Instant createdAt;

	private Notification(DispatchRequest request, NotificationType type, Instant now) {
		this.userId = request.getDriver().getUserId();
		this.type = type;
		this.dispatchRequest = request;
		this.createdAt = now;
	}

	public static Notification about(DispatchRequest request, NotificationType type, Instant now) {
		return new Notification(request, type, now);
	}

	/** 지정 오더를 받은 단골 차주에게 */
	public static Notification aboutTargetedOrder(Order order, Long driverUserId, Instant now) {
		Notification notification = new Notification();
		notification.userId = driverUserId;
		notification.type = NotificationType.ORDER_TARGETED;
		notification.order = order;
		notification.createdAt = now;
		return notification;
	}

	/** 단골 연결 요청을 받은 차주에게 */
	public static Notification aboutLink(BrokerDriverLink link, Long driverUserId, Instant now) {
		Notification notification = new Notification();
		notification.userId = driverUserId;
		notification.type = NotificationType.DRIVER_LINK_REQUESTED;
		notification.driverLink = link;
		notification.createdAt = now;
		return notification;
	}
}
