package com.ieo.server.global.push;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 테스트에서 FCM 대신 쓰는 발송기. 보낸 것을 모아 두고, [gone]에 넣은 토큰은 더는 쓸 수 없다고 돌려준다.
 * 모든 테스트 문맥이 같이 쓰도록 빈으로 둔다 (@MockitoBean은 문맥을 새로 만들어 DB 연결이 모자란다)
 */
@Primary
@Component
public class RecordingPushSender implements PushSender {

	public record Sent(List<String> tokens, String title, String body, Map<String, String> data) {
	}

	public final List<Sent> sent = new ArrayList<>();
	public final List<String> gone = new ArrayList<>();

	@Override
	public synchronized List<String> send(List<String> tokens, String title, String body, Map<String, String> data) {
		sent.add(new Sent(List.copyOf(tokens), title, body, Map.copyOf(data)));
		return tokens.stream().filter(gone::contains).toList();
	}

	public synchronized void clear() {
		sent.clear();
		gone.clear();
	}
}
