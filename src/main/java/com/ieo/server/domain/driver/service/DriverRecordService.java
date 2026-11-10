package com.ieo.server.domain.driver.service;

import com.ieo.server.domain.dispatch.entity.Cancellation;
import com.ieo.server.domain.dispatch.repository.CancellationRepository;
import com.ieo.server.domain.document.service.DriverDocumentService;
import com.ieo.server.domain.driver.dto.DriverRecordResponse;
import com.ieo.server.domain.driver.repository.DriverRepository;
import com.ieo.server.domain.trip.enums.TripStatus;
import com.ieo.server.domain.trip.repository.TripRepository;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차주 이력 한 곳에서 계산한다. 인증은 게이트 설정과 상관없이 실제 서류 승인 여부다 (인증 마크)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverRecordService {

	private final DriverRepository driverRepository;
	private final TripRepository tripRepository;
	private final CancellationRepository cancellationRepository;
	private final DriverDocumentService driverDocumentService;

	/** 차주 본인의 이력 */
	public DriverRecordResponse mine(Long userId) {
		driverRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.DISPATCH_NOT_DRIVER));
		return of(userId);
	}

	public DriverRecordResponse of(Long driverUserId) {
		long completed = tripRepository.countByDriverUserIdAndStatus(driverUserId, TripStatus.COMPLETED);
		long lateCancels = cancellationRepository.findAllByCanceledById(driverUserId).stream()
				.filter(Cancellation::isLate)
				.count();
		return new DriverRecordResponse(driverDocumentService.isVerified(driverUserId), completed, lateCancels);
	}
}
