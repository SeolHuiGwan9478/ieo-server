package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.wait.entity.WaitProof;
import java.time.Instant;

/** 차주에게 주는 증빙 링크. path를 웹 주소 뒤에 붙여 주선사에게 보낸다 */
public record WaitProofLinkResponse(String shareToken, String path, Instant expiresAt, Instant sentAt) {

	public static WaitProofLinkResponse from(WaitProof proof) {
		return new WaitProofLinkResponse(proof.getShareToken(), "/api/public/wait-proofs/" + proof.getShareToken(),
				proof.getExpiresAt(), proof.getSentAt());
	}
}
