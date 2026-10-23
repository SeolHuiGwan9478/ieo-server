package com.ieo.server.global.sms;

/** 문자 발송. 지금은 로그로만 남기고, 문자 업체를 정하면 구현체를 바꾼다. */
public interface SmsSender {

	void send(String phone, String message);
}
