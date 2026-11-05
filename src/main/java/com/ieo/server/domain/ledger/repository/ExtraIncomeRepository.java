package com.ieo.server.domain.ledger.repository;

import com.ieo.server.domain.ledger.entity.ExtraIncome;
import com.ieo.server.domain.trip.enums.TripSource;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExtraIncomeRepository extends JpaRepository<ExtraIncome, Long> {

	List<ExtraIncome> findAllByLedgerEntryIdIn(Collection<Long> ledgerEntryIds);

	boolean existsByWaitRecordId(Long waitRecordId);

	/** 아직 받지 못한 대기비 */
	List<ExtraIncome> findAllByLedgerEntryDriverUserIdAndPaidAtIsNull(Long userId);

	/** 주선사 신뢰 지표: 이 회사 오더로 배차된 운행의 대기비 중 운행일이 since 이후인 것 (BRK-030) */
	@Query("""
			select x from ExtraIncome x join fetch x.ledgerEntry e
			where e.broker.id = :brokerId and e.trip.source = :source and e.workDate >= :since
			""")
	List<ExtraIncome> findAllForBrokerMetrics(@Param("brokerId") Long brokerId, @Param("source") TripSource source,
			@Param("since") LocalDate since);
}
