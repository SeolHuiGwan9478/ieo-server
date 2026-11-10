package com.ieo.server.domain.document.service;

import com.ieo.server.domain.document.business.BusinessLookupClient;
import com.ieo.server.domain.document.dto.AdminDriverReviewResponse;
import com.ieo.server.domain.document.dto.AdminDriverReviewResponse.DriverInfo;
import com.ieo.server.domain.document.dto.AdminDriverReviewResponse.VehicleInfo;
import com.ieo.server.domain.document.dto.AdminReviewListResponse;
import com.ieo.server.domain.document.dto.AdminReviewRow;
import com.ieo.server.domain.document.dto.DocumentResponse;
import com.ieo.server.domain.document.dto.DocumentTypeResponse;
import com.ieo.server.domain.document.dto.EventResponse;
import com.ieo.server.domain.document.dto.RejectReasonResponse;
import com.ieo.server.domain.document.dto.RejectRequest;
import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.entity.DriverDocumentEvent;
import com.ieo.server.domain.document.enums.DocumentEventType;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.enums.ReviewFilter;
import com.ieo.server.domain.document.enums.ReviewSubject;
import com.ieo.server.domain.document.repository.DriverDocumentEventRepository;
import com.ieo.server.domain.document.repository.DriverDocumentRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import com.ieo.server.domain.vehicle.repository.VehicleRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.storage.FileStorage;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 차주 서류 심사 (07 인계서 6장·7장). /api/admin/** 은 AdminInterceptor가 운영자만 통과시킨다.
 * 목록은 차주 단위다: 서류 전체를 읽어 차주별로 묶고 탭·검색·정렬·페이지를 메모리에서 처리한다 (차주가 수천 명이 되면 DB 쿼리로 바꾼다).
 * 주선사 서류는 AdminBrokerDocumentService가 같은 규칙으로 다룬다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDocumentService {

	private final DriverDocumentRepository documentRepository;
	private final DriverDocumentEventRepository eventRepository;
	private final DriverRepository driverRepository;
	private final VehicleRepository vehicleRepository;
	private final UserRepository userRepository;
	private final FileStorage fileStorage;
	private final BusinessLookupClient businessLookupClient;
	private final Clock clock;

	public AdminReviewListResponse list(ReviewFilter filter, String query, int page, int size) {
		ReviewFilter tab = filter == null ? ReviewFilter.PENDING : filter;
		int pageSize = ReviewListing.pageSize(size);
		int pageNo = ReviewListing.pageNo(page);

		Map<Long, List<DriverDocument>> byDriver = new LinkedHashMap<>();
		for (DriverDocument document : documentRepository.findAllWithDriver()) {
			byDriver.computeIfAbsent(document.getDriver().getUserId(), id -> new ArrayList<>()).add(document);
		}
		List<Candidate> all = byDriver.values().stream()
				.map(documents -> new Candidate(documents.get(0).getDriver(), DocumentReview.ofDriver(documents)))
				.toList();
		long pendingDrivers = all.stream().filter(candidate -> candidate.review().pendingCount() > 0).count();
		Map<Long, Vehicle> vehicles = vehiclesOf(all.stream().map(Candidate::driverId).toList());

		String needle = ReviewListing.normalize(query);
		List<Candidate> matched = all.stream()
				.filter(candidate -> ReviewListing.matches(tab, candidate.review()))
				.filter(candidate -> needle.isEmpty() || matches(needle, candidate, vehicles.get(candidate.driverId())))
				.sorted(ReviewListing.order(tab, Candidate::review))
				.toList();
		List<AdminReviewRow> rows = matched.stream()
				.skip((long) pageNo * pageSize)
				.limit(pageSize)
				.map(candidate -> AdminReviewRow.of(candidate.driver().getUser(), vehicles.get(candidate.driverId()), candidate.review()))
				.toList();
		return new AdminReviewListResponse(rows, pageNo, pageSize, matched.size(), pendingDrivers);
	}

	public AdminDriverReviewResponse driver(Long driverId) {
		Driver driver = driverRepository.findById(driverId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		var review = DocumentReview.ofDriver(documentRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(driverId));
		List<DocumentTypeResponse> documents = Arrays.stream(DocumentType.values())
				.map(type -> DocumentTypeResponse.of(type, review.submissions(type)))
				.toList();
		List<DriverDocumentEvent> events = eventRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(driverId);
		Map<Long, String> actorNames = userRepository.findAllById(events.stream().map(DriverDocumentEvent::getActorUserId).collect(Collectors.toSet()))
				.stream().collect(HashMap::new, (map, user) -> map.put(user.getId(), user.getName()), HashMap::putAll);
		List<EventResponse> eventResponses = events.stream()
				.map(event -> EventResponse.of(event, actorNames.get(event.getActorUserId())))
				.toList();
		return new AdminDriverReviewResponse(DriverInfo.from(driver), VehicleInfo.from(vehicleOf(driverId)), review.verified(),
				review.approvedRequired(), review.requiredTotal(), documents, eventResponses, RejectReasonResponse.forSubject(ReviewSubject.DRIVER));
	}

	/** 서류 사진. 누가 언제 무엇을 열었는지 이력에 남긴다 (07 인계서 8장) */
	@Transactional
	public FileStorage.StoredFile file(Long adminId, Long documentId) {
		DriverDocument document = find(documentId);
		eventRepository.save(DriverDocumentEvent.of(document, DocumentEventType.VIEWED, adminId, null));
		return fileStorage.load(document.getFileKey());
	}

	@Transactional
	public DocumentResponse approve(Long adminId, Long documentId) {
		DriverDocument document = find(documentId);
		document.approve(adminId, clock.instant());
		eventRepository.save(DriverDocumentEvent.of(document, DocumentEventType.APPROVED, adminId, null));
		flushOrConflict();
		return DocumentResponse.from(document);
	}

	@Transactional
	public DocumentResponse reject(Long adminId, Long documentId, RejectRequest request) {
		DriverDocument document = find(documentId);
		document.reject(adminId, request.reasonCode(), request.note(), clock.instant());
		eventRepository.save(DriverDocumentEvent.of(document, DocumentEventType.REJECTED, adminId, document.rejectMessage()));
		flushOrConflict();
		return DocumentResponse.from(document);
	}

	/** 국세청 진위확인을 다시 한다 (처음 조회가 실패했거나 키를 나중에 넣었을 때). 심사 대기인 사업자등록증만 */
	@Transactional
	public DocumentResponse recheckBusiness(Long adminId, Long documentId) {
		DriverDocument document = find(documentId);
		BusinessCheck check = document.getBusinessCheck();
		if (check == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "사업자등록증만 국세청 조회를 할 수 있어요.");
		}
		document.recordBusinessCheck(businessLookupClient.lookup(check.getBusinessNumber(), check.getOpenedOn(), check.getOwnerName()), clock.instant());
		eventRepository.save(DriverDocumentEvent.of(document, DocumentEventType.BUSINESS_CHECKED, adminId, check.summary()));
		flushOrConflict();
		return DocumentResponse.from(document);
	}

	private DriverDocument find(Long documentId) {
		return documentRepository.findByIdWithDriver(documentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
	}

	/** 두 운영자가 같은 건을 동시에 처리하면 늦은 쪽은 409를 받는다 */
	private void flushOrConflict() {
		try {
			documentRepository.flush();
		} catch (ObjectOptimisticLockingFailureException e) {
			throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_REVIEWED);
		}
	}

	private record Candidate(Driver driver, DocumentReview<?, ?> review) {

		Long driverId() {
			return driver.getUserId();
		}
	}

	/** 이름, 휴대폰 뒷자리, 차량 번호로 찾는다 */
	private static boolean matches(String needle, Candidate candidate, Vehicle vehicle) {
		User user = candidate.driver().getUser();
		String name = user.getName() == null ? "" : user.getName().toLowerCase();
		String phone = user.getPhone() == null ? "" : user.getPhone();
		String plate = vehicle == null ? "" : ReviewListing.normalize(vehicle.getPlateNumber());
		return name.contains(needle) || (needle.chars().allMatch(Character::isDigit) && phone.endsWith(needle)) || plate.contains(needle);
	}

	private Vehicle vehicleOf(Long driverId) {
		return vehiclesOf(List.of(driverId)).get(driverId);
	}

	/** 기본 차량이 있으면 그것, 없으면 먼저 등록한 차량 */
	private Map<Long, Vehicle> vehiclesOf(List<Long> driverIds) {
		if (driverIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Vehicle> chosen = new HashMap<>();
		for (Vehicle vehicle : vehicleRepository.findAllByDriverUserIdInOrderByCreatedAtAsc(Set.copyOf(driverIds))) {
			chosen.merge(vehicle.getDriver().getUserId(), vehicle, (current, next) -> next.isDefaultVehicle() ? next : current);
		}
		return chosen;
	}
}
