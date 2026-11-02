package com.ieo.server.domain.site.repository;

import com.ieo.server.domain.site.entity.SiteStats;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** PK가 sites.id와 같으므로 findById(siteId)로 찾는다 */
public interface SiteStatsRepository extends JpaRepository<SiteStats, Long> {

	@Query("select s.siteId from SiteStats s")
	List<Long> findAllSiteIds();
}
