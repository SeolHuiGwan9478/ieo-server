package com.ieo.server.domain.delivery.repository;

import com.ieo.server.domain.delivery.entity.ProofPhoto;
import com.ieo.server.domain.delivery.enums.ProofPhotoType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProofPhotoRepository extends JpaRepository<ProofPhoto, Long> {

	List<ProofPhoto> findAllByDeliveryProofIdOrderByIdAsc(Long deliveryProofId);

	long countByDeliveryProofId(Long deliveryProofId);

	boolean existsByDeliveryProofIdAndType(Long deliveryProofId, ProofPhotoType type);
}
