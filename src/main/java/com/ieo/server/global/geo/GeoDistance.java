package com.ieo.server.global.geo;

/** 짧은 거리(몇 km 안)를 평면으로 어림한다. "같은 자리인가"를 볼 때만 쓴다 */
public final class GeoDistance {

	private static final double METERS_PER_DEGREE = 111_320;

	private GeoDistance() {
	}

	public static double approxMeters(double lat1, double lng1, double lat2, double lng2) {
		double dy = (lat2 - lat1) * METERS_PER_DEGREE;
		double dx = (lng2 - lng1) * METERS_PER_DEGREE * Math.cos(Math.toRadians(lat1));
		return Math.hypot(dx, dy);
	}
}
