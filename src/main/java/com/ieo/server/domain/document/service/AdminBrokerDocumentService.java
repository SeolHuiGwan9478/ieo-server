package com.ieo.server.domain.document.service;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.broker.repository.BrokerRepository;
import com.ieo.server.domain.document.business.BusinessLookupClient;
import com.ieo.server.domain.document.dto.AdminBrokerReviewListResponse;
import com.ieo.server.domain.document.dto.AdminBrokerReviewResponse;
import com.ieo.server.domain.document.dto.AdminBrokerReviewResponse.BrokerInfo;
import com.ieo.server.domain.document.dto.AdminBrokerReviewRow;
import com.ieo.server.domain.document.dto.DocumentResponse;
import com.ieo.server.domain.document.dto.DocumentTypeResponse;
import com.ieo.server.domain.document.dto.EventResponse;
import com.ieo.server.domain.document.dto.RejectReasonResponse;
import com.ieo.server.domain.document.dto.RejectRequest;
import com.ieo.server.domain.document.entity.BrokerDocument;
import com.ieo.server.domain.document.entity.BrokerDocumentEvent;
import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.enums.BrokerDocumentType;
import com.ieo.server.domain.document.enums.DocumentEventType;
import com.ieo.server.domain.document.enums.ReviewFilter;
import com.ieo.server.domain.document.enums.ReviewSubject;
import com.ieo.server.domain.document.repository.BrokerDocumentEventRepository;
import com.ieo.server.domain.document.repository.BrokerDocumentRepository;
import com.ieo.server.domain.user.repository.UserRepository;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 주선사 서류 심사 (07 인계서 13장). 차주 심사(AdminDocumentService)와 같은 규칙으로 회사 단위 목록·상세·승인·반려를 준다.
 * 목록은 서류 전체를 읽어 회사별로 묶고 탭·검색·정렬·페이지를 메모리에서 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBrokerDocumentService {

	private final BrokerDocumentRepository documentRepository;
	private final BrokerDocumentEventRepository eventRepository;
	private final BrokerRepository brokerRepository;
	private final BrokerMemberRepository memberRepository;
	private final UserRepository userRepository;
	private final FileStorage fileStorage;
	private final BusinessLookupClient businessLookupClient;
	private final Clock clock;

	public AdminBrokerReviewListResponse list(ReviewFilter filter, String query, int page, int size) {
		ReviewFilter tab = filter == null ? ReviewFilter.PENDING : filter;
		int pageSize = ReviewListing.pageSize(size);
		int pageNo = ReviewListing.pageNo(page);

		Map<Long, List<BrokerDocument>> byBroker = new LinkedHashMap<>();
		for (BrokerDocument document : documentRepository.findAllWithBroker()) {
			byBroker.computeIfAbsent(document.getBroker().getId(), id -> new ArrayList<>()).add(document);
		}
		List<Candidate> all = byBroker.values().stream()
				.map(documents -> new Candidate(documents.get(0).getBroker(), DocumentReview.ofBroker(documents)))
				.toList();
		long pendingBrokers = all.stream().filter(candidate -> candidate.review().pendingCount() > 0).count();

		String needle = ReviewListing.normalize(query);
		List<Candidate> matched = all.stream()
				.filter(candidate -> ReviewListing.matches(tab, candidate.review()))
				.filter(candidate -> needle.isEmpty() || matches(needle, candidate.broker()))
				.sorted(ReviewListing.order(tab, Candidate::review))
				.toList();
		List<AdminBrokerReviewRow> rows = matched.stream()
				.skip((long) pageNo * pageSize)
				.limit(pageSize)
				.map(candidate -> AdminBrokerReviewRow.of(candidate.broker(), candidate.review()))
				.toList();
		return new AdminBrokerReviewListResponse(rows, pageNo, pageSize, matched.size(), pendingBrokers);
	}

	public AdminBrokerReviewResponse broker(Long brokerId) {
		Broker broker = brokerRepository.findById(brokerId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NOT_FOUND));
		var review = DocumentReview.ofBroker(documentRepository.findAllByBrokerIdOrderByCreatedAtDescIdDesc(brokerId));
		List<DocumentTypeResponse> documents = Arrays.stream(BrokerDocumentType.values())
				.map(type -> DocumentTypeResponse.of(type, review.submissions(type)))
				.toList();
		List<BrokerDocumentEvent> events = eventRepository.findAllByBrokerIdOrderByCreatedAtDescIdDesc(brokerId);
		Map<Long, String> actorNames = userRepository.findAllById(events.stream().map(BrokerDocumentEvent::getActorUserId).collect(Collectors.toSet()))
				.stream().collect(HashMap::new, (map, user) -> map.put(user.getId(), user.getName()), HashMap::putAll);
		List<EventResponse> eventResponses = events.stream()
				.map(event -> EventResponse.of(event, actorNames.get(event.getActorUserId())))
				.toList();
		List<BrokerMember> members = memberRepository.findAllByBrokerIdAndRemovedAtIsNullOrderByCreatedAtAsc(brokerId);
		return new AdminBrokerReviewResponse(BrokerInfo.from(broker, members), review.verified(), review.approvedRequired(), review.requiredTotal(),
				documents, eventResponses, RejectReasonResponse.forSubject(ReviewSubject.BROKER));
	}

	/** 서류 사진. 누가 언제 무엇을 열었는지 이력에 남긴다 */
	@Transactional
	public FileStorage.StoredFile file(Long adminId, Long documentId) {
		BrokerDocument document = find(documentId);
		eventRepository.save(BrokerDocumentEvent.of(document, DocumentEventType.VIEWED, adminId, null));
		return fileStorage.load(document.getFileKey());
	}

	@Transactional
	public DocumentResponse approve(Long adminId, Long documentId) {
		BrokerDocument document = find(documentId);
		document.approve(adminId, clock.instant());
		eventRepository.save(BrokerDocumentEvent.of(document, DocumentEventType.APPROVED, adminId, null));
		flushOrConflict();
		return DocumentResponse.from(document);
	}

	@Transactional
	public DocumentResponse reject(Long adminId, Long documentId, RejectRequest request) {
		BrokerDocument document = find(documentId);
		document.reject(adminId, request.reasonCode(), request.note(), clock.instant());
		eventRepository.save(BrokerDocumentEvent.of(document, DocumentEventType.REJECTED, adminId, document.rejectMessage()));
		flushOrConflict();
		return DocumentResponse.from(document);
	}

	/** 국세청 진위확인을 다시 한다. 심사 대기인 사업자등록증만 */
	@Transactional
	public DocumentResponse recheckBusiness(Long adminId, Long documentId) {
		BrokerDocument document = find(documentId);
		BusinessCheck check = document.getBusinessCheck();
		if (check == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "사업자등록증만 국세청 조회를 할 수 있어요.");
		}
		document.recordBusinessCheck(businessLookupClient.lookup(check.getBusinessNumber(), check.getOpenedOn(), check.getOwnerName()), clock.instant());
		eventRepository.save(BrokerDocumentEvent.of(document, DocumentEventType.BUSINESS_CHECKED, adminId, check.summary()));
		flushOrConflict();
		return DocumentResponse.from(document);
	}

	private BrokerDocument find(Long documentId) {
		return documentRepository.findByIdWithBroker(documentId)
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

	private record Candidate(Broker broker, DocumentReview<?, ?> review) {
	}

	/** 상호, 사업자번호, 허가 번호로 찾는다 */
	private static boolean matches(String needle, Broker broker) {
		String name = broker.getName() == null ? "" : broker.getName().toLowerCase();
		String license = ReviewListing.normalize(broker.getLicenseNumber());
		return name.contains(needle) || broker.getBusinessNumber().contains(needle) || license.contains(needle);
	}
}
