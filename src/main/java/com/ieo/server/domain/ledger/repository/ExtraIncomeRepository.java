package com.ieo.server.domain.ledger.repository;

import com.ieo.server.domain.ledger.entity.ExtraIncome;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtraIncomeRepository extends JpaRepository<ExtraIncome, Long> {

	List<ExtraIncome> findAllByLedgerEntryIdIn(Collection<Long> ledgerEntryIds);

	boolean existsByWaitRecordId(Long waitRecordId);
}
