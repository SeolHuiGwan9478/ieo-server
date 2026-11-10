package com.ieo.server.domain.document.repository;

import com.ieo.server.domain.document.entity.BrokerDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrokerDocumentRepository extends JpaRepository<BrokerDocument, Long> {

	/** 한 회사의 서류 전부. 최근 제출이 앞 */
	List<BrokerDocument> findAllByBrokerIdOrderByCreatedAtDescIdDesc(Long brokerId);

	Optional<BrokerDocument> findByIdAndBrokerId(Long id, Long brokerId);

	/** 운영자 목록용. 회사까지 한 번에 읽는다 */
	@Query("select d from BrokerDocument d join fetch d.broker order by d.createdAt desc, d.id desc")
	List<BrokerDocument> findAllWithBroker();

	@Query("select d from BrokerDocument d join fetch d.broker where d.id = :id")
	Optional<BrokerDocument> findByIdWithBroker(@Param("id") Long id);
}
