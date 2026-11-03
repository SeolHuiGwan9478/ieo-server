package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerMemberPosition;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PK가 users.id와 같으므로 findById(userId)로 담당자 프로필을 찾는다.
 * 회사에서 나온 담당자(removedAt 있음)도 행이 남아 있으니, 소속 여부는 isActive()로 확인한다.
 */
public interface BrokerMemberRepository extends JpaRepository<BrokerMember, Long> {

	/** 지금 소속된 담당자 목록. 이름·전화번호를 같이 보여주므로 user를 함께 읽는다. */
	@EntityGraph(attributePaths = "user")
	List<BrokerMember> findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(Long brokerId);

	long countByBrokerIdAndPositionAndRemovedAtIsNull(Long brokerId, BrokerMemberPosition position);
}
