package com.ieo.server.domain.voice.dto;

import com.ieo.server.domain.voice.classifier.YesNo;

/** @param answer YES, NO, UNKNOWN */
public record ClassifyAnswerResponse(YesNo answer) {
}
