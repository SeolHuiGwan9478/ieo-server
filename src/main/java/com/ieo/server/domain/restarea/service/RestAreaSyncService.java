package com.ieo.server.domain.restarea.service;

import com.ieo.server.domain.restarea.entity.RestArea;
import com.ieo.server.domain.restarea.repository.RestAreaRepository;
import com.ieo.server.domain.restarea.service.ExpresswayRestAreaSource.ExpresswayRestArea;
import com.ieo.server.domain.restarea.service.RestAreaLocationSource.RestAreaLocation;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴게소 갱신 (TRIP-032). 도로공사 목록을 받아 휴게소 코드로 저장한 것과 맞춘다.
 * - 새 코드는 넣고, 해시가 다르면 고치고, 같으면 건드리지 않는다.
 * - 이번 응답에 없는 코드는 지우지 않고 사라진 때만 남긴다.
 * - 받은 수가 저장된 수의 {@link RestAreaProperties#minKeepRatio()}보다 적으면 API가 일부만 준 것으로 보고 아무것도 바꾸지 않는다.
 * 좌표·상하행은 표준데이터에서 이름("옥산(부산)")으로 찾아 붙인다. 표준데이터를 못 받았거나 이름이 없으면 저장된 좌표를 그대로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestAreaSyncService {

	private final ExpresswayRestAreaSource expressway;
	private final RestAreaLocationSource locations;
	private final RestAreaRepository repository;
	private final RestAreaProperties properties;
	private final Clock clock;

	@Transactional
	public SyncResult sync() {
		List<ExpresswayRestArea> received = expressway.fetchAll();
		int active = repository.findAllByMissingSinceIsNull().size();
		if (received.isEmpty() || received.size() < active * properties.minKeepRatio()) {
			log.warn("휴게소 갱신 멈춤: 받은 {}곳, 저장된 {}곳", received.size(), active);
			return SyncResult.skipped(received.size(), active);
		}
		Map<String, RestAreaLocation> byName = index(locations.fetchAll());
		Map<String, RestArea> saved = repository.findAll().stream()
				.collect(Collectors.toMap(RestArea::getCode, Function.identity()));
		Instant now = clock.instant();
		Set<String> seen = new HashSet<>();
		int added = 0;
		int updated = 0;
		int withoutLocation = 0;
		for (ExpresswayRestArea row : received) {
			if (!seen.add(row.code())) {
				continue;
			}
			RestArea area = saved.get(row.code());
			RestAreaSnapshot snapshot = snapshot(row, byName.get(key(row.name())), area);
			if (!snapshot.hasLocation()) {
				withoutLocation++;
			}
			if (area == null) {
				repository.save(RestArea.create(snapshot, now));
				added++;
			} else if (area.apply(snapshot, now)) {
				updated++;
			}
		}
		int missing = 0;
		for (RestArea area : saved.values()) {
			if (!seen.contains(area.getCode())) {
				if (area.getMissingSince() == null) {
					missing++;
				}
				area.markMissing(now);
			}
		}
		SyncResult result = new SyncResult(false, received.size(), added, updated, seen.size() - added - updated, missing,
				withoutLocation, byName.size());
		log.info("휴게소 갱신: {}", result);
		return result;
	}

	/** 표준데이터에 없으면 저장된 좌표·상하행을 그대로 쓴다 */
	private static RestAreaSnapshot snapshot(ExpresswayRestArea row, RestAreaLocation location, RestArea saved) {
		String roadDirection = location != null ? location.roadDirection() : saved == null ? null : saved.getRoadDirection();
		// 삼항식에서 double과 null이 섞이면 null을 풀다 멈추므로 Double로 맞춘다
		Double latitude = location != null ? Double.valueOf(location.latitude()) : saved == null ? null : saved.latitude();
		Double longitude = location != null ? Double.valueOf(location.longitude()) : saved == null ? null : saved.longitude();
		return new RestAreaSnapshot(row.code(), row.name(), row.routeName(), row.direction(), roadDirection,
				row.facilities(), row.truckRestArea(), row.phone(), row.address(), latitude, longitude);
	}

	private static Map<String, RestAreaLocation> index(List<RestAreaLocation> rows) {
		Map<String, RestAreaLocation> byName = new HashMap<>();
		for (RestAreaLocation row : rows) {
			byName.putIfAbsent(key(row.name()), row);
		}
		return byName;
	}

	/** "옥산(부산)휴게소"와 "옥산(부산)"을 같게 본다 */
	static String key(String name) {
		return name == null ? "" : name.replace("휴게소", "").replace(" ", "");
	}

	/**
	 * @param skipped         받은 수가 너무 적어 아무것도 바꾸지 않았다
	 * @param received        도로공사에서 받은 수
	 * @param unchanged       해시가 같아 그대로 둔 수
	 * @param missing         이번에 새로 사라진 수
	 * @param withoutLocation 좌표가 없는 수
	 * @param locations       표준데이터에서 받은 휴게소 수 (0이면 좌표를 못 받은 것)
	 */
	public record SyncResult(boolean skipped, int received, int added, int updated, int unchanged, int missing,
			int withoutLocation, int locations) {

		static SyncResult skipped(int received, int active) {
			return new SyncResult(true, received, 0, 0, active, 0, 0, 0);
		}
	}
}
