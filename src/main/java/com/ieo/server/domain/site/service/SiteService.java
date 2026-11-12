package com.ieo.server.domain.site.service;

import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.site.dto.CreateSiteAttributeRequest;
import com.ieo.server.domain.site.dto.CreateSiteNoteRequest;
import com.ieo.server.domain.site.dto.CreateSiteRequest;
import com.ieo.server.domain.site.dto.HeightWarning;
import com.ieo.server.domain.site.dto.NearbySiteResponse;
import com.ieo.server.domain.site.dto.SiteAttributesResponse;
import com.ieo.server.domain.site.dto.SiteCardResponse;
import com.ieo.server.domain.site.dto.SiteNoteResponse;
import com.ieo.server.domain.site.dto.SiteResponse;
import com.ieo.server.domain.site.dto.SiteStatsResponse;
import com.ieo.server.domain.site.entity.SiteStats;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.site.entity.SiteAttribute;
import com.ieo.server.domain.site.entity.SiteNote;
import com.ieo.server.domain.site.repository.SiteAttributeRepository;
import com.ieo.server.domain.site.repository.SiteNoteRepository;
import com.ieo.server.domain.site.repository.SiteRepository;
import com.ieo.server.domain.site.repository.SiteStatsRepository;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상하차지 등록·조회 (SITE-001, 002, 006, 007, 010)와 차주가 남기는 정보·메모 (SITE-004, 005).
 * 정보와 메모는 차주만 남길 수 있다. 반경 찾기는 DB(PostGIS)가 하고, 화면에 보일 거리는 Site.distanceMeters로 붙인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteService {

	/** 이 거리 안에 기존 상하차지가 있으면 같은 곳으로 본다 (SITE-002) */
	static final double SAME_SITE_METERS = 100;
	public static final int DEFAULT_NEARBY_RADIUS_M = 3000;
	static final int MIN_NEARBY_RADIUS_M = 100;
	static final int MAX_NEARBY_RADIUS_M = 10_000;
	static final int MAX_NEARBY_RESULTS = 50;
	static final int CARD_NOTE_COUNT = 5;

	private final SiteRepository siteRepository;
	private final SiteAttributeRepository attributeRepository;
	private final SiteNoteRepository noteRepository;
	private final SiteStatsRepository statsRepository;
	private final UserRepository userRepository;
	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;

	public record Registration(SiteResponse site, boolean created) {
	}

	/** 100m 안에 같은 곳이 있으면 새로 만들지 않고 가장 가까운 곳을 돌려준다 */
	@Transactional
	public Registration register(Long userId, CreateSiteRequest request) {
		requireActiveUser(userId);
		SiteMatch match = findOrCreate(request, driverRepository.findById(userId).orElse(null));
		return new Registration(SiteResponse.of(match.site(), !match.created()), match.created());
	}

	public record SiteMatch(Site site, boolean created) {
	}

	/**
	 * 100m 안의 기존 상하차지를 쓰고, 없으면 새로 만든다 (SITE-002).
	 * 운행 등록처럼 다른 도메인이 상하차지를 함께 만들 때도 쓴다
	 */
	@Transactional
	public SiteMatch findOrCreate(CreateSiteRequest request, Driver createdBy) {
		Site.validateLocation(request.lat(), request.lng());
		Optional<Site> same = findWithin(request.lat(), request.lng(), SAME_SITE_METERS).stream().findFirst();
		if (same.isPresent()) {
			return new SiteMatch(same.get(), false);
		}
		int radius = request.geofenceRadiusM() == null ? Site.DEFAULT_GEOFENCE_RADIUS_M : request.geofenceRadiusM();
		Site site = siteRepository.save(
				Site.create(request.name(), request.address(), request.lat(), request.lng(), radius, createdBy));
		return new SiteMatch(site, true);
	}

	/** 가까운 순, 최대 50곳 */
	public List<NearbySiteResponse> getNearby(Long userId, double lat, double lng, Integer radiusM) {
		requireActiveUser(userId);
		Site.validateLocation(lat, lng);
		int radius = radiusM == null ? DEFAULT_NEARBY_RADIUS_M : radiusM;
		if (radius < MIN_NEARBY_RADIUS_M || radius > MAX_NEARBY_RADIUS_M) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "검색 반경은 100m에서 10km 사이로 정해 주세요.");
		}
		return findWithin(lat, lng, radius).stream()
				.limit(MAX_NEARBY_RESULTS)
				.map(site -> NearbySiteResponse.of(site, site.distanceMeters(lat, lng)))
				.toList();
	}

	/** 정보 카드: 항목별 최신 값, 메모 5개, 내 기본 차량 높이 경고 */
	public SiteCardResponse getCard(Long userId, Long siteId) {
		requireActiveUser(userId);
		Site site = findSite(siteId);

		SiteAttributesResponse attributes = SiteAttributesResponse.from(attributeRepository.findLatestBySiteId(siteId));
		BigDecimal vehicleHeightM = vehicleRepository.findByDriverUserIdAndDefaultVehicleTrue(userId)
				.map(Vehicle::getHeightM)
				.orElse(null);
		List<SiteNoteResponse> notes = noteRepository.findForCard(siteId, Pageable.ofSize(CARD_NOTE_COUNT)).stream()
				.map(note -> SiteNoteResponse.of(note, userId))
				.toList();
		SiteStatsResponse stats = statsRepository.findById(siteId)
				.filter(SiteStats::hasEnoughSamples)
				.map(SiteStatsResponse::from)
				.orElse(null);
		return SiteCardResponse.of(site, attributes, HeightWarning.of(vehicleHeightM, attributes.heightLimitM()),
				stats, notes);
	}

	/** 높이 제한·지게차·주차 정보를 남긴다. 덮어쓰지 않고 쌓으며, 반영된 항목별 최신 값을 돌려준다 */
	@Transactional
	public SiteAttributesResponse addAttribute(Long userId, Long siteId, CreateSiteAttributeRequest request) {
		Driver driver = requireDriver(userId);
		Site site = findSite(siteId);
		attributeRepository.saveAndFlush(SiteAttribute.create(site, request.key(), request.value(), driver));
		// TODO(SITE-011, 12 요금제 크레딧): 정보를 남긴 차주에게 소정의 크레딧을 준다. 규칙은 크레딧을 만들 때 정한다
		return SiteAttributesResponse.from(attributeRepository.findLatestBySiteId(siteId));
	}

	/** 메모를 남긴다. 다른 차주에게는 작성자 없이 보인다 (SITE-008) */
	@Transactional
	public SiteNoteResponse addNote(Long userId, Long siteId, CreateSiteNoteRequest request) {
		Driver driver = requireDriver(userId);
		Site site = findSite(siteId);
		SiteNote note = noteRepository.saveAndFlush(SiteNote.create(site, driver, request.text()));
		// TODO(SITE-011, 12 요금제 크레딧): 메모를 남긴 차주에게 소정의 크레딧을 준다. 짧거나 같은 곳에 연달아 남긴 메모는 빼는 규칙과 함께
		return SiteNoteResponse.of(note, userId);
	}

	private Site findSite(Long siteId) {
		return siteRepository.findById(siteId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SITE_NOT_FOUND));
	}

	/** 차주 프로필이 없으면 주선사 담당자 등 차주가 아닌 계정이다 */
	private Driver requireDriver(Long userId) {
		requireActiveUser(userId);
		return driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SITE_NOT_DRIVER));
	}

	/** (lat, lng)에서 meters 안의 상하차지를 가까운 순으로 */
	private List<Site> findWithin(double lat, double lng, double meters) {
		return siteRepository.findWithin(lat, lng, meters, MAX_NEARBY_RESULTS);
	}

	/** 탈퇴한 계정의 남은 토큰은 없는 계정으로 본다 */
	private void requireActiveUser(Long userId) {
		userRepository.findById(userId)
				.filter(user -> user.getStatus() != UserStatus.WITHDRAWN)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}
