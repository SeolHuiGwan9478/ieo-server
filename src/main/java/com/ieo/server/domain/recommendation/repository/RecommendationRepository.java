package com.ieo.server.domain.recommendation.repository;

import com.ieo.server.domain.recommendation.entity.Recommendation;
import com.ieo.server.domain.recommendation.enums.RecommendationAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

	List<Recommendation> findAllByTripId(Long tripId);

	Optional<Recommendation> findByTripIdAndOrderId(Long tripId, Long orderId);

	long countByTripIdAndAnswer(Long tripId, RecommendationAnswer answer);
}
