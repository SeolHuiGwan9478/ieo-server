package com.ieo.server.domain.trip.repository;

import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

	/** 확정된 요청으로 생긴 운행 (그 차주의 가장 최근 것) */
	Optional<Trip> findFirstByOrderIdAndDriverUserIdOrderByCreatedAtDesc(Long orderId, Long userId);

	/** 오더의 끝나지 않은 운행. 주선사가 배차를 취소할 때 */
	Optional<Trip> findFirstByOrderIdAndStatusIn(Long orderId, Collection<TripStatus> statuses);

	/** 차주 이행 기록: 완료한 운행 수 */
	long countByDriverUserIdAndStatus(Long userId, TripStatus status);

	/** 주선사 콘솔: 우리 회사 운행 (BRK-020). 출처로 Ieo 배차 운행만 고른다 */
	@EntityGraph(attributePaths = {"driver.user", "vehicle", "order"})
	Page<Trip> findAllByBrokerIdAndSourceAndStatusInOrderByCreatedAtDesc(
			Long brokerId, TripSource source, Collection<TripStatus> statuses, Pageable pageable);

	/** 주선사 신뢰 지표 표본: 기간 안에 끝난 운행 수 (BRK-032) */
	long countByBrokerIdAndSourceAndStatusAndCompletedAtGreaterThanEqual(
			Long brokerId, TripSource source, TripStatus status, Instant since);
}
