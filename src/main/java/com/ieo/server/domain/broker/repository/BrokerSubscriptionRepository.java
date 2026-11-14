package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.BrokerSubscription;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** PK가 brokers.id와 같으므로 findById(brokerId)로 찾는다 */
public interface BrokerSubscriptionRepository extends JpaRepository<BrokerSubscription, Long> {

	/** 기간이 끝난 유료 구독 (새벽 정리) */
	List<BrokerSubscription> findAllByPeriodEndLessThanEqual(Instant now);
}
