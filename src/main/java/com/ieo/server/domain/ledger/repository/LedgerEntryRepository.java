package com.ieo.server.domain.ledger.repository;

import com.ieo.server.domain.ledger.entity.LedgerEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	Optional<LedgerEntry> findByTripId(Long tripId);

	boolean existsByTripId(Long tripId);

	/** 기간 장부. 최근 날짜 먼저 */
	List<LedgerEntry> findAllByDriverUserIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
			Long userId, LocalDate from, LocalDate to);

	/** 미수금. 지급 예정일이 빠른 순서 (LEDG-021, 022) */
	List<LedgerEntry> findAllByDriverUserIdAndPaidAtIsNullOrderByExpectedPaymentDateAsc(Long userId);
}
