package com.ieo.server.domain.recommendation.service;

import com.ieo.server.domain.dispatch.entity.Order;
import com.ieo.server.domain.recommendation.service.TruckRouteEstimator.Leg;
import com.ieo.server.domain.recommendation.service.TruckRouteEstimator.TruckRoute;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 실수익 = 운임 − (공차 + 운송 구간 기름값) − (공차 + 운송 구간 통행료) (REC-010).
 * rough는 직선거리로 어림하고(통행료 모름), exact는 TMAP 경로로 계산한다. 경로를 못 받은 오더는 rough와 같다
 */
@Component
@RequiredArgsConstructor
public class ProfitEstimator {

	private final EmptyRunCost cost;
	private final TruckRouteEstimator routes;

	/** @param tollFare 경로를 받지 못했으면 null */
	public record Estimate(int netProfit, int fuelCost, Integer tollFare, long emptyMeters) {
	}

	/** 어디서 출발해 이 오더를 맡는지. address는 경유가를 읽을 시도 */
	public record Target(Order order, double fromLat, double fromLng, String address) {
	}

	public Estimate rough(Target target, Vehicle vehicle) {
		Site pickup = target.order().getPickupSite();
		Site dropoff = target.order().getDropoffSite();
		long empty = cost.roadMeters(pickup.distanceMeters(target.fromLat(), target.fromLng()));
		long loaded = cost.roadMeters(pickup.distanceMeters(dropoff.getLat(), dropoff.getLng()));
		int fuel = cost.fuelCost(empty + loaded, vehicle, target.address());
		return new Estimate(target.order().getFare() - fuel, fuel, null, empty);
	}

	/** 오더 id별 결과. 몇 건 안 되는 곳(추천 상위 5건, 상세)에서만 부른다 */
	public Map<Long, Estimate> exact(List<Target> targets, Vehicle vehicle) {
		List<Leg> legs = new ArrayList<>();
		for (Target target : targets) {
			legs.add(emptyLeg(target));
			legs.add(loadedLeg(target));
		}
		Map<Leg, TruckRoute> found = routes.routes(legs, vehicle);
		Map<Long, Estimate> estimates = new HashMap<>();
		for (Target target : targets) {
			TruckRoute empty = found.get(emptyLeg(target));
			TruckRoute loaded = found.get(loadedLeg(target));
			estimates.put(target.order().getId(), empty == null || loaded == null
					? rough(target, vehicle)
					: estimate(target, vehicle, empty, loaded));
		}
		return estimates;
	}

	private Estimate estimate(Target target, Vehicle vehicle, TruckRoute empty, TruckRoute loaded) {
		int fuel = cost.fuelCost(empty.meters() + loaded.meters(), vehicle, target.address());
		int toll = empty.toll() + loaded.toll();
		return new Estimate(target.order().getFare() - fuel - toll, fuel, toll, empty.meters());
	}

	private static Leg emptyLeg(Target target) {
		Site pickup = target.order().getPickupSite();
		return new Leg(target.fromLat(), target.fromLng(), pickup.getLat(), pickup.getLng());
	}

	private static Leg loadedLeg(Target target) {
		Site pickup = target.order().getPickupSite();
		Site dropoff = target.order().getDropoffSite();
		return new Leg(pickup.getLat(), pickup.getLng(), dropoff.getLat(), dropoff.getLng());
	}
}
