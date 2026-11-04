package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.BrokerDriverLink;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 만료는 저장하지 않으므로 PENDING으로 읽은 뒤 statusAt(now)로 걸러 쓴다. */
public interface BrokerDriverLinkRepository extends JpaRepository<BrokerDriverLink, Long> {

	/** 우리 회사가 보낸 요청. 요청한 담당자 이름을 같이 보여 준다 */
	@EntityGraph(attributePaths = {"invitedBy", "invitedBy.user"})
	List<BrokerDriverLink> findAllByBrokerIdAndStatusOrderByCreatedAtDesc(Long brokerId, DriverLinkStatus status);

	/** 우리 회사 단골 차주. 이름과 번호를 같이 보여 준다 */
	@EntityGraph(attributePaths = {"driver", "driver.user"})
	List<BrokerDriverLink> findAllByBrokerIdAndStatusOrderByLinkedAtDesc(Long brokerId, DriverLinkStatus status);

	/** 내 번호로 온 요청. 회사 이름을 같이 보여 준다 */
	@EntityGraph(attributePaths = "broker")
	List<BrokerDriverLink> findAllByPhoneAndStatusOrderByCreatedAtDesc(String phone, DriverLinkStatus status);

	/** 내가 연결된 회사 */
	@EntityGraph(attributePaths = "broker")
	List<BrokerDriverLink> findAllByDriverUserIdAndStatusOrderByLinkedAtDesc(Long driverUserId, DriverLinkStatus status);

	List<BrokerDriverLink> findAllByBrokerIdAndPhoneAndStatus(Long brokerId, String phone, DriverLinkStatus status);

	boolean existsByBrokerIdAndDriverUserIdAndStatus(Long brokerId, Long driverUserId, DriverLinkStatus status);

	/** 수락·거절·취소·해제가 동시에 들어와도 한 번만 처리되게 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from BrokerDriverLink l where l.id = :id")
	Optional<BrokerDriverLink> findByIdForUpdate(@Param("id") Long id);

	/** 탈퇴하면 그 번호로 온 요청과 연결을 지운다 (개인정보). 이미 확정된 배차·운행은 오더와 운행에 남는다 */
	@Modifying
	@Query("delete from BrokerDriverLink l where l.phone = :phone")
	void deleteByPhone(@Param("phone") String phone);
}
