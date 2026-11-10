package com.ieo.server.domain.document.service;

import com.ieo.server.domain.document.business.ReviewProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 차주가 서류 심사를 통과했는지 (필수 서류 4종이 모두 승인). 게이트(ieo.review.gate-driver-requests)가 꺼져 있으면 늘 통과로 본다.
 * 통과 전에는 배차를 요청할 수 없고, 오더는 구간·운임 정도만 보인다 (정확한 주소·주선사·화물 내용은 가린다)
 */
@Component
@RequiredArgsConstructor
public class DriverVerification {

	private final DriverDocumentService driverDocumentService;
	private final ReviewProperties reviewProperties;

	public boolean isVerified(Long userId) {
		return !reviewProperties.gateDriverRequests() || driverDocumentService.isVerified(userId);
	}
}
