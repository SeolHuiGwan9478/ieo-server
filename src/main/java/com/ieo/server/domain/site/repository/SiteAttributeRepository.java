package com.ieo.server.domain.site.repository;

import com.ieo.server.domain.site.entity.SiteAttribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteAttributeRepository extends JpaRepository<SiteAttribute, Long> {

	/** 항목별 최신 값만 남긴 목록 (SITE-004, 007). 같은 시각이면 나중에 들어온 행을 쓴다 */
	@Query("""
			select a from SiteAttribute a
			where a.site.id = :siteId
			  and a.id = (select max(b.id) from SiteAttribute b
			              where b.site.id = :siteId and b.key = a.key)
			""")
	List<SiteAttribute> findLatestBySiteId(@Param("siteId") Long siteId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update SiteAttribute a set a.reportedBy = null where a.reportedBy.userId = :userId")
	int detachReporter(@Param("userId") Long userId);
}
