package com.ieo.server.domain.driver.repository;

import com.ieo.server.domain.driver.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

/** PK가 users.id와 같으므로 findById(userId)로 차주 프로필을 찾는다. */
public interface DriverRepository extends JpaRepository<Driver, Long> {
}
