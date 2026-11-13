package com.ieo.server.domain.notification.repository;

import com.ieo.server.domain.notification.entity.Notification;
import com.ieo.server.domain.notification.enums.NotificationType;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	boolean existsByDispatchRequestIdAndType(Long requestId, NotificationType type);

	/** after 다음 알림부터 오래된 순서 */
	List<Notification> findAllByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long after, Limit limit);

	/** 탈퇴로 그 번호의 단골 요청을 지우기 전에, 그 요청을 가리키는 알림을 먼저 지운다 */
	@Modifying
	@Query("delete from Notification n where n.driverLink.id in (select l.id from BrokerDriverLink l where l.phone = :phone)")
	void deleteByDriverLinkPhone(@Param("phone") String phone);

	/** 가장 최근 알림부터. 처음 연결할 때 */
	List<Notification> findAllByUserIdOrderByIdDesc(Long userId, Limit limit);
}
