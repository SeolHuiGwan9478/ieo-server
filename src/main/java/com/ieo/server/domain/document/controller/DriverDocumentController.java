package com.ieo.server.domain.document.controller;

import com.ieo.server.domain.document.dto.BusinessInput;
import com.ieo.server.domain.document.dto.DocumentResponse;
import com.ieo.server.domain.document.dto.DriverDocumentsResponse;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.service.DriverDocumentService;
import com.ieo.server.global.auth.AuthInterceptor;
import com.ieo.server.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 차주 서류 제출 (폰 앱). 07 인계서 7장의 차주 쪽 API */
@RestController
@RequestMapping("/api/drivers/me/documents")
@RequiredArgsConstructor
public class DriverDocumentController {

	private final DriverDocumentService driverDocumentService;

	/** 서류별 최신 상태와 반려 사유, 인증 여부 */
	@GetMapping
	public DriverDocumentsResponse list(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
		return driverDocumentService.myDocuments(userId);
	}

	/**
	 * multipart: type(서류 종류), file(JPG·PNG 5MB 이하). 새 건이 심사 대기로 생긴다.
	 * 사업자등록증은 businessNumber(123-45-67890)·openedOn(2020-01-31)이 필수, ownerName은 가입자 이름과 다를 때만.
	 * 서버가 국세청 진위확인을 바로 돌려 응답의 businessCheck에 결과를 준다
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse submit(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@RequestParam("type") DocumentType type,
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String businessNumber,
			@RequestParam(required = false) String openedOn,
			@RequestParam(required = false) String ownerName) {
		return driverDocumentService.submit(userId, type, file, new BusinessInput(businessNumber, openedOn, ownerName));
	}

	/** 내가 올린 서류 사진. 로그인 없이 열리는 주소를 만들지 않는다 */
	@GetMapping("/{documentId}/file")
	public ResponseEntity<byte[]> file(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long documentId) {
		FileStorage.StoredFile file = driverDocumentService.myFile(userId, documentId);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.contentType(MediaType.parseMediaType(file.contentType()))
				.body(file.content());
	}
}
