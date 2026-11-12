package com.ieo.server.domain.ledger.repository;

import com.ieo.server.domain.ledger.entity.TaxInvoice;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxInvoiceRepository extends JpaRepository<TaxInvoice, Long> {

	Optional<TaxInvoice> findByLedgerEntryId(Long ledgerEntryId);

	List<TaxInvoice> findAllByLedgerEntryIdIn(Collection<Long> ledgerEntryIds);

	/** 플랜 한도: 이번 달에 발행한 장 수 */
	long countByDriverUserIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(Long driverId, Instant from, Instant to);

	List<TaxInvoice> findAllByDriverUserIdAndWriteDateBetweenOrderByWriteDateDescIdDesc(Long driverId, LocalDate from, LocalDate to);
}
