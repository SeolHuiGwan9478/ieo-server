package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.BrokerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

/** PK가 brokers.id와 같으므로 findById(brokerId)로 찾는다 */
public interface BrokerSubscriptionRepository extends JpaRepository<BrokerSubscription, Long> {
}
