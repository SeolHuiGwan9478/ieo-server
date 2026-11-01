package com.ieo.server.domain.user.repository;

import com.ieo.server.domain.user.entity.PhoneVerification;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

	/** 같은 번호로 동시에 들어온 요청이 틀린 횟수·발송 횟수를 덮어쓰지 않게 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select v from PhoneVerification v where v.phone = :phone")
	Optional<PhoneVerification> findByPhoneForUpdate(@Param("phone") String phone);

	/** 하루 넘게 쓰지 않은 행. 잠금이 남아 있으면 지우지 않는다. */
	@Modifying
	@Query("delete from PhoneVerification v where v.updatedAt < :before and (v.lockedUntil is null or v.lockedUntil < :now)")
	int deleteStale(@Param("before") Instant before, @Param("now") Instant now);

	@Modifying
	@Query("delete from PhoneVerification v where v.phone = :phone")
	int deleteByPhone(@Param("phone") String phone);
}
