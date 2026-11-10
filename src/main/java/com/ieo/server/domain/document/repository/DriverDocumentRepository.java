package com.ieo.server.domain.document.repository;

import com.ieo.server.domain.document.entity.DriverDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {

	/** 한 차주의 서류 전부. 최근 제출이 앞 */
	List<DriverDocument> findAllByDriverUserIdOrderByCreatedAtDescIdDesc(Long userId);

	Optional<DriverDocument> findByIdAndDriverUserId(Long id, Long userId);

	/**
	 * 심사 목록용. 차주와 계정을 함께 읽는다. 최근 제출이 앞.
	 * 서류 전체를 읽어 메모리에서 차주별로 묶는다. 차주가 수천 명이 되면 DB에서 묶는 쿼리로 바꾼다
	 */
	@Query("select d from DriverDocument d join fetch d.driver dr join fetch dr.user order by d.createdAt desc, d.id desc")
	List<DriverDocument> findAllWithDriver();

	@Query("select d from DriverDocument d join fetch d.driver dr join fetch dr.user where d.id = :id")
	Optional<DriverDocument> findByIdWithDriver(@Param("id") Long id);
}
