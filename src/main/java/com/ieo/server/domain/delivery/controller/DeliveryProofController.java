package com.ieo.server.domain.delivery.controller;

import com.ieo.server.domain.delivery.dto.DeliveryProofResponse;
import com.ieo.server.domain.delivery.dto.IssueDeliveryProofRequest;
import com.ieo.server.domain.delivery.service.DeliveryProofService;
import com.ieo.server.global.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 차주의 전자인수증 (04 인수증). 차주 계정만 쓸 수 있다 */
@RestController
@RequestMapping("/api/trips/{tripId}/delivery-proof")
@RequiredArgsConstructor
public class DeliveryProofController {

	private final DeliveryProofService deliveryProofService;

	/** "인수증 받기": 링크 발급. 처음이면 201, 이미 있으면 같은 링크와 200. path를 QR로 보여 준다 */
	@PostMapping
	public ResponseEntity<DeliveryProofResponse> issue(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@RequestBody(required = false) IssueDeliveryProofRequest request) {
		DeliveryProofService.Issued issued = deliveryProofService.issue(userId, tripId, request);
		return ResponseEntity.status(issued.created() ? HttpStatus.CREATED : HttpStatus.OK).body(issued.proof());
	}

	/** 서명됐는지 확인 */
	@GetMapping
	public DeliveryProofResponse get(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return deliveryProofService.getForDriver(userId, tripId);
	}

	/** 서명을 못 받을 때 종이 인수증 사진 (multipart, 필드 이름 file) */
	@PostMapping(path = "/paper-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DeliveryProofResponse uploadPaperPhoto(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@RequestPart("file") MultipartFile file) {
		return deliveryProofService.uploadPaperPhoto(userId, tripId, file);
	}
}
