package com.ieo.server.domain.user.repository;

import com.ieo.server.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByPhone(String phone);
}
