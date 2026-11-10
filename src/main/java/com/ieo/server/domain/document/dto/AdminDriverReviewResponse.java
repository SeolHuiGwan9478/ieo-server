package com.ieo.server.domain.document.dto;

import com.ieo.server.domain.document.entity.DriverDocumentEvent;
import com.ieo.server.domain.document.enums.DocumentEventType;
import com.ieo.server.domain.document.enums.DocumentType;
import com.ieo.server.domain.document.enums.RejectReason;
import com.ieo.server.domain.driver.entity.Driver;
import com.ieo.server.domain.user.enums.UserStatus;
import com.ieo.server.domain.vehicle.entity.Vehicle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 차주별 심사 상세 (07 인계서 6.3). 서류 사진과 나란히 대조할 앱 입력값(이름·차량 번호·톤수·차종),
 * 서류별 최신 상태와 이전 제출, 처리 이력, 반려 사유 목록을 한 번에 준다
 */
public record AdminDriverReviewResponse(
		DriverInfo driver,
		VehicleInfo vehicle,
		boolean verified,
		int approvedRequired,
		int requiredTotal,
		List<DocumentTypeResponse> documents,
		List<EventResponse> events,
		List<RejectReasonResponse> rejectReasons
) {

	public record DriverInfo(Long id, String name, String phone, String licenseNumber, UserStatus status) {

		public static DriverInfo from(Driver driver) {
			return new DriverInfo(driver.getUserId(), driver.getUser().getName(), PhoneMask.mask(driver.getUser().getPhone()),
					driver.getLicenseNumber(), driver.getUser().getStatus());
		}
	}

	public record VehicleInfo(Long id, String plateNumber, String tonnageLabel, String bodyTypeLabel, BigDecimal heightM) {

		public static VehicleInfo from(Vehicle vehicle) {
			return vehicle == null ? null : new VehicleInfo(vehicle.getId(), vehicle.getPlateNumber(),
					vehicle.getTonnage().getLabel(), vehicle.getBodyType().getLabel(), vehicle.getHeightM());
		}
	}

	public record EventResponse(
			Long id,
			DocumentEventType type,
			String typeLabel,
			Long documentId,
			DocumentType documentType,
			String documentTypeLabel,
			Instant at,
			Long actorId,
			String actorName,
			String detail
	) {

		public static EventResponse of(DriverDocumentEvent event, String actorName) {
			DocumentType type = event.getDocument().getType();
			return new EventResponse(event.getId(), event.getType(), event.getType().getLabel(), event.getDocument().getId(),
					type, type.getLabel(), event.getCreatedAt(), event.getActorUserId(), actorName, event.getDetail());
		}
	}

	public record RejectReasonResponse(RejectReason code, String label, String message, boolean noteRequired) {

		public static List<RejectReasonResponse> all() {
			return Arrays.stream(RejectReason.values())
					.map(reason -> new RejectReasonResponse(reason, reason.getLabel(), reason.getMessage(), reason.requiresNote()))
					.toList();
		}
	}
}
