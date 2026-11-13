package com.ieo.server.global.push;

import java.util.List;
import java.util.Map;

/** 휴대폰 푸시 보내기. 받는 기기 토큰과 앱이 열 화면 정보(data)만 넘긴다 */
public interface PushSender {

	/**
	 * 토큰마다 보내고, 더는 쓸 수 없는 토큰(앱 삭제·만료)을 돌려준다. 호출한 쪽이 지운다.
	 * 실패해도 예외를 던지지 않는다: 푸시는 앱 안 알림을 돕는 것이라 다른 일을 막지 않는다
	 */
	List<String> send(List<String> tokens, String title, String body, Map<String, String> data);
}
