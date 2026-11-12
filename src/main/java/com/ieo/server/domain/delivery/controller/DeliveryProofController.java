package com.ieo.server.domain.delivery.controller;

import com.ieo.server.domain.delivery.dto.DeliveryProofResponse;
import com.ieo.server.domain.delivery.service.DeliveryProofService;
import com.ieo.server.global.auth.AuthInterceptor;
import com.ieo.server.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 차주의 인수증: 종이 인수증 사진을 올리고 주선사에게 보낸다 (04 인수증). 차주 계정만 쓸 수 있다 */
@RestController
@RequestMapping("/api/trips/{tripId}/delivery-proof")
@RequiredArgsConstructor
public class DeliveryProofController {

	private final DeliveryProofService deliveryProofService;

	/** 상태와 사진 목록. 아직 사진이 없으면 status NONE */
	@GetMapping
	public DeliveryProofResponse get(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return deliveryProofService.get(userId, tripId);
	}

	/** 사진 한 장 (multipart, 필드 이름 file) */
	@PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DeliveryProofResponse addPhoto(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
			@PathVariable Long tripId,
			@RequestPart("file") MultipartFile file) {
		return deliveryProofService.addPhoto(userId, tripId, file);
	}

	@DeleteMapping("/photos/{photoId}")
	public DeliveryProofResponse deletePhoto(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId, @PathVariable Long photoId) {
		return deliveryProofService.deletePhoto(userId, tripId, photoId);
	}

	@GetMapping("/photos/{photoId}")
	public ResponseEntity<byte[]> photo(
			@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId, @PathVariable Long photoId) {
		FileStorage.StoredFile file = deliveryProofService.photoForDriver(userId, tripId, photoId);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).body(file.content());
	}

	/** [주선사에게 보내기] */
	@PostMapping("/send")
	public DeliveryProofResponse send(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long tripId) {
		return deliveryProofService.send(userId, tripId);
	}
}
