package com.ieo.server.domain.recommendation.repository;

import com.ieo.server.domain.recommendation.entity.RecommendationShown;
import com.ieo.server.domain.trip.enums.StopType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationShownRepository extends JpaRepository<RecommendationShown, Long> {

	/** 이 운행의 이 상하차지에서 이미 추천했는지 */
	Optional<RecommendationShown> findFirstByDriverUserIdAndTripIdAndStopTypeOrderByShownAtDesc(Long driverId, Long tripId,
			StopType stopType);

	/** 운행이 없을 때 [since] 뒤에 받은 가장 최근 추천 */
	Optional<RecommendationShown> findFirstByDriverUserIdAndTripIdIsNullAndShownAtAfterOrderByShownAtDesc(Long driverId,
			Instant since);
}
