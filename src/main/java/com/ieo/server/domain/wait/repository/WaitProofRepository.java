package com.ieo.server.domain.wait.repository;

import com.ieo.server.domain.wait.entity.WaitProof;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitProofRepository extends JpaRepository<WaitProof, Long> {

	/** 주선사가 링크를 열 때 */
	Optional<WaitProof> findByShareToken(String shareToken);

	Optional<WaitProof> findByTripId(Long tripId);

	List<WaitProof> findAllByTripIdIn(Collection<Long> tripIds);

	/** 플랜 한도: 이 시각 이후 주선사에게 보낸 증빙 수 */
	long countByTripDriverUserIdAndSentAtGreaterThanEqual(Long driverId, Instant since);
}
