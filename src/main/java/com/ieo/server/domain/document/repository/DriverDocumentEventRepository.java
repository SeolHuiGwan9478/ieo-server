package com.ieo.server.domain.document.repository;

import com.ieo.server.domain.document.entity.DriverDocumentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverDocumentEventRepository extends JpaRepository<DriverDocumentEvent, Long> {

	/** 한 차주의 처리 이력. 최근 것이 앞 */
	List<DriverDocumentEvent> findAllByDriverUserIdOrderByCreatedAtDescIdDesc(Long userId);

	void deleteAllByDriverUserId(Long userId);
}
