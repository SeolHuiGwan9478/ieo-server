package com.ieo.server.domain.restarea.repository;

import com.ieo.server.domain.restarea.entity.RestArea;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestAreaRepository extends JpaRepository<RestArea, Long> {

	/** 지난 갱신 때 응답에 있던 휴게소 */
	List<RestArea> findAllByMissingSinceIsNull();
}
