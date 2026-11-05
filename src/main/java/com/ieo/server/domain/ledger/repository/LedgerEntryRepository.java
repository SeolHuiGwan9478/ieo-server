package com.ieo.server.domain.ledger.repository;

import com.ieo.server.domain.ledger.entity.LedgerEntry;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	Optional<LedgerEntry> findByTripId(Long tripId);

	boolean existsByTripId(Long tripId);

	/** 기간 장부. 최근 날짜 먼저 */
	List<LedgerEntry> findAllByDriverUserIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
			Long userId, LocalDate from, LocalDate to);

	/** 미수금. 지급 예정일이 빠른 순서 (LEDG-021, 022) */
	List<LedgerEntry> findAllByDriverUserIdAndPaidAtIsNullOrderByExpectedPaymentDateAsc(Long userId);

	/** 주선사 신뢰 지표: 이 회사 오더로 배차된 운행의 장부 중 결제 방식이 같고 운행일이 since 이후인 것 (BRK-030) */
	@Query("""
			select e from LedgerEntry e
			where e.broker.id = :brokerId and e.trip.source = :source
			  and e.paymentType = :paymentType and e.workDate >= :since
			""")
	List<LedgerEntry> findAllForBrokerMetrics(@Param("brokerId") Long brokerId, @Param("source") TripSource source,
			@Param("paymentType") PaymentType paymentType, @Param("since") LocalDate since);
}
