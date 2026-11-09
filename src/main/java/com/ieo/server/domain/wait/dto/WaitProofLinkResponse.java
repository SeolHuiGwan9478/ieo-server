package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.wait.entity.WaitProof;
import com.ieo.server.global.config.WebConfig;
import java.time.Instant;

/** 차주에게 주는 증빙 링크. path(대기 증빙 페이지)를 웹 주소 뒤에 붙여 주선사에게 보낸다 */
public record WaitProofLinkResponse(String shareToken, String path, Instant expiresAt, Instant sentAt, boolean canSend) {

	public static WaitProofLinkResponse from(WaitProof proof, boolean canSend) {
		return new WaitProofLinkResponse(proof.getShareToken(), WebConfig.WAIT_PROOF_PAGE + "/" + proof.getShareToken(),
				proof.getExpiresAt(), proof.getSentAt(), canSend);
	}
}
