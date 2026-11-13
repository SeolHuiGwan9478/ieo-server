package com.ieo.server.domain.notification.repository;

import com.ieo.server.domain.notification.entity.PushToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

	Optional<PushToken> findByToken(String token);

	List<PushToken> findAllByUserId(Long userId);

	@Modifying
	@Query("delete from PushToken t where t.token in :tokens")
	void deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);

	@Modifying
	@Query("delete from PushToken t where t.userId = :userId and t.token = :token")
	void deleteByUserIdAndToken(@Param("userId") Long userId, @Param("token") String token);

	@Modifying
	@Query("delete from PushToken t where t.userId = :userId")
	void deleteAllByUserId(@Param("userId") Long userId);
}
