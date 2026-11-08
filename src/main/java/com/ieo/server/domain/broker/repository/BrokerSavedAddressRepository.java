package com.ieo.server.domain.broker.repository;

import com.ieo.server.domain.broker.entity.BrokerSavedAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerSavedAddressRepository extends JpaRepository<BrokerSavedAddress, Long> {
    List<BrokerSavedAddress> findAllByBrokerIdOrderByIdDesc(Long brokerId);
    Optional<BrokerSavedAddress> findByIdAndBrokerId(Long id, Long brokerId);
    boolean existsByBrokerIdAndAddressAndDetailAddress(Long brokerId, String address, String detailAddress);
}
