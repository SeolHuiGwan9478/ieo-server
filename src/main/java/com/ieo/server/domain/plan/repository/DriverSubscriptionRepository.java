package com.ieo.server.domain.plan.repository;

import com.ieo.server.domain.plan.entity.DriverSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

/** PK가 drivers.user_id와 같으므로 findById(userId)로 찾는다 */
public interface DriverSubscriptionRepository extends JpaRepository<DriverSubscription, Long> {
}
