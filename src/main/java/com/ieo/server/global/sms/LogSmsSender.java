package com.ieo.server.global.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실제로 보내지 않고 로그만 찍는다. 개발 중에는 로그에서 인증번호를 확인한다.
 * TODO: 문자 업체 연동 후 운영에서는 이 구현체를 쓰지 않는다.
 */
@Slf4j
@Component
public class LogSmsSender implements SmsSender {

	@Override
	public void send(String phone, String message) {
		log.info("[문자 발송] to={} message={}", phone, message);
	}
}
