package com.ieo.server.domain.vehicle.entity;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.vehicle.enums.BodyType;
import com.ieo.server.domain.vehicle.enums.FuelEconomySource;
import com.ieo.server.domain.vehicle.enums.Tonnage;
import com.ieo.server.global.entity.BaseTimeEntity;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 차주의 차량 (VEH-001). 한 차주가 여러 대를 가질 수 있고, 그중 하나가 기본 차량이다.
 * 기본 차량이 차주당 하나인지는 서비스가 지킨다.
 */
@Getter
@Entity
@Table(name = "vehicles", indexes = @Index(name = "idx_vehicles_driver", columnList = "driver_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends BaseTimeEntity {

	/**
	 * 자동차 등록번호 (자동차 등록번호판 등의 기준에 관한 고시). 예: 12가3456, 123가4567, 경기82사1234
	 * - 숫자 2~3자리 + 용도 글자 + 숫자 4자리. 사업용은 지역명 + 숫자 2자리로 시작할 수 있다
	 * - 용도 글자: 비사업용 가~마·거~저·고~조·구~주(32자), 사업용 아·바·사·자, 렌터카 하·허·호, 택배 배
	 * 휴대폰 앱 PlateNumber와 같은 규칙이다
	 */
	private static final Pattern PLATE_NUMBER = Pattern.compile(
			"^(?:(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)\\d{2}|\\d{2,3})"
					+ "[가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아바사자하허호배]\\d{4}$");
	private static final BigDecimal MIN_HEIGHT_M = new BigDecimal("1.50");
	private static final BigDecimal MAX_HEIGHT_M = new BigDecimal("4.20");
	private static final BigDecimal HUNDRED_KM = new BigDecimal("100");
	/** 화물차 연비로 말이 되는 범위(km/L) */
	private static final BigDecimal MIN_KM_PER_LITER = new BigDecimal("1.5");
	private static final BigDecimal MAX_KM_PER_LITER = new BigDecimal("15");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id")
	private Driver driver;

	/** 띄어쓰기 없이 저장한다. 차량 한 대에 차주 한 명이라 전체에서 겹치지 않는다 (VEH-005) */
	@Column(nullable = false, unique = true, length = 20)
	private String plateNumber;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Tonnage tonnage;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private BodyType bodyType;

	/** 적재함 포함 높이. 비어 있으면 높이 제한 경고를 하지 않는다 (VEH-002) */
	@Column(precision = 3, scale = 2)
	private BigDecimal heightM;

	/** 트럭 폭·길이(cm)와 총중량(kg). 선택 입력이고 비어 있으면 톤수별 기본값으로 경로를 찾는다 */
	private Integer widthCm;

	private Integer lengthCm;

	private Integer totalWeightKg;

	/** 축 수. 선택 입력이고 고속도로 통행료 차종을 정하는 데 쓴다 */
	private Integer axleCount;

	/** Pleos 누적 연비를 km/L로 바꿔 저장한다 (VEH-003) */
	@Column(precision = 4, scale = 2)
	private BigDecimal measuredKmPerLiter;

	private Instant fuelEconomyUpdatedAt;

	/** 차주가 직접 넣은 평소 연비(km/L). Pleos 실측이 없을 때 쓴다 (VEH-003) */
	@Column(precision = 4, scale = 2)
	private BigDecimal manualKmPerLiter;

	/** Pleos 차량 VIN의 SHA-256. 원래 VIN은 저장하지 않는다 (VEH-004) */
	@Column(unique = true, length = 64)
	private String vinHash;

	@Column(nullable = false)
	private boolean defaultVehicle;

	private Vehicle(Driver driver, String plateNumber, Tonnage tonnage, BodyType bodyType, BigDecimal heightM) {
		this.driver = driver;
		this.plateNumber = plateNumber;
		this.tonnage = tonnage;
		this.bodyType = bodyType;
		this.heightM = heightM;
	}

	public static Vehicle create(Driver driver, String plateNumber, Tonnage tonnage, BodyType bodyType, BigDecimal heightM) {
		return new Vehicle(driver, normalizePlateNumber(plateNumber), tonnage, bodyType, validateHeight(heightM));
	}

	/** 톤수·차종·높이를 고친다 */
	public void changeSpec(Tonnage tonnage, BodyType bodyType, BigDecimal heightM) {
		this.tonnage = tonnage;
		this.bodyType = bodyType;
		this.heightM = validateHeight(heightM);
	}

	/** 번호를 바꾼다 (오타 수정·번호 변경). 자동차등록증 재확인은 서비스가 맡는다 */
	public void changePlate(String plateNumber) {
		this.plateNumber = normalizePlateNumber(plateNumber);
	}

	/** 폭·길이·총중량·축 수를 바꾼다. null이면 비운다. 범위는 TMAP이 받는 값에 맞췄다 */
	public void changeDimensions(Integer widthCm, Integer lengthCm, Integer totalWeightKg, Integer axleCount) {
		this.widthCm = validateRange(widthCm, 100, 300);
		this.lengthCm = validateRange(lengthCm, 200, 2_000);
		this.totalWeightKg = validateRange(totalWeightKg, 500, 40_000);
		this.axleCount = validateRange(axleCount, 2, 6);
	}

	private static Integer validateRange(Integer value, int min, int max) {
		if (value != null && (value < min || value > max)) {
			throw new BusinessException(ErrorCode.VEHICLE_INVALID_DIMENSION);
		}
		return value;
	}

	/**
	 * Pleos 누적 연비를 km/L로 바꿔 저장한다. 문서에 단위가 없어 L/100km(100 ÷ 값)와 km/L(값 그대로)를 둘 다 따져
	 * 화물차 연비 범위 안에 들고 이 톤수 기본값에 더 가까운 쪽을 고른다. 둘 다 범위 밖(아직 집계 전 등)이면 무시한다.
	 */
	public void updateFuelEconomy(float reported, Instant now) {
		if (!(reported > 0) || Float.isInfinite(reported)) {
			return;
		}
		BigDecimal value = BigDecimal.valueOf(reported);
		BigDecimal fromPer100Km = HUNDRED_KM.divide(value, 2, RoundingMode.HALF_UP);
		BigDecimal asKmPerLiter = value.setScale(2, RoundingMode.HALF_UP);
		BigDecimal picked = closerToDefault(fromPer100Km, asKmPerLiter);
		if (picked == null) {
			return;
		}
		this.measuredKmPerLiter = picked;
		this.fuelEconomyUpdatedAt = now;
	}

	/** 범위 안 후보 중 이 톤수 기본 연비와 비율 차이가 작은 것 */
	private BigDecimal closerToDefault(BigDecimal first, BigDecimal second) {
		double base = tonnage.getDefaultKmPerLiter();
		BigDecimal best = null;
		double bestGap = Double.MAX_VALUE;
		for (BigDecimal candidate : new BigDecimal[] {first, second}) {
			if (!isTruckKmPerLiter(candidate)) {
				continue;
			}
			double gap = Math.abs(Math.log(candidate.doubleValue() / base));
			if (gap < bestGap) {
				best = candidate;
				bestGap = gap;
			}
		}
		return best;
	}

	/** 차주가 넣은 평소 연비. null이면 비운다 */
	public void changeManualFuelEconomy(BigDecimal kmPerLiter) {
		if (kmPerLiter != null && !isTruckKmPerLiter(kmPerLiter)) {
			throw new BusinessException(ErrorCode.VEHICLE_INVALID_FUEL_ECONOMY);
		}
		this.manualKmPerLiter = kmPerLiter == null ? null : kmPerLiter.setScale(1, RoundingMode.HALF_UP);
	}

	/** 기름값 계산에 쓰는 연비: Pleos 실측 → 차주 입력 → 톤수 기본값 (REC-011) */
	public double kmPerLiter() {
		if (measuredKmPerLiter != null) {
			return measuredKmPerLiter.doubleValue();
		}
		return manualKmPerLiter != null ? manualKmPerLiter.doubleValue() : tonnage.getDefaultKmPerLiter();
	}

	public FuelEconomySource fuelEconomySource() {
		if (measuredKmPerLiter != null) {
			return FuelEconomySource.PLEOS;
		}
		return manualKmPerLiter != null ? FuelEconomySource.MANUAL : FuelEconomySource.DEFAULT;
	}

	private static boolean isTruckKmPerLiter(BigDecimal kmPerLiter) {
		return kmPerLiter.compareTo(MIN_KM_PER_LITER) >= 0 && kmPerLiter.compareTo(MAX_KM_PER_LITER) <= 0;
	}

	/** Pleos 차량과 연결한다. 이 차에서 다시 로그인하면 이 차량으로 알아본다 */
	public void linkPleos(String vinHash) {
		this.vinHash = vinHash;
	}

	public void markDefault() {
		this.defaultVehicle = true;
	}

	public void unmarkDefault() {
		this.defaultVehicle = false;
	}

	public boolean isOwnedBy(Long userId) {
		return driver.getUserId().equals(userId);
	}

	/** 띄어쓰기·하이픈을 지우고 형식을 확인한다 */
	public static String normalizePlateNumber(String plateNumber) {
		String compact = plateNumber == null ? "" : plateNumber.replaceAll("[\\s-]", "");
		if (!PLATE_NUMBER.matcher(compact).matches()) {
			throw new BusinessException(ErrorCode.VEHICLE_INVALID_PLATE);
		}
		return compact;
	}

	private static BigDecimal validateHeight(BigDecimal heightM) {
		if (heightM == null) {
			return null;
		}
		if (heightM.compareTo(MIN_HEIGHT_M) < 0 || heightM.compareTo(MAX_HEIGHT_M) > 0) {
			throw new BusinessException(ErrorCode.VEHICLE_INVALID_HEIGHT);
		}
		return heightM.setScale(2, RoundingMode.HALF_UP);
	}
}
