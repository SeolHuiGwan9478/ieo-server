package com.ieo.server.domain.broker.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class BrokerTest {

	@Test
	void 사업자등록번호는_하이픈과_띄어쓰기를_지우고_저장한다() {
		Broker broker = Broker.create("○○물류", "123-45-67891", " 제2026-001호 ", null);

		assertThat(broker.getBusinessNumber()).isEqualTo("1234567891");
		assertThat(broker.getLicenseNumber()).isEqualTo("제2026-001호");
	}

	@Test
	void 검증_번호가_맞지_않는_사업자등록번호는_거부한다() {
		assertThatThrownBy(() -> Broker.create("○○물류", "1234567890", "제2026-001호", null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_BUSINESS_NUMBER);
	}

	@Test
	void 숫자_10자리가_아니면_거부한다() {
		assertThatThrownBy(() -> Broker.normalizeBusinessNumber("12345"))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_BUSINESS_NUMBER);
		assertThatThrownBy(() -> Broker.normalizeBusinessNumber("12345678a1"))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_BUSINESS_NUMBER);
		assertThatThrownBy(() -> Broker.normalizeBusinessNumber(null))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_BUSINESS_NUMBER);
	}

	@Test
	void 허가_번호가_없으면_만들_수_없다() {
		assertThatThrownBy(() -> Broker.create("○○물류", "1234567891", "  ", null))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_LICENSE_NUMBER);
		assertThatThrownBy(() -> Broker.create("○○물류", "1234567891", null, null))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_LICENSE_NUMBER);
	}
}
