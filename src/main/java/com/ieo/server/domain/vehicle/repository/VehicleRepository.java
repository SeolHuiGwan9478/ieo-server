package com.ieo.server.domain.vehicle.repository;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	/** 내 차량 목록. 먼저 등록한 순서 */
	List<Vehicle> findAllByDriverUserIdOrderByCreatedAtAsc(Long userId);

	Optional<Vehicle> findByDriverUserIdAndDefaultVehicleTrue(Long userId);

	long countByDriverUserId(Long userId);

	boolean existsByPlateNumber(String plateNumber);

	/** QR 로그인한 Pleos 차량이 어떤 차량인지 찾는다 (VEH-004) */
	Optional<Vehicle> findByVinHash(String vinHash);
}
