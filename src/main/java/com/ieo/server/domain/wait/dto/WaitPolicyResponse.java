package com.ieo.server.domain.wait.dto;

import com.ieo.server.domain.wait.entity.WaitPolicy;
import com.ieo.server.domain.wait.enums.WaitRounding;
import java.time.Instant;

public record WaitPolicyResponse(
		Long id,
		int freeMinutes,
		int unitMinutes,
		int unitAmount,
		int maxAmount,
		WaitRounding rounding,
		boolean active,
		Instant createdAt) {

	public static WaitPolicyResponse from(WaitPolicy policy) {
		if (policy == null) {
			return null;
		}
		return new WaitPolicyResponse(policy.getId(), policy.getFreeMinutes(), policy.getUnitMinutes(),
				policy.getUnitAmount(), policy.getMaxAmount(), policy.getRounding(), policy.isActive(),
				policy.getCreatedAt());
	}
}
