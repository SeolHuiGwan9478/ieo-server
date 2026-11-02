package com.ieo.server.domain.site.repository;

import com.ieo.server.domain.site.entity.SiteNote;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteNoteRepository extends JpaRepository<SiteNote, Long> {

	/**
	 * 정보 카드에 보여 줄 메모 (SITE-007, 009). 신고가 적은 것 먼저, 그다음 최근 것 먼저.
	 * 정보 카드는 Pageable.ofSize(5)로 부른다
	 */
	@Query("""
			select n from SiteNote n
			where n.site.id = :siteId
			order by n.reportedCount asc, n.createdAt desc, n.id desc
			""")
	List<SiteNote> findForCard(@Param("siteId") Long siteId, Pageable pageable);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update SiteNote n set n.driver = null where n.driver.userId = :userId")
	int detachAuthor(@Param("userId") Long userId);
}
