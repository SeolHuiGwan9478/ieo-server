package com.ieo.server.domain.document.service;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.broker.entity.BrokerMember;
import com.ieo.server.domain.broker.repository.BrokerMemberRepository;
import com.ieo.server.domain.document.business.BusinessLookupClient;
import com.ieo.server.domain.document.dto.BrokerDocumentsResponse;
import com.ieo.server.domain.document.dto.BusinessInput;
import com.ieo.server.domain.document.dto.DocumentResponse;
import com.ieo.server.domain.document.entity.BrokerDocument;
import com.ieo.server.domain.document.entity.BrokerDocumentEvent;
import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.entity.ReviewDocument;
import com.ieo.server.domain.document.enums.BrokerDocumentType;
import com.ieo.server.domain.document.enums.DocumentEventType;
import com.ieo.server.domain.document.repository.BrokerDocumentEventRepository;
import com.ieo.server.domain.document.repository.BrokerDocumentRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.storage.FileStorage;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주선사 쪽 회사 서류 제출 (07 인계서 13장). 주선사 콘솔의 설정 > 회사 서류가 쓴다.
 * - 회사에 소속된 담당자면 직책과 상관없이 낼 수 있다. 서류는 회사 단위로 심사한다
 * - 낸 서류는 심사 대기로 들어가고, 결과가 나오기 전에는 같은 종류를 다시 낼 수 없다. 반려·승인 뒤에는 새 건으로 다시 낸다
 * - 사업자등록증은 회사에 등록된 사업자번호(와 대표자)로 국세청 진위확인을 바로 돌린다. 개업일만 새로 받는다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerDocumentService {

	/** 저장소 안의 폴더. 차주 서류와 같은 폴더를 쓴다 (key가 무작위라 겹치지 않는다) */
	static final String FOLDER = "documents";

	private final BrokerDocumentRepository documentRepository;
	private final BrokerDocumentEventRepository eventRepository;
	private final BrokerMemberRepository memberRepository;
	private final UserRepository userRepository;
	private final FileStorage fileStorage;
	private final BusinessLookupClient businessLookupClient;
	private final Clock clock;

	/** 회사 서류가 모두 승인됐는지. 오더 등록 게이트와 /api/brokers/me의 verified가 쓴다 */
	public boolean isVerified(Long brokerId) {
		return DocumentReview.ofBroker(documentRepository.findAllByBrokerIdOrderByCreatedAtDescIdDesc(brokerId)).verified();
	}

	public BrokerDocumentsResponse myDocuments(Long userId) {
		Broker broker = requireMember(userId).getBroker();
		return BrokerDocumentsResponse.of(DocumentReview.ofBroker(documentRepository.findAllByBrokerIdOrderByCreatedAtDescIdDesc(broker.getId())));
	}

	@Transactional
	public DocumentResponse submit(Long userId, BrokerDocumentType type, MultipartFile file, BusinessInput businessInput) {
		Broker broker = requireMember(userId).getBroker();
		if (type == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "서류 종류를 골라 주세요.");
		}
		DocumentFiles.Upload upload = DocumentFiles.read(file);
		var review = DocumentReview.ofBroker(documentRepository.findAllByBrokerIdOrderByCreatedAtDescIdDesc(broker.getId()));
		if (review.latest(type).map(ReviewDocument::isPending).orElse(false)) {
			throw new BusinessException(ErrorCode.DOCUMENT_PENDING);
		}
		BusinessCheck businessCheck = businessCheckOf(type, broker, businessInput);
		if (businessCheck != null) {
			// 국세청이 응답하지 않아도 ERROR 결과로 남을 뿐 제출은 막히지 않는다. 운영자가 다시 조회할 수 있다
			businessCheck.record(businessLookupClient.lookup(businessCheck.getBusinessNumber(), businessCheck.getOpenedOn(), businessCheck.getOwnerName()),
					clock.instant());
		}
		String key = fileStorage.store(FOLDER, upload.content(), upload.contentType());
		BrokerDocument document = documentRepository.save(BrokerDocument.submit(broker, type, key, upload.contentType(), businessCheck));
		eventRepository.save(BrokerDocumentEvent.of(document, DocumentEventType.SUBMITTED, userId, null));
		if (businessCheck != null) {
			eventRepository.save(BrokerDocumentEvent.of(document, DocumentEventType.BUSINESS_CHECKED, userId, businessCheck.summary()));
		}
		return DocumentResponse.from(document);
	}

	/**
	 * 사업자등록증은 개업일이 필수다. 사업자번호는 회사 등록값을 쓰고, 보내 왔다면 등록값과 같아야 한다 (다르면 회사 정보를 먼저 고친다).
	 * 대표자는 비우면 등록된 대표자. 등록된 대표자도 없으면 적어야 한다
	 */
	private static BusinessCheck businessCheckOf(BrokerDocumentType type, Broker broker, BusinessInput input) {
		BusinessInput given = input == null ? new BusinessInput(null, null, null) : input;
		if (type != BrokerDocumentType.BUSINESS_REGISTRATION) {
			if (!given.isEmpty()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "사업자 정보는 사업자등록증을 낼 때만 보내요.");
			}
			return null;
		}
		if (given.businessNumber() != null && !given.businessNumber().isBlank()
				&& !ReviewListing.normalize(given.businessNumber()).equals(broker.getBusinessNumber())) {
			throw new BusinessException(ErrorCode.DOCUMENT_BUSINESS_INFO, "회사에 등록된 사업자등록번호와 달라요. 설정의 회사 정보를 먼저 확인해 주세요.");
		}
		String ownerName = given.ownerName() == null || given.ownerName().isBlank() ? broker.getRepresentativeName() : given.ownerName();
		return BusinessCheck.of(broker.getBusinessNumber(), given.openedOnDate(), ownerName);
	}

	/** 우리 회사 서류 사진 */
	public FileStorage.StoredFile myFile(Long userId, Long documentId) {
		Broker broker = requireMember(userId).getBroker();
		BrokerDocument document = documentRepository.findByIdAndBrokerId(documentId, broker.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		return fileStorage.load(document.getFileKey());
	}

	/** 지금 회사에 소속된 주선사 담당자. 직책은 가리지 않는다 */
	private BrokerMember requireMember(Long userId) {
		User user = userRepository.findById(userId)
				.filter(found -> found.getStatus() != UserStatus.WITHDRAWN)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (user.getRole() != UserRole.BROKER) {
			throw new BusinessException(ErrorCode.DOCUMENT_NOT_BROKER);
		}
		BrokerMember member = memberRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROKER_NO_MEMBERSHIP));
		if (!member.isActive()) {
			throw new BusinessException(ErrorCode.BROKER_MEMBER_REMOVED);
		}
		return member;
	}
}
