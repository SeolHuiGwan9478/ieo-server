package com.ieo.server.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PhoneVerificationTest {

	private static final LocalDate TODAY = LocalDate.parse("2026-10-23");
	private final Instant now = Instant.parse("2026-10-23T01:00:00Z");

	@Test
	void 맞으면_인증번호를_지워서_다시_못_쓴다() {
		PhoneVerification verification = issued("hash");

		verification.verify("hash", now.plusSeconds(30));

		assertThat(verification.getCodeHash()).isNull();
		assertError(() -> verification.verify("hash", now.plusSeconds(40)), ErrorCode.AUTH_CODE_EXPIRED);
	}

	@Test
	void 다시_받기는_1분_뒤부터() {
		PhoneVerification verification = issued("hash");

		assertError(() -> verification.issueCode("hash2", now.plusSeconds(59), TODAY), ErrorCode.AUTH_CODE_TOO_SOON);
		verification.issueCode("hash2", now.plus(PhoneVerification.RESEND_INTERVAL), TODAY);
	}

	@Test
	void 하루에_5번까지만_보내고_다음_날_다시_센다() {
		PhoneVerification verification = PhoneVerification.create("01012345678");
		Instant at = now;
		for (int i = 0; i < PhoneVerification.DAILY_SEND_LIMIT; i++) {
			verification.issueCode("hash" + i, at, TODAY);
			at = at.plus(Duration.ofMinutes(1));
		}

		Instant sixth = at;
		assertError(() -> verification.issueCode("hash", sixth, TODAY), ErrorCode.AUTH_CODE_DAILY_LIMIT);
		verification.issueCode("hash", sixth, TODAY.plusDays(1));
		assertThat(verification.getSendCount()).isEqualTo(1);
	}

	@Test
	void 틀리면_남은_횟수를_알려주고_5번_틀리면_10분_잠근다() {
		PhoneVerification verification = issued("hash");

		assertThatThrownBy(() -> verification.verify("wrong", now))
				.isInstanceOf(BusinessException.class)
				.hasMessage("인증번호가 달라요 (4번 남음)");
		for (int i = 0; i < 3; i++) {
			assertError(() -> verification.verify("wrong", now), ErrorCode.AUTH_CODE_MISMATCH);
		}
		assertError(() -> verification.verify("wrong", now), ErrorCode.AUTH_CODE_LOCKED);

		// 잠긴 동안에는 맞는 번호도, 새 인증번호도 안 된다
		assertError(() -> verification.issueCode("hash2", now.plus(Duration.ofMinutes(9)), TODAY), ErrorCode.AUTH_CODE_LOCKED);
		verification.issueCode("hash2", now.plus(PhoneVerification.LOCK_DURATION), TODAY);
		assertThat(verification.getFailedAttempts()).isZero();
	}

	@Test
	void 인증번호는_3분이_지나면_만료된다() {
		PhoneVerification verification = issued("hash");

		assertError(() -> verification.verify("hash", now.plus(PhoneVerification.CODE_TTL)), ErrorCode.AUTH_CODE_EXPIRED);
	}

	@Test
	void 받은_적_없으면_만료로_본다() {
		PhoneVerification verification = PhoneVerification.create("01012345678");

		assertError(() -> verification.verify("hash", now), ErrorCode.AUTH_CODE_EXPIRED);
	}

	private PhoneVerification issued(String codeHash) {
		PhoneVerification verification = PhoneVerification.create("01012345678");
		verification.issueCode(codeHash, now, TODAY);
		return verification;
	}

	private static void assertError(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(errorCode);
	}
}
