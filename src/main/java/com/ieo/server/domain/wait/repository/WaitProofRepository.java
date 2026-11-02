package com.ieo.server.domain.wait.repository;

import com.ieo.server.domain.wait.entity.WaitProof;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitProofRepository extends JpaRepository<WaitProof, Long> {

	/** 주선사가 링크를 열 때 */
	Optional<WaitProof> findByShareToken(String shareToken);

	Optional<WaitProof> findByWaitRecordId(Long waitRecordId);
}
