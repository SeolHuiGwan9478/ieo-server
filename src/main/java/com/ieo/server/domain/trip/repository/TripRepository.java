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

	/** 노선 평균 운임용 (DSP-044): Ieo 배차로 생겨 since 뒤에 끝난 운행. 오더·상하차지를 함께 읽는다 */
	@org.springframework.data.jpa.repository.Query("""
			select t from Trip t join fetch t.order o join fetch o.pickupSite join fetch o.dropoffSite
			where t.source = com.ieo.server.domain.trip.enums.TripSource.IEO_DISPATCH
			  and t.status = com.ieo.server.domain.trip.enums.TripStatus.COMPLETED
			  and t.completedAt > :since
			""")
	List<Trip> findCompletedDispatchSince(@org.springframework.data.repository.query.Param("since") Instant since);

	/** 내 운행 목록. 최근 등록한 순서 */
	List<Trip> findAllByDriverUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	/** 지금 출발해서 진행 중인 운행. 차량 앱이 지오펜스를 걸 상하차지를 여기서 얻는다 */
	Optional<Trip> findFirstByDriverUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<TripStatus> statuses);

	boolean existsByDriverUserIdAndStatusIn(Long userId, Collection<TripStatus> statuses);

	/** 끝나지 않은 운행에 쓰는 차량은 지우지 못하게 막을 때 쓴다 */
	boolean existsByVehicleIdAndStatusIn(Long vehicleId, Collection<TripStatus> statuses);

	boolean existsByOrderId(Long orderId);

	/** 확정된 요청으로 생긴 운행 (그 차주의 가장 최근 것) */
	Optional<Trip> findFirstByOrderIdAndDriverUserIdOrderByCreatedAtDesc(Long orderId, Long userId);

	/** 오더의 끝나지 않은 운행. 주선사가 배차를 취소할 때 */
	/** 이 기사님이 이 회사와 끝낸 운행 수 (단골 요청 화면) */
	long countByDriverUserIdAndBrokerIdAndStatus(Long driverUserId, Long brokerId, TripStatus status);

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

	/** 리포트: 기간 안에 끝난 Ieo 배차 운행, 모든 회사 (BRK-040 노선별 운임·소요 시간) */
	@EntityGraph(attributePaths = {"stops", "stops.site"})
	List<Trip> findAllBySourceAndStatusAndCompletedAtBetween(TripSource source, TripStatus status, Instant from, Instant to);

	/** 리포트: 우리 회사 운행 중 기간 안에 배차된 것, 상태 무관 (BRK-042, 043) */
	@EntityGraph(attributePaths = {"driver.user", "vehicle", "order", "stops", "stops.site"})
	List<Trip> findAllByBrokerIdAndSourceAndCreatedAtBetween(Long brokerId, TripSource source, Instant from, Instant to);

	/** 대시보드: 우리 회사 오더로 배차돼 기간 안에 완료된 운행 (지급·청구 운임, 수수료, 대기비 합계) */
	@EntityGraph(attributePaths = "order")
	List<Trip> findAllByBrokerIdAndSourceAndStatusAndCompletedAtBetween(Long brokerId, TripSource source,
			TripStatus status, Instant from, Instant to);

	/** 대시보드: 기간 안에 취소된 운행 */
	List<Trip> findAllByBrokerIdAndSourceAndStatusAndCanceledAtBetween(Long brokerId, TripSource source,
			TripStatus status, Instant from, Instant to);
}
