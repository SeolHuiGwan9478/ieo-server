package com.ieo.server.domain.trip.repository;

import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.TripStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

	/** 내 운행 목록. 최근 등록한 순서 */
	List<Trip> findAllByDriverUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	/** 지금 출발해서 진행 중인 운행. 차량 앱이 지오펜스를 걸 상하차지를 여기서 얻는다 */
	Optional<Trip> findFirstByDriverUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<TripStatus> statuses);

	boolean existsByDriverUserIdAndStatusIn(Long userId, Collection<TripStatus> statuses);

	/** 끝나지 않은 운행에 쓰는 차량은 지우지 못하게 막을 때 쓴다 */
	boolean existsByVehicleIdAndStatusIn(Long vehicleId, Collection<TripStatus> statuses);

	/** 운행 기록이 있는 차량은 삭제하면 기록이 깨진다 */
	boolean existsByVehicleId(Long vehicleId);

	boolean existsByOrderId(Long orderId);
}
