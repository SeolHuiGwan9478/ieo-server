package com.ieo.server.domain.delivery.service;

import com.ieo.server.domain.delivery.dto.DeliveryProofResponse;
import com.ieo.server.domain.delivery.dto.IssueDeliveryProofRequest;
import com.ieo.server.domain.delivery.dto.ProofPhotoResponse;
import com.ieo.server.domain.delivery.dto.SignDeliveryProofRequest;
import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.entity.ProofPhoto;
import com.ieo.server.domain.delivery.enums.ProofPhotoType;
import com.ieo.server.domain.delivery.repository.DeliveryProofRepository;
import com.ieo.server.domain.delivery.repository.ProofPhotoRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.service.LedgerRecorder;
import com.ieo.server.domain.site.entity.Site;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.domain.trip.service.TripService;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.storage.FileStorage;
import com.ieo.server.global.util.KoreanDate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 전자인수증 (04 인수증). 차주가 링크를 발급하고(차량 화면 QR), 수하인이 로그인 없이 링크로 열어 서명한다.
 * 서명하거나 차주가 종이 인수증 사진을 올리면 인수가 끝난 것으로 본다:
 * 하차지에 있는 운행은 완료하고, 인수증 결제의 지급 예정일을 그날부터 다시 센다 (POD-008).
 * TODO: 주선사 전달(POD-007)과 ETA 알림에 링크 넣기(POD-002)는 09 알림을 만든 뒤 붙인다
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryProofService {

	private static final String FOLDER = "delivery";

	private final DeliveryProofRepository proofRepository;
	private final ProofPhotoRepository photoRepository;
	private final TripRepository tripRepository;
	private final DriverRepository driverRepository;
	private final TripService tripService;
	private final LedgerRecorder ledgerRecorder;
	private final FileStorage fileStorage;
	private final Clock clock;

	public record Issued(DeliveryProofResponse proof, boolean created) {
	}

	/** 차주: 인수증 링크 발급. 이미 있으면 같은 링크 (POD-001, 003) */
	public Issued issue(Long userId, Long tripId, IssueDeliveryProofRequest request) {
		Trip trip = findMyTrip(userId, tripId);
		if (trip.getStatus() == TripStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.DELIVERY_TRIP_CANCELLED);
		}
		Optional<DeliveryProof> existing = proofRepository.findByTripId(tripId);
		if (existing.isPresent()) {
			return new Issued(toResponse(existing.get()), false);
		}
		Double lat = request == null ? null : request.lat();
		Double lng = request == null ? null : request.lng();
		if (lat != null && lng != null) {
			Site.validateLocation(lat, lng);
		} else {
			lat = null;
			lng = null;
		}
		return new Issued(toResponse(proofRepository.save(DeliveryProof.issue(trip, lat, lng))), true);
	}

	/** 차주: 인수증 상태 (차량 앱이 서명됐는지 확인할 때) */
	@Transactional(readOnly = true)
	public DeliveryProofResponse getForDriver(Long userId, Long tripId) {
		findMyTrip(userId, tripId);
		return toResponse(proofRepository.findByTripId(tripId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND)));
	}

	/** 차주: 서명을 못 받을 때 종이 인수증 사진을 올린다 (POD-009). 인수가 끝난 것으로 본다 */
	public DeliveryProofResponse uploadPaperPhoto(Long userId, Long tripId, MultipartFile file) {
		findMyTrip(userId, tripId);
		DeliveryProof proof = proofRepository.findByTripId(tripId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND));
		addPhoto(proof, ProofPhotoType.PAPER, file);
		onDelivered(proof.getTrip());
		return toResponse(proof);
	}

	/** 수하인: 링크로 인수증 보기 (POD-004) */
	@Transactional(readOnly = true)
	public DeliveryProofResponse view(String token) {
		return toResponse(findOpen(token));
	}

	/** 수하인: 서명 (POD-006) */
	public DeliveryProofResponse sign(String token, SignDeliveryProofRequest request) {
		DeliveryProof proof = findOpen(token);
		if (proof.isSigned()) {
			throw new BusinessException(ErrorCode.DELIVERY_ALREADY_SIGNED);
		}
		byte[] signature = ProofFiles.decodeSignature(request.signature());
		boolean withLocation = request.lat() != null && request.lng() != null;
		if (withLocation) {
			Site.validateLocation(request.lat(), request.lng());
		}
		String key = fileStorage.store(FOLDER, signature, ProofFiles.PNG);
		proof.sign(request.signerName(), key, request.issueNote(),
				withLocation ? request.lat() : null, withLocation ? request.lng() : null, clock.instant());
		onDelivered(proof.getTrip());
		return toResponse(proof);
	}

	/** 수하인: 이상 사항 사진 (POD-005) */
	public DeliveryProofResponse uploadIssuePhoto(String token, MultipartFile file) {
		DeliveryProof proof = findOpen(token);
		addPhoto(proof, ProofPhotoType.ISSUE, file);
		return toResponse(proof);
	}

	@Transactional(readOnly = true)
	public FileStorage.StoredFile signatureImage(String token) {
		DeliveryProof proof = findOpen(token);
		if (proof.getSignatureKey() == null) {
			throw new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND);
		}
		return fileStorage.load(proof.getSignatureKey());
	}

	@Transactional(readOnly = true)
	public FileStorage.StoredFile photo(String token, Long photoId) {
		DeliveryProof proof = findOpen(token);
		ProofPhoto photo = photoRepository.findById(photoId)
				.filter(found -> found.getDeliveryProof().getId().equals(proof.getId()))
				.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND));
		return fileStorage.load(photo.getFileKey());
	}

	/** 인수가 끝남: 하차지에 있는 운행은 완료하고, 인수증 결제의 지급 예정일을 오늘부터 다시 센다 */
	private void onDelivered(Trip trip) {
		Instant now = clock.instant();
		if (trip.getStatus() == TripStatus.AT_DROPOFF) {
			tripService.completeTrip(trip, now);
		}
		ledgerRecorder.onDeliveryProofSigned(trip, KoreanDate.of(now));
	}

	private void addPhoto(DeliveryProof proof, ProofPhotoType type, MultipartFile file) {
		if (photoRepository.countByDeliveryProofId(proof.getId()) >= ProofPhoto.MAX_PER_PROOF) {
			throw new BusinessException(ErrorCode.DELIVERY_PHOTO_LIMIT);
		}
		ProofFiles.Photo photo = ProofFiles.readPhoto(file);
		String key = fileStorage.store(FOLDER, photo.content(), photo.contentType());
		photoRepository.save(ProofPhoto.of(proof, type, key));
	}

	private DeliveryProofResponse toResponse(DeliveryProof proof) {
		List<ProofPhoto> photos = photoRepository.findAllByDeliveryProofIdOrderByIdAsc(proof.getId());
		boolean hasPaper = photos.stream().anyMatch(photo -> photo.getType() == ProofPhotoType.PAPER);
		return DeliveryProofResponse.of(proof, proof.status(hasPaper),
				photos.stream().map(photo -> ProofPhotoResponse.of(photo, proof.getToken())).toList());
	}

	/** 없는 토큰과 만료된 링크는 똑같이 404 */
	private DeliveryProof findOpen(String token) {
		return proofRepository.findByToken(token)
				.filter(proof -> !proof.isExpired(clock.instant()))
				.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND));
	}

	/** 남의 운행이면 있는지도 알려 주지 않도록 404 */
	private Trip findMyTrip(Long userId, Long tripId) {
		Driver driver = driverRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_DRIVER));
		if (driver.getUser().getStatus() == UserStatus.WITHDRAWN) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		return tripRepository.findById(tripId)
				.filter(trip -> trip.isOwnedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
	}
}
