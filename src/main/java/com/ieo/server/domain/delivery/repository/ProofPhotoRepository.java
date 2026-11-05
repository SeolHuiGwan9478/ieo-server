package com.ieo.server.domain.delivery.repository;

import com.ieo.server.domain.delivery.entity.ProofPhoto;
import com.ieo.server.domain.delivery.enums.ProofPhotoType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProofPhotoRepository extends JpaRepository<ProofPhoto, Long> {

	List<ProofPhoto> findAllByDeliveryProofIdOrderByIdAsc(Long deliveryProofId);

	long countByDeliveryProofId(Long deliveryProofId);

	boolean existsByDeliveryProofIdAndType(Long deliveryProofId, ProofPhotoType type);

	/** 이 종류의 사진이 있는 인수증 id (주선사 운행 목록에서 종이 인수증 여부) */
	@Query("select distinct p.deliveryProof.id from ProofPhoto p where p.deliveryProof.id in :proofIds and p.type = :type")
	List<Long> findProofIdsHavingType(@Param("proofIds") Collection<Long> proofIds, @Param("type") ProofPhotoType type);
}
