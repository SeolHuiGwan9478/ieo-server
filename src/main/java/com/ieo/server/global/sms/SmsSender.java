package com.ieo.server.global.sms;

/**
 * 문자 발송. 로그인 인증번호에만 쓴다. 주선사·차주에게 가는 알림 문자와 알림톡은 뺐다 (2026-11-13).
 * 지금은 로그로만 남기고, 문자 업체를 정하면 구현체를 바꾼다.
 */
public interface SmsSender {

	void send(String phone, String message);
}
