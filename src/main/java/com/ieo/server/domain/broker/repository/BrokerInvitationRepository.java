package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.BrokerInvitation;
import com.ieo.server.domain.broker.enums.BrokerInvitationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 만료는 저장하지 않으므로 PENDING으로 읽은 뒤 statusAt(now)로 걸러 쓴다. */
public interface BrokerInvitationRepository extends JpaRepository<BrokerInvitation, Long> {

	/** 우리 회사가 보낸 초대. 초대한 사람 이름을 같이 보여 준다 */
	@EntityGraph(attributePaths = {"invitedBy", "invitedBy.user"})
	List<BrokerInvitation> findAllByBrokerIdAndStatusOrderByCreatedAtDesc(Long brokerId, BrokerInvitationStatus status);

	/** 내 번호로 온 초대. 회사 이름을 같이 보여 준다 */
	@EntityGraph(attributePaths = "broker")
	List<BrokerInvitation> findAllByPhoneAndStatusOrderByCreatedAtDesc(String phone, BrokerInvitationStatus status);

	List<BrokerInvitation> findAllByBrokerIdAndPhoneAndStatus(Long brokerId, String phone, BrokerInvitationStatus status);

	/** 수락·거절·취소가 동시에 들어와도 한 번만 처리되게 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from BrokerInvitation i where i.id = :id")
	Optional<BrokerInvitation> findByIdForUpdate(@Param("id") Long id);

	/** 탈퇴하면 그 번호로 온 초대를 지운다 (개인정보) */
	@Modifying
	@Query("delete from BrokerInvitation i where i.phone = :phone")
	void deleteByPhone(@Param("phone") String phone);
}
