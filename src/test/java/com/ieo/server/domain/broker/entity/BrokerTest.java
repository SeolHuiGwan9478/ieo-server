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

	@Test
	void 정산_주기는_등록할_때_넣을_수_있고_비우면_30일() {
		assertThat(Broker.create("○○물류", "1234567891", "제2026-001호", null, 45).getSettlementDays()).isEqualTo(45);
		assertThat(Broker.create("○○물류", "1234567891", "제2026-001호", null, null).getSettlementDays()).isEqualTo(30);
		assertThatThrownBy(() -> Broker.create("○○물류", "1234567891", "제2026-001호", null, 400))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_SETTLEMENT_DAYS);
	}

	@Test
	void 회사_정보를_바꾼다() {
		Broker broker = Broker.create("○○물류", "1234567891", "제2026-001호", "홍길동");

		broker.rename(" 한빛로지스 ");
		broker.changeLicenseNumber("제2026-009호");
		broker.changeRepresentativeName("  ");

		assertThat(broker.getName()).isEqualTo("한빛로지스");
		assertThat(broker.getLicenseNumber()).isEqualTo("제2026-009호");
		assertThat(broker.getRepresentativeName()).isNull();
		assertThatThrownBy(() -> broker.rename(" "))
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
		assertThatThrownBy(() -> broker.changeLicenseNumber(""))
				.extracting("errorCode").isEqualTo(ErrorCode.BROKER_INVALID_LICENSE_NUMBER);
	}
}
