package com.ieo.server.domain.site.repository;

import com.ieo.server.domain.site.entity.Site;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteRepository extends JpaRepository<Site, Long> {

	/**
	 * 위도·경도 범위 안의 상하차지. 정확한 거리는 서비스가 Site.distanceMeters로 다시 거른다
	 * (SITE-002 같은 곳 찾기, SITE-006 근처 찾기)
	 */
	@Query("""
			select s from Site s
			where s.lat between :minLat and :maxLat
			  and s.lng between :minLng and :maxLng
			""")
	List<Site> findInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
			@Param("minLng") double minLng, @Param("maxLng") double maxLng);

	/** 탈퇴한 차주와의 연결을 끊는다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Site s set s.createdBy = null where s.createdBy.userId = :userId")
	int detachCreator(@Param("userId") Long userId);
}
