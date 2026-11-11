package com.ieo.server.domain.restarea.repository;

import com.ieo.server.domain.restarea.entity.RestArea;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestAreaRepository extends JpaRepository<RestArea, Long> {

	/** 지난 갱신 때 응답에 있던 휴게소 */
	List<RestArea> findAllByMissingSinceIsNull();

	/** 좌표가 있고 지난 갱신 때 응답에 있던 휴게소 중 반경 안, 가까운 순 */
	@Query(nativeQuery = true, value = """
			select * from rest_areas r
			where r.missing_since is null and r.location is not null
			  and ST_DWithin(r.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :meters)
			order by ST_Distance(r.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
			limit 500
			""")
	List<RestArea> findActiveWithin(@Param("lat") double lat, @Param("lng") double lng, @Param("meters") double meters);
}
