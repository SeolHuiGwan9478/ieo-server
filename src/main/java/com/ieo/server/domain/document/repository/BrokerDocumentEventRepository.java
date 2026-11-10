package com.ieo.server.domain.document.repository;

import com.ieo.server.domain.document.entity.BrokerDocumentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerDocumentEventRepository extends JpaRepository<BrokerDocumentEvent, Long> {

	/** 한 회사의 처리 이력. 최근 것이 앞 */
	List<BrokerDocumentEvent> findAllByBrokerIdOrderByCreatedAtDescIdDesc(Long brokerId);
}
