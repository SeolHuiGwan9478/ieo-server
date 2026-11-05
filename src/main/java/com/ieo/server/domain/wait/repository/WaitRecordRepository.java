package com.ieo.server.domain.wait.repository;

import com.ieo.server.domain.wait.entity.WaitRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitRecordRepository extends JpaRepository<WaitRecord, Long> {

	Optional<WaitRecord> findByTripStopId(Long tripStopId);

	boolean existsByTripStopId(Long tripStopId);

	/** 운행의 상차·하차 대기 기록 */
	List<WaitRecord> findAllByTripStopTripId(Long tripId);

	/** 여러 운행의 대기 기록 (주선사 운행 목록) */
	List<WaitRecord> findAllByTripStopTripIdIn(Collection<Long> tripIds);

	/** 상하차지 통계용: 이 상하차지에서 since 이후 진입한 기록 */
	@Query("""
			select r from WaitRecord r
			where r.tripStop.site.id = :siteId and r.arrivedAt >= :since
			""")
	List<WaitRecord> findAllForSiteStats(@Param("siteId") Long siteId, @Param("since") Instant since);

	/** since 이후 기록이 있는 상하차지 */
	@Query("select distinct r.tripStop.site.id from WaitRecord r where r.arrivedAt >= :since")
	List<Long> findSiteIdsArrivedSince(@Param("since") Instant since);
}
