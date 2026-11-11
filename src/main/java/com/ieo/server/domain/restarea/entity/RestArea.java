package com.ieo.server.domain.restarea.entity;

import com.ieo.server.domain.restarea.service.RestAreaSnapshot;
import com.ieo.server.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * 고속도로 휴게소 (TRIP-032). 한국도로공사 휴게소 API(편의시설·화물 휴게소)와
 * 공공데이터포털 전국휴게소정보표준데이터(좌표·상하행)를 합쳐 일주일에 한 번 갱신한다 ({@code RestAreaSyncService}).
 * 같은 휴게소인지는 도로공사 휴게소 코드로, 바뀌었는지는 {@link #contentHash}로 가른다.
 * 응답에서 사라지면 지우지 않고 {@link #missingSince}만 채운다.
 */
@Getter
@Entity
@Table(name = "rest_areas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestArea extends BaseTimeEntity {

	private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
	private static final String FACILITY_SEPARATOR = "|";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 도로공사 휴게소 코드 (serviceAreaCode, 예: A00032) */
	@Column(nullable = false, unique = true, length = 20)
	private String code;

	/** 도로공사 휴게소 이름 (예: 옥산(부산)휴게소) */
	@Column(nullable = false, length = 100)
	private String name;

	/** 노선 (예: 경부선) */
	@Column(length = 50)
	private String routeName;

	/** 가는 쪽 도시 (예: 부산). 도로공사 값 */
	@Column(length = 30)
	private String direction;

	/** 상행·하행. 표준데이터 값 */
	@Column(length = 10)
	private String roadDirection;

	/** 편의시설을 |로 이은 글 (예: 수유실|수면실|샤워실|세탁실) */
	@Column(nullable = false, length = 200)
	private String facilities;

	/** 도로공사가 화물 휴게소로 표시한 곳 */
	@Column(nullable = false)
	private boolean truckRestArea;

	@Column(length = 30)
	private String phone;

	@Column(length = 200)
	private String address;

	/** 표준데이터 좌표. x가 경도, y가 위도. 이름이 맞는 표준데이터가 없으면 비어 있다 */
	@Column(columnDefinition = "geography(Point,4326)")
	private Point location;

	/** 좌표를 사람이 직접 넣었다. 갱신 때 덮어쓰지 않는다 */
	@Column(nullable = false)
	private boolean locationManual;

	/** 저장한 내용의 SHA-256. 갱신 때 이것만 비교한다 */
	@Column(nullable = false, length = 64)
	private String contentHash;

	/** 마지막으로 응답에서 본 때 */
	@Column(nullable = false)
	private Instant lastSeenAt;

	/** 응답에서 처음 사라진 때. 다시 보이면 비운다. 채워져 있으면 안내에 쓰지 않는다 */
	private Instant missingSince;

	private RestArea(String code) {
		this.code = code;
	}

	public static RestArea create(RestAreaSnapshot snapshot, Instant now) {
		RestArea area = new RestArea(snapshot.code());
		area.apply(snapshot, now);
		return area;
	}

	/** 응답에서 본 내용으로 바꾼다. 내용이 바뀌었으면 true. 직접 넣은 좌표는 그대로 둔다 */
	public boolean apply(RestAreaSnapshot snapshot, Instant now) {
		this.lastSeenAt = now;
		this.missingSince = null;
		RestAreaSnapshot merged = locationManual ? snapshot.withLocation(latitude(), longitude()) : snapshot;
		String hash = merged.hash();
		if (hash.equals(contentHash)) {
			return false;
		}
		this.name = merged.name();
		this.routeName = merged.routeName();
		this.direction = merged.direction();
		this.roadDirection = merged.roadDirection();
		this.facilities = String.join(FACILITY_SEPARATOR, merged.facilities());
		this.truckRestArea = merged.truckRestArea();
		this.phone = merged.phone();
		this.address = merged.address();
		this.location = merged.hasLocation() ? point(merged.latitude(), merged.longitude()) : this.location;
		this.contentHash = hash;
		return true;
	}

	/** 이번 응답에 없었다. 처음 사라진 때만 남긴다 */
	public void markMissing(Instant now) {
		if (missingSince == null) {
			missingSince = now;
		}
	}

	/** 표준데이터 좌표가 틀린 곳을 직접 고친다 */
	public void fixLocation(double latitude, double longitude) {
		this.location = point(latitude, longitude);
		this.locationManual = true;
	}

	public List<String> facilityList() {
		return facilities.isEmpty() ? List.of() : Arrays.asList(facilities.split("\\" + FACILITY_SEPARATOR));
	}

	/** 수면실·샤워실·세탁실이 모두 있다. 화물차 라운지가 있는 휴게소와 거의 같다 */
	public boolean hasTruckLounge() {
		List<String> list = facilityList();
		return list.contains("수면실") && list.contains("샤워실") && list.contains("세탁실");
	}

	public Double latitude() {
		return location == null ? null : location.getY();
	}

	public Double longitude() {
		return location == null ? null : location.getX();
	}

	private static Point point(double latitude, double longitude) {
		return WGS84.createPoint(new Coordinate(longitude, latitude));
	}
}
