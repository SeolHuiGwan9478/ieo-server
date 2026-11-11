package com.ieo.server.domain.voice.controller;

import com.ieo.server.domain.voice.dto.ClassifyAnswerRequest;
import com.ieo.server.domain.voice.dto.ClassifyAnswerResponse;
import com.ieo.server.domain.voice.service.VoiceAnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 차량 앱 음성 (10 음성·안전 UX). 로그인한 사용자만 부른다 (/api/**) */
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

	private final VoiceAnswerService voiceAnswerService;

	/** 예/아니오로 물은 질문의 대답을 판별한다 (UX-025). 대답은 저장하지 않는다 */
	@PostMapping("/answers/classify")
	public ClassifyAnswerResponse classify(@Valid @RequestBody ClassifyAnswerRequest request) {
		return new ClassifyAnswerResponse(voiceAnswerService.classify(request.question(), request.answer()));
	}
}
