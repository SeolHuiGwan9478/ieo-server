package com.ieo.server.domain.delivery.repository;

import com.ieo.server.domain.delivery.entity.DeliveryProof;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, Long> {

	Optional<DeliveryProof> findByTripId(Long tripId);

	/** 여러 운행의 인수증 (주선사 운행 목록) */
	List<DeliveryProof> findAllByTripIdIn(Collection<Long> tripIds);

	/** 수하인이 링크를 열 때 */
	Optional<DeliveryProof> findByToken(String token);
}
