package com.ieo.server.domain.delivery.service;

import com.ieo.server.domain.delivery.dto.DeliveryProofResponse;
import com.ieo.server.domain.delivery.dto.ProofPhotoResponse;
import com.ieo.server.domain.delivery.entity.DeliveryProof;
import com.ieo.server.domain.delivery.entity.ProofPhoto;
import com.ieo.server.domain.delivery.enums.DeliveryProofStatus;
import com.ieo.server.domain.delivery.repository.DeliveryProofRepository;
import com.ieo.server.domain.delivery.repository.ProofPhotoRepository;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.ledger.service.LedgerRecorder;
import com.ieo.server.domain.notification.service.BrokerNotifier;
import com.ieo.server.domain.trip.entity.Trip;
import com.ieo.server.domain.trip.enums.PaymentType;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
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
 * 인수증 (04 인수증). 결제 방식이 인수증인 운행만 받는다.
 * 차주가 하차지에 도착한 뒤 종이 인수증 사진을 휴대폰으로 올리고(5장까지), [주선사에게 보내기]를 누른다.
 * 보내면 장부의 지급 예정일을 보낸 날부터 다시 세고(LEDG-020), 주선사에게 문자로 알린다. 주선사는 콘솔에서 사진을 본다
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
	private final LedgerRecorder ledgerRecorder;
	private final BrokerNotifier brokerNotifier;
	private final FileStorage fileStorage;
	private final Clock clock;

	/** 차주: 인수증 상태와 사진. 아직 없으면 NONE */
	@Transactional(readOnly = true)
	public DeliveryProofResponse get(Long userId, Long tripId) {
		Trip trip = findMyTrip(userId, tripId);
		return toResponse(trip, proofRepository.findByTripId(tripId).orElse(null));
	}

	/** 차주: 종이 인수증 사진 한 장 올리기 (POD-009) */
	public DeliveryProofResponse addPhoto(Long userId, Long tripId, MultipartFile file) {
		Trip trip = findMyTrip(userId, tripId);
		requireUploadable(trip);
		DeliveryProof proof = proofRepository.findByTripId(tripId)
				.orElseGet(() -> proofRepository.save(DeliveryProof.create(trip)));
		proof.requireNotSent();
		if (photoRepository.countByDeliveryProofId(proof.getId()) >= ProofPhoto.MAX_PER_PROOF) {
			throw new BusinessException(ErrorCode.DELIVERY_PHOTO_LIMIT);
		}
		ProofFiles.Photo photo = ProofFiles.readPhoto(file);
		String key = fileStorage.store(FOLDER, photo.content(), photo.contentType());
		photoRepository.save(ProofPhoto.of(proof, key));
		return toResponse(trip, proof);
	}

	/** 차주: 보내기 전에 잘못 찍은 사진 지우기 */
	public DeliveryProofResponse deletePhoto(Long userId, Long tripId, Long photoId) {
		Trip trip = findMyTrip(userId, tripId);
		DeliveryProof proof = findProof(tripId);
		proof.requireNotSent();
		ProofPhoto photo = findPhoto(proof, photoId);
		photoRepository.delete(photo);
		photoRepository.flush();
		fileStorage.delete(photo.getFileKey());
		return toResponse(trip, proof);
	}

	/** 차주: [주선사에게 보내기] (POD-011). 사진이 한 장 이상 있어야 한다 */
	public DeliveryProofResponse send(Long userId, Long tripId) {
		Trip trip = findMyTrip(userId, tripId);
		DeliveryProof proof = proofRepository.findByTripId(tripId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NO_PHOTO));
		proof.requireNotSent();
		if (photoRepository.countByDeliveryProofId(proof.getId()) == 0) {
			throw new BusinessException(ErrorCode.DELIVERY_NO_PHOTO);
		}
		Instant now = clock.instant();
		proof.send(now);
		ledgerRecorder.onReceiptSent(trip, KoreanDate.of(now));
		if (trip.getBroker() != null) {
			brokerNotifier.send(trip.getBroker(), "[이에오] " + trip.getVehicle().getPlateNumber()
					+ " 차량의 인수증 사진이 도착했어요. 콘솔 운행 상세에서 볼 수 있어요.");
		}
		return toResponse(trip, proof);
	}

	/** 차주: 내 운행의 인수증 사진 */
	@Transactional(readOnly = true)
	public FileStorage.StoredFile photoForDriver(Long userId, Long tripId, Long photoId) {
		findMyTrip(userId, tripId);
		return fileStorage.load(findPhoto(findProof(tripId), photoId).getFileKey());
	}

	/** 주선사: 보낸 인수증의 사진. 운행이 그 주선사 것인지는 부르는 쪽이 확인한다 */
	@Transactional(readOnly = true)
	public FileStorage.StoredFile sentPhoto(Trip trip, Long photoId) {
		DeliveryProof proof = proofRepository.findByTripId(trip.getId())
				.filter(DeliveryProof::isSent)
				.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND));
		return fileStorage.load(findPhoto(proof, photoId).getFileKey());
	}

	private static void requireUploadable(Trip trip) {
		if (trip.getPaymentType() != PaymentType.RECEIPT) {
			throw new BusinessException(ErrorCode.DELIVERY_NOT_RECEIPT);
		}
		if (trip.getStatus() == TripStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.DELIVERY_TRIP_CANCELLED);
		}
		if (!reachedDropoff(trip)) {
			throw new BusinessException(ErrorCode.DELIVERY_TOO_EARLY);
		}
	}

	private static boolean reachedDropoff(Trip trip) {
		return trip.getStatus() == TripStatus.AT_DROPOFF || trip.getStatus() == TripStatus.COMPLETED;
	}

	private DeliveryProofResponse toResponse(Trip trip, DeliveryProof proof) {
		List<ProofPhoto> photos = proof == null ? List.of()
				: photoRepository.findAllByDeliveryProofIdOrderByIdAsc(proof.getId());
		boolean sent = proof != null && proof.isSent();
		boolean canUpload = trip.getPaymentType() == PaymentType.RECEIPT && reachedDropoff(trip)
				&& !sent && photos.size() < ProofPhoto.MAX_PER_PROOF;
		return new DeliveryProofResponse(
				trip.getId(),
				trip.getPaymentType() == PaymentType.RECEIPT,
				proof == null ? DeliveryProofStatus.NONE : proof.status(photos.size()),
				proof == null ? null : proof.getSentAt(),
				photos.stream().map(photo -> ProofPhotoResponse.forDriver(photo, trip.getId())).toList(),
				ProofPhoto.MAX_PER_PROOF,
				canUpload);
	}

	private DeliveryProof findProof(Long tripId) {
		return proofRepository.findByTripId(tripId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND));
	}

	private ProofPhoto findPhoto(DeliveryProof proof, Long photoId) {
		Optional<ProofPhoto> photo = photoRepository.findById(photoId)
				.filter(found -> found.getDeliveryProof().getId().equals(proof.getId()));
		return photo.orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_PROOF_NOT_FOUND));
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
