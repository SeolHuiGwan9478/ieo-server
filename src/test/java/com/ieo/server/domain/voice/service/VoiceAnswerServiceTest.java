package com.ieo.server.domain.voice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ieo.server.domain.voice.classifier.Classification;
import com.ieo.server.domain.voice.classifier.YesNo;
import org.junit.jupiter.api.Test;

class VoiceAnswerServiceTest {

	@Test
	void 충분히_확신하면_그대로_낮으면_모름() {
		assertEquals(YesNo.NO, new VoiceAnswerService((q, a) -> new Classification(YesNo.NO, 0.7)).classify("q", "a"));
		assertEquals(YesNo.UNKNOWN, new VoiceAnswerService((q, a) -> new Classification(YesNo.YES, 0.69)).classify("q", "a"));
	}
}
