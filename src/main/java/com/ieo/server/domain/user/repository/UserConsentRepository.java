package com.ieo.server.domain.user.repository;

import com.ieo.server.domain.user.entity.UserConsent;
import com.ieo.server.domain.user.enums.ConsentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

	/** 종류 하나의 현재 동의 상태 (가장 최근 행) */
	Optional<UserConsent> findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(Long userId, ConsentType type);

	/** 종류별 현재 동의 상태. id는 IDENTITY라 늦게 쌓인 행일수록 크다. */
	@Query("""
		select c from UserConsent c
		where c.id in (
			select max(c2.id) from UserConsent c2
			where c2.user.id = :userId
			group by c2.type
		)
		""")
	List<UserConsent> findLatestByUserId(@Param("userId") Long userId);

	/** 여러 사람의 한 종류 현재 동의 상태 */
	@Query("""
		select c from UserConsent c
		where c.id in (
			select max(c2.id) from UserConsent c2
			where c2.user.id in :userIds and c2.type = :type
			group by c2.user.id
		)
		""")
	List<UserConsent> findLatestByUserIdInAndType(@Param("userIds") Collection<Long> userIds, @Param("type") ConsentType type);

	/** 동의·철회 이력 전체, 최근 순 */
	List<UserConsent> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
