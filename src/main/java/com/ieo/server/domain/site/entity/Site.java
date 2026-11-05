package com.ieo.server.domain.site.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 상하차지 (SITE-001). 오더에 담기지 않는 현장 정보(높이 제한, 지게차, 메모)가 이 장소에 쌓인다.
 * 같은 곳이 두 번 생기지 않도록, 100m 안에 기존 상하차지가 있으면 서비스가 그것을 쓴다 (SITE-002).
 * 좌표는 PostGIS geography(Point, 4326) 한 칸에 둔다. 반경 찾기는 DB가 지구 타원체 거리로 한다 (SiteRepository).
 * GIST 인덱스는 scripts/sites-geography.sql 로 만든다.
 */
@Getter
@Entity
@Table(name = "sites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Site extends BaseTimeEntity {

	public static final int DEFAULT_GEOFENCE_RADIUS_M = 200;
	private static final int MIN_GEOFENCE_RADIUS_M = 50;
	private static final int MAX_GEOFENCE_RADIUS_M = 1000;
	private static final double EARTH_RADIUS_M = 6_371_000;
	private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	/** 도로명 주소 */
	@Column(nullable = false, length = 200)
	private String address;

	/** 위치. x가 경도, y가 위도 */
	@Column(nullable = false, columnDefinition = "geography(Point,4326)")
	private Point location;

	/** 이 반경에 들어오면 도착, 나가면 이탈로 본다 */
	@Column(nullable = false)
	private int geofenceRadiusM;

	/** 처음 등록한 차주. 탈퇴하면 비운다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private Driver createdBy;

	private Site(String name, String address, double lat, double lng, int geofenceRadiusM, Driver createdBy) {
		this.name = name;
		this.address = address;
		this.location = WGS84.createPoint(new Coordinate(lng, lat));
		this.geofenceRadiusM = geofenceRadiusM;
		this.createdBy = createdBy;
	}

	public static Site create(String name, String address, double lat, double lng, Driver createdBy) {
		return create(name, address, lat, lng, DEFAULT_GEOFENCE_RADIUS_M, createdBy);
	}

	public static Site create(String name, String address, double lat, double lng, int geofenceRadiusM, Driver createdBy) {
		validateLocation(lat, lng);
		if (geofenceRadiusM < MIN_GEOFENCE_RADIUS_M || geofenceRadiusM > MAX_GEOFENCE_RADIUS_M) {
			throw new BusinessException(ErrorCode.SITE_INVALID_RADIUS);
		}
		return new Site(requireText(name), requireText(address), lat, lng, geofenceRadiusM, createdBy);
	}

	public double getLat() {
		return location.getY();
	}

	public double getLng() {
		return location.getX();
	}

	/** 이 상하차지에서 (lat, lng)까지의 직선 거리(m). 화면에 보여 줄 거리용 하버사인 공식. 찾기는 DB가 한다 */
	public double distanceMeters(double lat, double lng) {
		double dLat = Math.toRadians(lat - getLat());
		double dLng = Math.toRadians(lng - getLng());
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(getLat())) * Math.cos(Math.toRadians(lat))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
	}

	public boolean isWithin(double lat, double lng, double meters) {
		return distanceMeters(lat, lng) <= meters;
	}

	/** 탈퇴한 차주와의 연결을 끊는다. 장소 정보는 다른 차주에게 쓸모 있으니 남긴다 */
	public void detachCreator() {
		this.createdBy = null;
	}

	public static void validateLocation(double lat, double lng) {
		if (!(lat >= -90 && lat <= 90) || !(lng >= -180 && lng <= 180)) {
			throw new BusinessException(ErrorCode.SITE_INVALID_LOCATION);
		}
	}

	private static String requireText(String text) {
		if (text == null || text.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return text.strip();
	}
}
