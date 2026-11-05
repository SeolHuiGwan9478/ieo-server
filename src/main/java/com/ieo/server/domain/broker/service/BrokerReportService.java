package com.ieo.server.domain.broker.service;

import com.ieo.server.domain.broker.dto.report.CompanyReportResponse;
import com.ieo.server.domain.broker.dto.report.DriverReportResponse;
import com.ieo.server.domain.broker.dto.report.ReportPeriod;
import com.ieo.server.domain.broker.dto.report.RouteReportResponse;
import com.ieo.server.domain.broker.dto.report.SiteReportResponse;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.enums.BrokerFeature;
import com.ieo.server.domain.broker.enums.DriverLinkStatus;
import com.ieo.server.domain.broker.repository.BrokerDriverLinkRepository;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.dispatch.entity.Cancellation;
import com.ieo.server.domain.dispatch.repository.CancellationRepository;
import com.ieo.server.domain.dispatch.repository.OrderRepository;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.entity.TripStop;
import com.ieo.server.domain.trip.enums.TripSource;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.wait.entity.WaitRecord;
import com.ieo.server.domain.wait.repository.WaitRecordRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.KoreanDate;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주선사 운행 데이터 리포트 (BRK-040~044). 담당자 누구나, 단 플랜이 PRO 이상이어야 한다 (BASIC은 403 PLAN_403_UPGRADE).
 *
 * 캐시 없이 조회할 때 계산한다. 리포트는 가끔 열고 기간이 매번 달라서 미리 계산해 둘 수 없다.
 * 노선·상하차지는 모든 회사의 Ieo 배차 운행으로 평균을 내고(그래야 값이 된다), 내 회사·차주 리포트는 우리 회사 것만 본다.
 * 직접 등록한 운행(MANUAL)은 어디에도 넣지 않는다 (아무 회사나 고를 수 있어서).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerReportService {

	/** 상차지 예정 시각에서 이만큼 늦게까지는 정시로 본다 (상하차 가능 시간에 보통 폭이 있다) */
	static final Duration ON_TIME_GRACE = Duration.ofMinutes(30);

	private final BrokerMemberRepository memberRepository;
	private final BrokerDriverLinkRepository linkRepository;
	private final TripRepository tripRepository;
	private final OrderRepository orderRepository;
	private final CancellationRepository cancellationRepository;
	private final WaitRecordRepository waitRecordRepository;
	private final BrokerPlanService planService;
	private final Clock clock;

	/** 노선·상하차지를 보여 주는 최소 건수 (BRK-044는 10. 데이터가 적은 동안 설정으로 낮춘다) */
	@Value("${ieo.report.min-samples:10}")
	private int minSamples;

	/** 노선별 운임·소요 시간 (BRK-040). 노선 = 상차지 시·군·구 → 하차지 시·군·구 */
	public RouteReportResponse routes(Long userId, LocalDate from, LocalDate to) {
		requireActiveMember(userId, BrokerFeature.REPORTS);
		ReportPeriod period = ReportPeriod.of(from, to, clock);
		List<Trip> trips = tripRepository.findAllBySourceAndStatusAndCompletedAtBetween(
				TripSource.IEO_DISPATCH, TripStatus.COMPLETED, period.start(), period.end());
		record RouteKey(String pickup, String dropoff) {
		}
		Map<RouteKey, List<Trip>> byRoute = trips.stream().collect(Collectors.groupingBy(
				trip -> new RouteKey(area(trip.pickup().getSite()), area(trip.dropoff().getSite()))));
		List<RouteReportResponse.Route> routes = byRoute.entrySet().stream()
				.filter(entry -> entry.getValue().size() >= minSamples)
				.map(entry -> route(entry.getKey().pickup(), entry.getKey().dropoff(), entry.getValue()))
				.sorted(Comparator.comparingInt(RouteReportResponse.Route::tripCount).reversed()
						.thenComparing(RouteReportResponse.Route::pickupArea))
				.toList();
		return new RouteReportResponse(period.from(), period.to(), minSamples, routes);
	}

	private static RouteReportResponse.Route route(String pickup, String dropoff, List<Trip> trips) {
		int[] fares = trips.stream().mapToInt(Trip::getFare).sorted().toArray();
		long[] durations = trips.stream()
				.filter(trip -> trip.getStartedAt() != null)
				.mapToLong(trip -> Duration.between(trip.getStartedAt(), trip.getCompletedAt()).toMinutes())
				.toArray();
		Map<YearMonth, List<Trip>> byMonth = trips.stream().collect(Collectors.groupingBy(
				trip -> YearMonth.from(KoreanDate.of(trip.getCompletedAt())), TreeMap::new, Collectors.toList()));
		List<RouteReportResponse.Month> months = byMonth.entrySet().stream()
				.map(entry -> new RouteReportResponse.Month(entry.getKey().toString(), entry.getValue().size(),
						(int) Math.round(entry.getValue().stream().mapToInt(Trip::getFare).average().orElse(0))))
				.toList();
		return new RouteReportResponse.Route(pickup, dropoff, trips.size(),
				(int) Math.round(Arrays.stream(fares).average().orElse(0)), median(fares), fares[0], fares[fares.length - 1],
				durations.length == 0 ? null : (int) Math.round(Arrays.stream(durations).average().orElse(0)), months);
	}

	/**
	 * 주소의 앞 두 단계 = 시·도 + 시·군·구. "경기 평택시 포승읍 …" → "경기 평택시", "서울특별시 강남구 …" → "서울 강남구".
	 * 상하차지 id로 묶으면 같은 공단의 옆 창고가 다른 노선이 돼서 건수가 안 모인다. 행정구역 코드가 생기면 바꾼다
	 */
	static String area(Site site) {
		String[] tokens = site.getAddress().strip().split("\\s+");
		String province = tokens[0];
		for (String suffix : new String[] {"특별자치도", "특별자치시", "특별시", "광역시", "도"}) {
			if (province.length() > 2 && province.endsWith(suffix)) {
				province = province.substring(0, province.length() - suffix.length());
				break;
			}
		}
		return tokens.length > 1 ? province + " " + tokens[1] : province;
	}

	/** 우리 회사 운행이 들른 상하차지의 대기·작업 시간 (BRK-041). 평균은 그곳의 모든 회사 기록으로 */
	public SiteReportResponse sites(Long userId, LocalDate from, LocalDate to) {
		BrokerMember member = requireActiveMember(userId, BrokerFeature.REPORTS);
		ReportPeriod period = ReportPeriod.of(from, to, clock);
		Set<Long> siteIds = ourTrips(member, period).stream()
				.flatMap(trip -> trip.getStops().stream())
				.map(stop -> stop.getSite().getId())
				.collect(Collectors.toSet());
		if (siteIds.isEmpty()) {
			return new SiteReportResponse(period.from(), period.to(), minSamples, List.of());
		}
		Map<Site, List<WaitRecord>> bySite = waitRecordRepository
				.findAllForSiteReport(siteIds, TripSource.IEO_DISPATCH, period.start(), period.end()).stream()
				.filter(record -> record.waitMinutes() != null)
				.collect(Collectors.groupingBy(record -> record.getTripStop().getSite()));
		List<SiteReportResponse.SiteRow> rows = bySite.entrySet().stream()
				.filter(entry -> entry.getValue().size() >= minSamples)
				.map(entry -> {
					Site site = entry.getKey();
					long[] waits = entry.getValue().stream().mapToLong(WaitRecord::waitMinutes).toArray();
					long[] works = entry.getValue().stream().map(WaitRecord::workMinutes).filter(Objects::nonNull)
							.mapToLong(Long::longValue).toArray();
					return new SiteReportResponse.SiteRow(site.getId(), site.getName(), site.getAddress(), waits.length,
							(int) Math.round(Arrays.stream(waits).average().orElse(0)), (int) Arrays.stream(waits).max().orElse(0),
							works.length == 0 ? null : (int) Math.round(Arrays.stream(works).average().orElse(0)));
				})
				.sorted(Comparator.comparingInt(SiteReportResponse.SiteRow::recordCount).reversed()
						.thenComparing(SiteReportResponse.SiteRow::name))
				.toList();
		return new SiteReportResponse(period.from(), period.to(), minSamples, rows);
	}

	/** 내 회사: 오더·배차·완료·취소 건수, 평균 배차 확정 시간, 차량별 이행률, 대기비 청구 가능액 (BRK-042) */
	public CompanyReportResponse company(Long userId, LocalDate from, LocalDate to) {
		BrokerMember member = requireActiveMember(userId, BrokerFeature.REPORTS);
		ReportPeriod period = ReportPeriod.of(from, to, clock);
		List<Trip> trips = ourTrips(member, period);
		long orderCount = orderRepository.countByBrokerIdAndCreatedAtBetween(
				member.getBroker().getId(), period.start(), period.end());
		Map<Long, Cancellation> cancellationByTrip = cancellations(trips);

		int canceledByBroker = (int) cancellationByTrip.values().stream()
				.filter(cancellation -> cancellation.getCanceledBy().getRole() == UserRole.BROKER).count();
		int canceled = (int) trips.stream().filter(trip -> trip.getStatus() == TripStatus.CANCELLED).count();
		long[] confirmMinutes = trips.stream()
				.filter(trip -> trip.getOrder() != null)
				.mapToLong(trip -> Duration.between(trip.getOrder().getCreatedAt(), trip.getCreatedAt()).toMinutes())
				.toArray();
		List<Long> completedIds = trips.stream()
				.filter(trip -> trip.getStatus() == TripStatus.COMPLETED).map(Trip::getId).toList();
		long waitFeeTotal = completedIds.isEmpty() ? 0
				: waitRecordRepository.findAllByTripStopTripIdIn(completedIds).stream()
						.map(WaitRecord::chargeableAmount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();

		List<CompanyReportResponse.VehicleRow> vehicles = trips.stream()
				.collect(Collectors.groupingBy(trip -> trip.getVehicle().getId())).values().stream()
				.map(group -> {
					Trip first = group.getFirst();
					int completed = (int) group.stream().filter(trip -> trip.getStatus() == TripStatus.COMPLETED).count();
					int cancelled = (int) group.stream().filter(trip -> trip.getStatus() == TripStatus.CANCELLED).count();
					return new CompanyReportResponse.VehicleRow(first.getVehicle().getPlateNumber(),
							first.getDriver().getUser().getName(), group.size(), completed, cancelled,
							percent(completed, completed + cancelled));
				})
				.sorted(Comparator.comparingInt(CompanyReportResponse.VehicleRow::dispatched).reversed()
						.thenComparing(CompanyReportResponse.VehicleRow::plateNumber))
				.toList();

		return new CompanyReportResponse(period.from(), period.to(), orderCount, trips.size(),
				completedIds.size(), canceled, canceledByBroker, canceled - canceledByBroker,
				confirmMinutes.length == 0 ? null : (int) Math.round(Arrays.stream(confirmMinutes).average().orElse(0)),
				waitFeeTotal, vehicles);
	}

	/** 함께 일한 차주의 이행 기록 (BRK-043): 완료·취소·늦은 취소·정시 도착 */
	public DriverReportResponse drivers(Long userId, LocalDate from, LocalDate to) {
		BrokerMember member = requireActiveMember(userId, BrokerFeature.DRIVER_RECORDS);
		ReportPeriod period = ReportPeriod.of(from, to, clock);
		List<Trip> trips = ourTrips(member, period);
		Map<Long, Cancellation> cancellationByTrip = cancellations(trips);
		Set<Long> linkedIds = linkRepository
				.findAllByBrokerIdAndStatusOrderByLinkedAtDesc(member.getBroker().getId(), DriverLinkStatus.LINKED).stream()
				.map(link -> link.getDriver().getUserId())
				.collect(Collectors.toSet());
		Map<Long, WaitRecord> pickupRecordByTrip = trips.isEmpty() ? Map.of()
				: waitRecordRepository.findAllByTripStopTripIdIn(trips.stream().map(Trip::getId).toList()).stream()
						.filter(record -> record.getTripStop().isPickup())
						.collect(Collectors.toMap(record -> record.getTripStop().getTrip().getId(), Function.identity()));

		List<DriverReportResponse.DriverRow> rows = trips.stream()
				.collect(Collectors.groupingBy(trip -> trip.getDriver().getUserId())).entrySet().stream()
				.map(entry -> {
					Long driverId = entry.getKey();
					List<Trip> group = entry.getValue();
					User user = group.getFirst().getDriver().getUser();
					boolean linked = linkedIds.contains(driverId);
					List<Cancellation> byDriver = group.stream()
							.map(trip -> cancellationByTrip.get(trip.getId()))
							.filter(cancellation -> cancellation != null && cancellation.getCanceledBy().getId().equals(driverId))
							.toList();
					int onTimeBase = 0;
					int onTime = 0;
					for (Trip trip : group) {
						WaitRecord record = pickupRecordByTrip.get(trip.getId());
						if (trip.getStatus() != TripStatus.COMPLETED || record == null || trip.pickup().getPlannedAt() == null) {
							continue;
						}
						onTimeBase++;
						if (!record.getArrivedAt().isAfter(trip.pickup().getPlannedAt().plus(ON_TIME_GRACE))) {
							onTime++;
						}
					}
					return new DriverReportResponse.DriverRow(driverId, user.getName(), linked ? user.getPhone() : null, linked,
							group.size(), (int) group.stream().filter(trip -> trip.getStatus() == TripStatus.COMPLETED).count(),
							byDriver.size(), (int) byDriver.stream().filter(Cancellation::isLate).count(),
							onTime, onTimeBase, percent(onTime, onTimeBase));
				})
				.sorted(Comparator.comparingInt(DriverReportResponse.DriverRow::dispatched).reversed()
						.thenComparing(DriverReportResponse.DriverRow::name))
				.toList();
		return new DriverReportResponse(period.from(), period.to(), rows);
	}

	/** 기간 안에 우리 회사 오더로 배차된 운행 */
	private List<Trip> ourTrips(BrokerMember member, ReportPeriod period) {
		return tripRepository.findAllByBrokerIdAndSourceAndCreatedAtBetween(
				member.getBroker().getId(), TripSource.IEO_DISPATCH, period.start(), period.end());
	}

	private Map<Long, Cancellation> cancellations(List<Trip> trips) {
		if (trips.isEmpty()) {
			return Map.of();
		}
		return cancellationRepository.findAllByTripIdIn(trips.stream().map(Trip::getId).toList()).stream()
				.collect(Collectors.toMap(cancellation -> cancellation.getTrip().getId(), Function.identity()));
	}

	private static int median(int[] sorted) {
		int mid = sorted.length / 2;
		return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
	}

	private static Integer percent(int count, int total) {
		return total == 0 ? null : Math.round(count * 100f / total);
	}

	/** 소속·활성 확인 뒤 플랜에 그 기능이 있는지 본다 */
	private BrokerMember requireActiveMember(Long userId, BrokerFeature feature) {
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NO_MEMBERSHIP));
		if (member.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		planService.requireFeature(member.getBroker().getId(), feature);
		return member;
	}
}
