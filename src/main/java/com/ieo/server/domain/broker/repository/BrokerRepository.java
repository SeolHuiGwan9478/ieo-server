package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.Broker;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerRepository extends JpaRepository<Broker, Long> {

	Optional<Broker> findByBusinessNumber(String businessNumber);

	boolean existsByBusinessNumber(String businessNumber);
}
