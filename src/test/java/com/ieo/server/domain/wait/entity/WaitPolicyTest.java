package com.ieo.server.domain.wait.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.domain.broker.entity.Broker;
import com.ieo.server.domain.wait.enums.WaitRounding;
import com.ieo.server.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class WaitPolicyTest {

	private final Broker broker = Broker.create("한빛로지스", "1234567891", "제2026-001호", null);

	@Test
	void 요구사항_예시대로_계산한다() {
		WaitPolicy policy = WaitPolicy.create(broker, 120, 30, 10_000, 50_000, WaitRounding.UP);

		assertThat(policy.calculate(100)).isZero();          // 1시간 40분: 무료 이내
		assertThat(policy.calculate(130)).isEqualTo(10_000); // 2시간 10분: 초과 10분 → 1단위
		assertThat(policy.calculate(245)).isEqualTo(50_000); // 4시간 5분: 초과 125분 → 5단위
		assertThat(policy.calculate(360)).isEqualTo(50_000); // 6시간: 8단위 → 상한
	}

	@Test
	void 내림이면_다_채운_단위만_센다() {
		WaitPolicy policy = WaitPolicy.create(broker, 120, 30, 10_000, 50_000, WaitRounding.DOWN);

		assertThat(policy.calculate(130)).isZero();
		assertThat(policy.calculate(150)).isEqualTo(10_000);
	}

	@Test
	void 단위_시간이_0이거나_상한이_단위_금액보다_작으면_거부한다() {
		assertThatThrownBy(() -> WaitPolicy.create(broker, 120, 0, 10_000, 50_000, WaitRounding.UP))
				.extracting("errorCode").isEqualTo(ErrorCode.WAIT_INVALID_POLICY);
		assertThatThrownBy(() -> WaitPolicy.create(broker, 120, 30, 10_000, 5_000, WaitRounding.UP))
				.extracting("errorCode").isEqualTo(ErrorCode.WAIT_INVALID_POLICY);
	}
}
