package com.ieo.server.domain.user.repository;

import com.ieo.server.domain.user.entity.CarSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarSessionRepository extends JpaRepository<CarSession, UUID> {

	boolean existsByIdAndRevokedAtIsNull(UUID id);

	Optional<CarSession> findByIdAndUserId(UUID id, Long userId);

	List<CarSession> findAllByUserIdAndRevokedAtIsNull(Long userId);

	List<CarSession> findAllByUserIdAndDeviceIdAndRevokedAtIsNull(Long userId, String deviceId);

	List<CarSession> findAllByUserIdAndRevokedAtIsNullAndLastUsedAtAfterOrderByLastUsedAtDesc(Long userId, Instant after);
}
