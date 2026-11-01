package com.ieo.server.domain.user.repository;

import com.ieo.server.domain.user.entity.CarLoginSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarLoginSessionRepository extends JpaRepository<CarLoginSession, UUID> {

	/** 휴대폰이 승인 화면에 띄울 세션을 찾는다. */
	Optional<CarLoginSession> findByCode(String code);

	boolean existsByCode(String code);

	/** 승인·거절이 동시에 들어와도 한 번만 처리되게 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CarLoginSession s where s.code = :code")
	Optional<CarLoginSession> findByCodeForUpdate(@Param("code") String code);

	/** 차량이 토큰을 받아 갈 때. 같은 세션으로 두 번 받지 못하게 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CarLoginSession s where s.id = :id")
	Optional<CarLoginSession> findByIdForUpdate(@Param("id") UUID id);

	/** 만료되고 하루 지난 세션. 새벽 정리 작업에서 쓴다. */
	@Modifying
	@Query("delete from CarLoginSession s where s.expiresAt < :before")
	int deleteExpiredBefore(@Param("before") Instant before);
}
