package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.BrokerMetrics;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** PK가 brokers.id와 같으므로 findById(brokerId)로 찾는다 */
public interface BrokerMetricsRepository extends JpaRepository<BrokerMetrics, Long> {

	@Query("select m.brokerId from BrokerMetrics m")
	List<Long> findAllBrokerIds();
}
