package com.ieo.server.domain.wait.repository;

import com.ieo.server.domain.wait.entity.WaitPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitPolicyRepository extends JpaRepository<WaitPolicy, Long> {

	/** 주선사의 지금 정책. 대기가 시작될 때 기록에 붙인다 */
	Optional<WaitPolicy> findFirstByBrokerIdAndActiveTrue(Long brokerId);

	/** 정책 이력. 최근 것 먼저 */
	List<WaitPolicy> findAllByBrokerIdOrderByCreatedAtDesc(Long brokerId);
}
