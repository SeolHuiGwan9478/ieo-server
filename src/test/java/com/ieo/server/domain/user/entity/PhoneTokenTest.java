package com.ieo.server.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhoneTokenTest {

	private final Instant now = Instant.parse("2026-10-23T01:00:00Z");
	private final PhoneToken token = PhoneToken.issue("hash", "01012345678", "device-a", now);

	@Test
	void 받은_기기에서_10분_안에만_쓴다() {
		assertThat(token.isUsableBy("device-a", now.plusSeconds(599))).isTrue();
		assertThat(token.isUsableBy("device-b", now)).isFalse();
		assertThat(token.isUsableBy("device-a", now.plus(PhoneToken.TTL))).isFalse();
	}
}
