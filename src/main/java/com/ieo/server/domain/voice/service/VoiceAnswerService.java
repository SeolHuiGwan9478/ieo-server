package com.ieo.server.domain.voice.service;

import com.ieo.server.domain.voice.classifier.AnswerClassifier;
import com.ieo.server.domain.voice.classifier.Classification;
import com.ieo.server.domain.voice.classifier.YesNo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 음성 대답 판별 (UX-025). 확신이 낮으면 "모름"으로 돌려 앱이 다시 묻게 한다 */
@Service
@RequiredArgsConstructor
public class VoiceAnswerService {

	/** 이보다 낮게 확신하면 "모름" */
	static final double MIN_CONFIDENCE = 0.7;

	private final AnswerClassifier classifier;

	public YesNo classify(String question, String answer) {
		Classification result = classifier.classify(question, answer);
		return result.confidence() >= MIN_CONFIDENCE ? result.answer() : YesNo.UNKNOWN;
	}
}
