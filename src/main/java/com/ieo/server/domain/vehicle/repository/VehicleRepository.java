package com.ieo.server.domain.vehicle.repository;

import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	/** 내 차량 목록. 먼저 등록한 순서 */
	List<Vehicle> findAllByDriverUserIdOrderByCreatedAtAsc(Long userId);

	Optional<Vehicle> findByDriverUserIdAndDefaultVehicleTrue(Long userId);

	/** 여러 차주의 차량을 한 번에. 운영자 심사 목록이 차량 번호를 보여 줄 때 쓴다 */
	List<Vehicle> findAllByDriverUserIdInOrderByCreatedAtAsc(Collection<Long> userIds);

	long countByDriverUserId(Long userId);

	boolean existsByPlateNumber(String plateNumber);

	/** QR 로그인한 Pleos 차량이 어떤 차량인지 찾는다 (VEH-004) */
	Optional<Vehicle> findByVinHash(String vinHash);
}
