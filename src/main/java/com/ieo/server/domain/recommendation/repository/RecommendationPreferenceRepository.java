package com.ieo.server.domain.recommendation.repository;

import com.ieo.server.domain.recommendation.entity.RecommendationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/** PK가 차주 id와 같으므로 findById(driverId)로 찾는다 */
public interface RecommendationPreferenceRepository extends JpaRepository<RecommendationPreference, Long> {
}
