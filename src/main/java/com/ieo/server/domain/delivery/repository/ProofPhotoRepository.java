package com.ieo.server.domain.delivery.repository;

import com.ieo.server.domain.delivery.entity.ProofPhoto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProofPhotoRepository extends JpaRepository<ProofPhoto, Long> {

	List<ProofPhoto> findAllByDeliveryProofIdOrderByIdAsc(Long deliveryProofId);

	/** 여러 인수증의 사진 (주선사 운행 목록) */
	List<ProofPhoto> findAllByDeliveryProofIdInOrderByIdAsc(Collection<Long> deliveryProofIds);

	long countByDeliveryProofId(Long deliveryProofId);
}
