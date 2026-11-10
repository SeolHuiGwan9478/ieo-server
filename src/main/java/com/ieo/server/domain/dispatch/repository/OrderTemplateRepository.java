package com.ieo.server.domain.dispatch.repository;

import com.ieo.server.domain.dispatch.entity.OrderTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTemplateRepository extends JpaRepository<OrderTemplate, Long> {

	/** 회사의 노선. 최근 저장한 것 먼저 */
	List<OrderTemplate> findAllByBrokerIdOrderByCreatedAtDesc(Long brokerId);

	long countByBrokerId(Long brokerId);
}
