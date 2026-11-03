package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.Broker;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrokerRepository extends JpaRepository<Broker, Long> {

	Optional<Broker> findByBusinessNumber(String businessNumber);

	boolean existsByBusinessNumber(String businessNumber);

	/**
	 * 담당자 직책 변경·내보내기·나가기·초대를 회사 단위로 한 줄로 세운다.
	 * 대표 둘이 동시에 서로를 내리면 대표가 없어질 수 있어서다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Broker b where b.id = :id")
	Optional<Broker> findByIdForUpdate(@Param("id") Long id);
}
