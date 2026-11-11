package com.ieo.server.domain.recommendation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * TMAP 화물차 경로안내로 거리·시간·통행료를 받는다.
 * 추천 응답 안에서 부르므로 동시에 부르고, 실패하면 잠시 부르지 않는다. 결과는 약관에 맞춰 24시간 안에서만 들고 있는다
 */
@Slf4j
@Component
public class TmapTruckRouteEstimator implements TruckRouteEstimator {

	static final Duration CACHE_TTL = Duration.ofHours(23);
	private static final Duration COOLDOWN = Duration.ofMinutes(1);
	private static final long WAIT_SECONDS = 8;
	private static final int MAX_CACHE = 5_000;

	private final TmapProperties properties;
	private final Clock clock;
	private final RestClient client;
	private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
	private volatile Instant coolDownUntil = Instant.MIN;

	public TmapTruckRouteEstimator(TmapProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(6));
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
	}

	@Override
	public Map<Leg, TruckRoute> routes(Collection<Leg> legs, Vehicle vehicle) {
		if (properties.appKey() == null || properties.appKey().isBlank()) {
			return Map.of();
		}
		TruckSpec spec = TruckSpec.of(vehicle);
		Map<Leg, TruckRoute> found = new HashMap<>();
		List<Leg> missing = legs.stream().distinct().filter(leg -> !takeFromCache(leg, spec, found)).toList();
		if (missing.isEmpty() || clock.instant().isBefore(coolDownUntil)) {
			return found;
		}
		fetchAll(missing, spec, found);
		return found;
	}

	private boolean takeFromCache(Leg leg, TruckSpec spec, Map<Leg, TruckRoute> found) {
		Cached cached = cache.get(new Key(leg, spec));
		if (cached == null || !clock.instant().isBefore(cached.expiresAt())) {
			return false;
		}
		found.put(leg, cached.route());
		return true;
	}

	private void fetchAll(List<Leg> legs, TruckSpec spec, Map<Leg, TruckRoute> found) {
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Callable<TruckRoute>> calls = legs.stream().<Callable<TruckRoute>>map(leg -> () -> fetch(leg, spec)).toList();
			List<Future<TruckRoute>> results = pool.invokeAll(calls, WAIT_SECONDS, TimeUnit.SECONDS);
			for (int i = 0; i < legs.size(); i++) {
				remember(legs.get(i), spec, results.get(i), found);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void remember(Leg leg, TruckSpec spec, Future<TruckRoute> result, Map<Leg, TruckRoute> found) {
		try {
			TruckRoute route = result.resultNow();
			found.put(leg, route);
			if (cache.size() >= MAX_CACHE) {
				cache.clear();
			}
			cache.put(new Key(leg, spec), new Cached(route, clock.instant().plus(CACHE_TTL)));
		} catch (IllegalStateException e) {
			coolDownUntil = clock.instant().plus(COOLDOWN);
			log.warn("TMAP 경로를 받지 못함");
		}
	}

	private TruckRoute fetch(Leg leg, TruckSpec spec) {
		Response body = client.post()
				.uri(uri -> uri.path("/tmap/truck/routes").queryParam("version", "1").build())
				.header("appKey", properties.appKey())
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(spec.routeRequest(leg.fromLat(), leg.fromLng(), leg.toLat(), leg.toLng(), 2))
				.retrieve()
				.body(Response.class);
		Summary summary = body.features().getFirst().properties();
		return new TruckRoute(summary.totalDistance(), summary.totalTime(), summary.totalFare());
	}

	private record Key(Leg leg, TruckSpec spec) {
	}

	private record Cached(TruckRoute route, Instant expiresAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Response(List<Feature> features) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Feature(Summary properties) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Summary(long totalDistance, long totalTime, int totalFare) {
	}
}
