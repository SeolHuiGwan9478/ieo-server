package com.ieo.server.domain.plan.repository;

import com.ieo.server.domain.plan.entity.PlanPayment;
import com.ieo.server.domain.plan.enums.PlanOwnerType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPaymentRepository extends JpaRepository<PlanPayment, Long> {

	List<PlanPayment> findAllByOwnerTypeAndOwnerIdOrderByPaidAtDesc(PlanOwnerType ownerType, Long ownerId);
}
