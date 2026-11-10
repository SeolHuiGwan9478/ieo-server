package com.ieo.server.domain.document.controller;

import com.ieo.server.domain.document.dto.AdminDriverReviewResponse;
import com.ieo.server.domain.document.dto.AdminReviewListResponse;
import com.ieo.server.domain.document.dto.DocumentResponse;
import com.ieo.server.domain.document.dto.RejectRequest;
import com.ieo.server.domain.document.enums.ReviewFilter;
import com.ieo.server.domain.document.service.AdminDocumentService;
import com.ieo.server.global.auth.AuthInterceptor;
import com.ieo.server.global.storage.FileStorage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 서류 심사 API (07 인계서 7장). /api/admin/** 은 AdminInterceptor가 운영자(ADMIN)만 통과시킨다.
 * 서류 파일은 여기서만 내려준다. 공개 주소를 만들지 않고, 열 때마다 이력을 남긴다
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDocumentController {

	private final AdminDocumentService adminDocumentService;

	/** 심사 목록. 차주 한 명이 한 줄. status 탭(기본 심사 대기), q는 이름·휴대폰 뒷자리·차량 번호 */
	@GetMapping("/driver-documents")
	public AdminReviewListResponse list(
			@RequestParam(defaultValue = "PENDING") ReviewFilter status,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminDocumentService.list(status, q, page, size);
	}

	/** 차주별 상세: 대조할 앱 입력값, 서류별 상태와 이전 제출, 처리 이력 */
	@GetMapping("/drivers/{driverId}/documents")
	public AdminDriverReviewResponse driver(@PathVariable Long driverId) {
		return adminDocumentService.driver(driverId);
	}

	@GetMapping("/driver-documents/{documentId}/file")
	public ResponseEntity<byte[]> file(@RequestAttribute(AuthInterceptor.USER_ID) Long adminId, @PathVariable Long documentId) {
		FileStorage.StoredFile file = adminDocumentService.file(adminId, documentId);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.contentType(MediaType.parseMediaType(file.contentType()))
				.body(file.content());
	}

	@PostMapping("/driver-documents/{documentId}/approve")
	public DocumentResponse approve(@RequestAttribute(AuthInterceptor.USER_ID) Long adminId, @PathVariable Long documentId) {
		return adminDocumentService.approve(adminId, documentId);
	}

	/** 사업자등록증의 국세청 진위확인을 다시 한다 (심사 대기일 때만). 결과는 응답의 businessCheck */
	@PostMapping("/driver-documents/{documentId}/business-check")
	public DocumentResponse recheckBusiness(@RequestAttribute(AuthInterceptor.USER_ID) Long adminId, @PathVariable Long documentId) {
		return adminDocumentService.recheckBusiness(adminId, documentId);
	}

	@PostMapping("/driver-documents/{documentId}/reject")
	public DocumentResponse reject(
			@RequestAttribute(AuthInterceptor.USER_ID) Long adminId,
			@PathVariable Long documentId,
			@Valid @RequestBody RejectRequest request) {
		return adminDocumentService.reject(adminId, documentId, request);
	}
}
