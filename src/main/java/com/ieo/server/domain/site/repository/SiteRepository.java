package com.ieo.server.domain.site.repository;

import com.ieo.server.domain.site.entity.Site;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteRepository extends JpaRepository<Site, Long> {

	/**
	 * (lat, lng)에서 meters 안의 상하차지를 가까운 순으로 (SITE-002 같은 곳 찾기, SITE-006 근처 찾기).
	 * geography라서 거리는 지구 타원체 위의 미터다
	 */
	@Query(nativeQuery = true, value = """
			select * from sites s
			where ST_DWithin(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :meters)
			order by ST_Distance(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
			limit :limit
			""")
	List<Site> findWithin(@Param("lat") double lat, @Param("lng") double lng,
			@Param("meters") double meters, @Param("limit") int limit);

	/** 탈퇴한 차주와의 연결을 끊는다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Site s set s.createdBy = null where s.createdBy.userId = :userId")
	int detachCreator(@Param("userId") Long userId);
}
