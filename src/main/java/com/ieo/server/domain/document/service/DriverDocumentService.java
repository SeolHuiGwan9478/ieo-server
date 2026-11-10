package com.ieo.server.domain.document.service;

import com.ieo.server.domain.document.business.BusinessLookupClient;
import com.ieo.server.domain.document.dto.BusinessInput;
import com.ieo.server.domain.document.dto.DocumentResponse;
import com.ieo.server.domain.document.dto.DriverDocumentsResponse;
import com.ieo.server.domain.document.entity.BusinessCheck;
import com.ieo.server.domain.document.entity.DriverDocument;
import com.ieo.server.domain.document.entity.DriverDocumentEvent;
import com.ieo.server.domain.document.enums.DocumentEventType;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.repository.DriverDocumentEventRepository;
import com.ieo.server.domain.document.repository.DriverDocumentRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.user.entity.User;
import com.ieo.server.domain.user.enums.UserRole;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.user.repository.UserRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.storage.FileStorage;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 차주 쪽 서류 제출 (07 인계서 3장, 7장). 폰 앱이 쓴다.
 * - 낸 서류는 심사 대기로 들어가고, 결과가 나오기 전에는 같은 종류를 다시 낼 수 없다
 * - 반려되거나 승인된 뒤에는 새 건으로 다시 낼 수 있다 (갱신)
 * - 서류 파일은 본인과 운영자만 로그인한 API로 본다. 공개 주소를 만들지 않는다
 * - 사업자등록증은 사업자번호·개업일(·대표자)을 같이 받아 국세청 진위확인을 바로 돌리고 결과를 서류에 붙인다 (반자동)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverDocumentService {

	/** 저장소 안의 폴더. 인수증(delivery)과 분리해 둔다 */
	static final String FOLDER = "documents";

	private final DriverDocumentRepository documentRepository;
	private final DriverDocumentEventRepository eventRepository;
	private final DriverRepository driverRepository;
	private final UserRepository userRepository;
	private final FileStorage fileStorage;
	private final BusinessLookupClient businessLookupClient;
	private final Clock clock;

	public DriverDocumentsResponse myDocuments(Long userId) {
		requireDriver(userId);
		return DriverDocumentsResponse.of(DocumentReview.ofDriver(documentRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(userId)));
	}

	/** 필수 서류 4종이 모두 승인됐는지. 배차 요청 게이트가 쓴다 */
	public boolean isVerified(Long userId) {
		return DocumentReview.ofDriver(documentRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(userId)).verified();
	}

	@Transactional
	public DocumentResponse submit(Long userId, DocumentType type, MultipartFile file, BusinessInput businessInput) {
		Driver driver = requireDriver(userId);
		if (type == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "서류 종류를 골라 주세요.");
		}
		DocumentFiles.Upload upload = DocumentFiles.read(file);
		var review = DocumentReview.ofDriver(documentRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(userId));
		if (review.latest(type).map(DriverDocument::isPending).orElse(false)) {
			throw new BusinessException(ErrorCode.DOCUMENT_PENDING);
		}
		BusinessCheck businessCheck = businessCheckOf(type, driver, businessInput);
		if (businessCheck != null) {
			// 국세청이 응답하지 않아도 ERROR 결과로 남을 뿐 제출은 막히지 않는다. 운영자가 다시 조회할 수 있다
			businessCheck.record(businessLookupClient.lookup(businessCheck.getBusinessNumber(), businessCheck.getOpenedOn(), businessCheck.getOwnerName()),
					clock.instant());
		}
		String key = fileStorage.store(FOLDER, upload.content(), upload.contentType());
		DriverDocument document = documentRepository.save(DriverDocument.submit(driver, type, key, upload.contentType(), businessCheck));
		eventRepository.save(DriverDocumentEvent.of(document, DocumentEventType.SUBMITTED, userId, null));
		if (businessCheck != null) {
			eventRepository.save(DriverDocumentEvent.of(document, DocumentEventType.BUSINESS_CHECKED, userId, businessCheck.summary()));
		}
		return DocumentResponse.from(document);
	}

	/**
	 * 사업자등록증은 개업일이 필수고 다른 서류는 사업자 정보를 받지 않는다.
	 * 번호·대표자를 비우면 계정에 적어 둔 사업자 정보(ACC-003, Driver.businessNumber·representativeName)를, 그것도 없으면 가입자 이름을 쓴다
	 */
	private static BusinessCheck businessCheckOf(DocumentType type, Driver driver, BusinessInput input) {
		BusinessInput given = input == null ? new BusinessInput(null, null, null) : input;
		if (type != DocumentType.BUSINESS_REGISTRATION) {
			if (!given.isEmpty()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "사업자 정보는 사업자등록증을 낼 때만 보냅니다.");
			}
			return null;
		}
		String number = firstNonBlank(given.businessNumber(), driver.getBusinessNumber());
		String ownerName = firstNonBlank(given.ownerName(), driver.getRepresentativeName(), driver.getUser().getName());
		return BusinessCheck.of(number, given.openedOnDate(), ownerName);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	/** 내가 올린 서류 사진 */
	public FileStorage.StoredFile myFile(Long userId, Long documentId) {
		requireDriver(userId);
		DriverDocument document = documentRepository.findByIdAndDriverUserId(documentId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		return fileStorage.load(document.getFileKey());
	}

	/** 탈퇴할 때. 서류는 민감 정보라 파일과 이력을 모두 지운다 (07 인계서 8장) */
	@Transactional
	public void deleteAllOf(Long userId) {
		List<DriverDocument> documents = documentRepository.findAllByDriverUserIdOrderByCreatedAtDescIdDesc(userId);
		if (documents.isEmpty()) {
			return;
		}
		eventRepository.deleteAllByDriverUserId(userId);
		documentRepository.deleteAll(documents);
		documentRepository.flush();
		documents.forEach(document -> fileStorage.delete(document.getFileKey()));
	}

	private Driver requireDriver(Long userId) {
		User user = userRepository.findById(userId)
				.filter(found -> found.getStatus() != UserStatus.WITHDRAWN)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (user.getRole() != UserRole.DRIVER) {
			throw new BusinessException(ErrorCode.DOCUMENT_NOT_DRIVER);
		}
		return driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_DRIVER));
	}
}
