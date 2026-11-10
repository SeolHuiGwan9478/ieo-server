package com.ieo.server.domain.document.service;

import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.enums.DocumentStatus;
import com.ieo.server.domain.document.enums.DocumentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한 차주의 서류를 종류별로 묶어 인증 여부를 계산한다 (07 인계서 5장).
 * 인증 여부는 저장하지 않고 계산한다: 필수 서류의 최신 제출이 모두 승인이면 인증됨.
 * 입력은 최근 제출이 앞인 목록이어야 한다 (repository가 그렇게 돌려준다).
 */
public final class DocumentReview {

	private final Map<DocumentType, List<DriverDocument>> byType = new EnumMap<>(DocumentType.class);

	private DocumentReview(List<DriverDocument> latestFirst) {
		for (DriverDocument document : latestFirst) {
			byType.computeIfAbsent(document.getType(), type -> new ArrayList<>()).add(document);
		}
	}

	public static DocumentReview of(List<DriverDocument> latestFirst) {
		return new DocumentReview(latestFirst);
	}

	/** 이 종류의 제출 전부. 최근 것이 앞 */
	public List<DriverDocument> submissions(DocumentType type) {
		return byType.getOrDefault(type, List.of());
	}

	public Optional<DriverDocument> latest(DocumentType type) {
		List<DriverDocument> submissions = submissions(type);
		return submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.get(0));
	}

	/** 심사 대기인 사업자등록증의 국세청 조회. 목록에서 눈에 띄게 보여 준다 */
	public Optional<BusinessCheck> pendingBusinessCheck() {
		return latest(DocumentType.BUSINESS_REGISTRATION).filter(DriverDocument::isPending).map(DriverDocument::getBusinessCheck);
	}

	/** 종류별 최신 제출 */
	public List<DriverDocument> latestAll() {
		return byType.values().stream().map(submissions -> submissions.get(0)).toList();
	}

	public boolean isEmpty() {
		return byType.isEmpty();
	}

	public boolean verified() {
		return approvedRequired() == DocumentType.requiredTypes().size();
	}

	public int approvedRequired() {
		return (int) DocumentType.requiredTypes().stream()
				.filter(type -> latest(type).map(DriverDocument::isApproved).orElse(false))
				.count();
	}

	public long pendingCount() {
		return latestAll().stream().filter(DriverDocument::isPending).count();
	}

	public long rejectedCount() {
		return latestAll().stream().filter(document -> document.getStatus() == DocumentStatus.REJECTED).count();
	}

	/** 같은 종류를 두 번 이상 냈다 (반려 뒤 다시 냈거나 갱신했다) */
	public boolean resubmitted() {
		return byType.values().stream().anyMatch(submissions -> submissions.size() > 1);
	}

	public Optional<Instant> latestSubmittedAt() {
		return latestAll().stream().map(DriverDocument::getCreatedAt).max(Comparator.naturalOrder());
	}

	/** 가장 오래 기다린 심사 대기 서류의 제출 시각. 목록을 오래된 순으로 보여 줄 때 쓴다 */
	public Optional<Instant> oldestPendingAt() {
		return latestAll().stream().filter(DriverDocument::isPending).map(DriverDocument::getCreatedAt).min(Comparator.naturalOrder());
	}
}
