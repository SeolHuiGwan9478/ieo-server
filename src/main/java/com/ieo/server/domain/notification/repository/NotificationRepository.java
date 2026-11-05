package com.ieo.server.domain.notification.repository;

import com.ieo.server.domain.notification.entity.Notification;
import com.ieo.server.domain.notification.enums.NotificationType;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	boolean existsByDispatchRequestIdAndType(Long requestId, NotificationType type);

	/** after 다음 알림부터 오래된 순서 */
	List<Notification> findAllByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long after, Limit limit);

	/** 가장 최근 알림부터. 처음 연결할 때 */
	List<Notification> findAllByUserIdOrderByIdDesc(Long userId, Limit limit);
}
