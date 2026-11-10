package com.ieo.server.domain.document.service;

import com.ieo.server.domain.document.entity.BrokerDocument;
import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.entity.ReviewDocument;
import com.ieo.server.domain.document.enums.BrokerDocumentType;
import com.ieo.server.domain.document.enums.DocumentKind;
import com.ieo.server.domain.document.enums.DocumentStatus;
import com.ieo.server.domain.document.enums.DocumentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 한 차주(또는 한 주선사 회사)의 서류를 종류별로 묶어 인증 여부를 계산한다 (07 인계서 5장).
 * 인증 여부는 저장하지 않고 계산한다: 필수 서류의 최신 제출이 모두 승인이면 인증됨.
 * 입력은 최근 제출이 앞인 목록이어야 한다 (repository가 그렇게 돌려준다).
 *
 * @param <T> 서류 종류 enum (차주 DocumentType, 주선사 BrokerDocumentType)
 * @param <D> 서류 엔티티
 */
public final class DocumentReview<T extends Enum<T> & DocumentKind, D extends ReviewDocument> {

	private final Map<T, List<D>> byType;
	private final List<T> requiredTypes;
	private final T businessType;

	private DocumentReview(Class<T> typeClass, List<T> requiredTypes, T businessType, List<D> latestFirst, Function<D, T> typeOf) {
		this.byType = new EnumMap<>(typeClass);
		this.requiredTypes = requiredTypes;
		this.businessType = businessType;
		for (D document : latestFirst) {
			byType.computeIfAbsent(typeOf.apply(document), type -> new ArrayList<>()).add(document);
		}
	}

	public static DocumentReview<DocumentType, DriverDocument> ofDriver(List<DriverDocument> latestFirst) {
		return new DocumentReview<>(DocumentType.class, DocumentType.requiredTypes(), DocumentType.BUSINESS_REGISTRATION, latestFirst, DriverDocument::getType);
	}

	public static DocumentReview<BrokerDocumentType, BrokerDocument> ofBroker(List<BrokerDocument> latestFirst) {
		return new DocumentReview<>(BrokerDocumentType.class, BrokerDocumentType.requiredTypes(), BrokerDocumentType.BUSINESS_REGISTRATION, latestFirst,
				BrokerDocument::getType);
	}

	/** 이 종류의 제출 전부. 최근 것이 앞 */
	public List<D> submissions(T type) {
		return byType.getOrDefault(type, List.of());
	}

	public Optional<D> latest(T type) {
		List<D> submissions = submissions(type);
		return submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.get(0));
	}

	/** 심사 대기인 사업자등록증의 국세청 조회. 목록에서 눈에 띄게 보여 준다 */
	public Optional<BusinessCheck> pendingBusinessCheck() {
		return latest(businessType).filter(ReviewDocument::isPending).map(ReviewDocument::getBusinessCheck);
	}

	/** 종류별 최신 제출 */
	public List<D> latestAll() {
		return byType.values().stream().map(submissions -> submissions.get(0)).toList();
	}

	public boolean isEmpty() {
		return byType.isEmpty();
	}

	public boolean verified() {
		return approvedRequired() == requiredTypes.size();
	}

	public int approvedRequired() {
		return (int) requiredTypes.stream()
				.filter(type -> latest(type).map(ReviewDocument::isApproved).orElse(false))
				.count();
	}

	public int requiredTotal() {
		return requiredTypes.size();
	}

	public long pendingCount() {
		return latestAll().stream().filter(ReviewDocument::isPending).count();
	}

	public long rejectedCount() {
		return latestAll().stream().filter(document -> document.getStatus() == DocumentStatus.REJECTED).count();
	}

	/** 같은 종류를 두 번 이상 냈다 (반려 뒤 다시 냈거나 갱신했다) */
	public boolean resubmitted() {
		return byType.values().stream().anyMatch(submissions -> submissions.size() > 1);
	}

	public Optional<Instant> latestSubmittedAt() {
		return latestAll().stream().map(ReviewDocument::getCreatedAt).max(Comparator.naturalOrder());
	}

	/** 가장 오래 기다린 심사 대기 서류의 제출 시각. 목록을 오래된 순으로 보여 줄 때 쓴다 */
	public Optional<Instant> oldestPendingAt() {
		return latestAll().stream().filter(ReviewDocument::isPending).map(ReviewDocument::getCreatedAt).min(Comparator.naturalOrder());
	}
}
