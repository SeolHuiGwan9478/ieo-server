package com.ieo.server.domain.voice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question 앱이 물은 말
 * @param answer   음성 인식으로 받은 차주 대답
 */
public record ClassifyAnswerRequest(
		@NotBlank @Size(max = 200) String question,
		@NotBlank @Size(max = 200) String answer) {
}
