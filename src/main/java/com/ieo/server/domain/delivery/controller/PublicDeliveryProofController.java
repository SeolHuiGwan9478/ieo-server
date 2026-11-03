package com.ieo.server.domain.delivery.controller;

import com.ieo.server.domain.delivery.dto.DeliveryProofResponse;
import com.ieo.server.domain.delivery.dto.SignDeliveryProofRequest;
import com.ieo.server.domain.delivery.service.DeliveryProofService;
import com.ieo.server.global.storage.FileStorage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 수하인이 링크로 여는 전자인수증 (POD-004~006). 로그인 없이 열린다.
 * 서명 화면(웹 페이지)은 나중에 웹 개발 때 만들고, 이 API를 부른다
 */
@RestController
@RequestMapping("/api/public/delivery-proofs/{token}")
@RequiredArgsConstructor
public class PublicDeliveryProofController {

	private final DeliveryProofService deliveryProofService;

	/** 운행 정보(상하차지, 화물, 차량)와 서명 상태 */
	@GetMapping
	public DeliveryProofResponse view(@PathVariable String token) {
		return deliveryProofService.view(token);
	}

	/** 서명. signature는 PNG data URL */
	@PostMapping("/sign")
	public DeliveryProofResponse sign(@PathVariable String token, @Valid @RequestBody SignDeliveryProofRequest request) {
		return deliveryProofService.sign(token, request);
	}

	/** 이상 사항 사진 (multipart, 필드 이름 file) */
	@PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DeliveryProofResponse uploadIssuePhoto(@PathVariable String token, @RequestPart("file") MultipartFile file) {
		return deliveryProofService.uploadIssuePhoto(token, file);
	}

	@GetMapping("/signature")
	public ResponseEntity<byte[]> signature(@PathVariable String token) {
		return image(deliveryProofService.signatureImage(token));
	}

	@GetMapping("/photos/{photoId}")
	public ResponseEntity<byte[]> photo(@PathVariable String token, @PathVariable Long photoId) {
		return image(deliveryProofService.photo(token, photoId));
	}

	private static ResponseEntity<byte[]> image(FileStorage.StoredFile file) {
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).body(file.content());
	}
}
