package com.ieo.server.domain.document.controller;

import com.ieo.server.domain.document.dto.AdminBrokerReviewListResponse;
import com.ieo.server.domain.document.dto.AdminBrokerReviewResponse;
import com.ieo.server.domain.document.dto.DocumentResponse;
import com.ieo.server.domain.document.dto.RejectRequest;
import com.ieo.server.domain.document.enums.ReviewFilter;
import com.ieo.server.domain.document.service.AdminBrokerDocumentService;
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

/** 운영자의 주선사 서류 심사 API (07 인계서 13장). /api/admin/** 은 AdminInterceptor가 운영자(ADMIN)만 통과시킨다 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBrokerDocumentController {

	private final AdminBrokerDocumentService adminBrokerDocumentService;

	/** 심사 목록. 회사 하나가 한 줄. status 탭(기본 심사 대기), q는 상호·사업자번호·허가 번호 */
	@GetMapping("/broker-documents")
	public AdminBrokerReviewListResponse list(
			@RequestParam(defaultValue = "PENDING") ReviewFilter status,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return adminBrokerDocumentService.list(status, q, page, size);
	}

	/** 회사별 상세: 대조할 회사 등록 정보, 서류별 상태와 이전 제출, 처리 이력 */
	@GetMapping("/brokers/{brokerId}/documents")
	public AdminBrokerReviewResponse broker(@PathVariable Long brokerId) {
		return adminBrokerDocumentService.broker(brokerId);
	}

	@GetMapping("/broker-documents/{documentId}/file")
	public ResponseEntity<byte[]> file(@RequestAttribute(AuthInterceptor.USER_ID) Long adminId, @PathVariable Long documentId) {
		FileStorage.StoredFile file = adminBrokerDocumentService.file(adminId, documentId);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.contentType(MediaType.parseMediaType(file.contentType()))
				.body(file.content());
	}

	@PostMapping("/broker-documents/{documentId}/approve")
	public DocumentResponse approve(@RequestAttribute(AuthInterceptor.USER_ID) Long adminId, @PathVariable Long documentId) {
		return adminBrokerDocumentService.approve(adminId, documentId);
	}

	/** 사업자등록증의 국세청 진위확인을 다시 한다 (심사 대기일 때만) */
	@PostMapping("/broker-documents/{documentId}/business-check")
	public DocumentResponse recheckBusiness(@RequestAttribute(AuthInterceptor.USER_ID) Long adminId, @PathVariable Long documentId) {
		return adminBrokerDocumentService.recheckBusiness(adminId, documentId);
	}

	@PostMapping("/broker-documents/{documentId}/reject")
	public DocumentResponse reject(
			@RequestAttribute(AuthInterceptor.USER_ID) Long adminId,
			@PathVariable Long documentId,
			@Valid @RequestBody RejectRequest request) {
		return adminBrokerDocumentService.reject(adminId, documentId, request);
	}
}
