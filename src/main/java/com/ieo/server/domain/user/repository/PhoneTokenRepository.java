package com.ieo.server.domain.user.repository;

import com.ieo.server.domain.user.entity.PhoneToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhoneTokenRepository extends JpaRepository<PhoneToken, String> {

	/** 지운 행 수를 돌려준다. 동시에 같은 토큰을 쓰면 한 쪽만 1을 받는다. */
	@Modifying
	@Query("delete from PhoneToken t where t.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);

	@Modifying
	@Query("delete from PhoneToken t where t.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);

	@Modifying
	@Query("delete from PhoneToken t where t.phone = :phone")
	int deleteByPhone(@Param("phone") String phone);
}
