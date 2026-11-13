package com.ieo.server.global.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FCM으로 보낸다 (09 알림). 휴대폰 앱이 화면을 직접 그리도록 data 메시지로 보내고(제목·본문도 data에 담는다),
 * 앱이 꺼져 있어도 깨우게 우선순위를 높인다. 서비스 계정 키가 없으면 아무것도 하지 않는다
 */
@Slf4j
@Component
public class FcmPushSender implements PushSender {

	/** 앱을 지웠거나 토큰이 바뀌어 더는 쓸 수 없는 경우 */
	private static final Set<MessagingErrorCode> GONE = Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT);

	private final FirebaseMessaging messaging;

	public FcmPushSender(PushProperties properties) {
		this.messaging = properties.enabled() ? connect(properties.firebaseCredentials()) : null;
		if (messaging == null) {
			log.info("[푸시] FIREBASE_CREDENTIALS가 비어 있어 푸시를 보내지 않는다. 앱 안 알림(SSE)만 간다");
		}
	}

	@Override
	public List<String> send(List<String> tokens, String title, String body, Map<String, String> data) {
		if (messaging == null || tokens.isEmpty()) {
			return List.of();
		}
		Map<String, String> payload = new HashMap<>(data);
		payload.put("title", title);
		payload.put("body", body);
		MulticastMessage message = MulticastMessage.builder()
				.addAllTokens(tokens)
				.putAllData(payload)
				.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
				.build();
		try {
			BatchResponse response = messaging.sendEachForMulticast(message);
			List<String> gone = new ArrayList<>();
			List<SendResponse> results = response.getResponses();
			for (int i = 0; i < results.size(); i++) {
				SendResponse result = results.get(i);
				MessagingErrorCode code = result.isSuccessful() || result.getException() == null
						? null : result.getException().getMessagingErrorCode();
				if (code != null && GONE.contains(code)) {
					gone.add(tokens.get(i));
				}
			}
			return gone;
		} catch (Exception e) {
			log.warn("[푸시] 보내지 못함", e);
			return List.of();
		}
	}

	private static FirebaseMessaging connect(String credentials) {
		try (InputStream in = credentials.trim().startsWith("{")
				? new ByteArrayInputStream(credentials.getBytes(StandardCharsets.UTF_8))
				: new FileInputStream(credentials.trim())) {
			FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build();
			FirebaseApp app = FirebaseApp.getApps().stream().filter(a -> a.getName().equals("ieo")).findFirst()
					.orElseGet(() -> FirebaseApp.initializeApp(options, "ieo"));
			return FirebaseMessaging.getInstance(app);
		} catch (IOException | RuntimeException e) {
			log.warn("[푸시] Firebase 서비스 계정 키를 읽지 못해 푸시를 끈다: {}", e.getMessage());
			return null;
		}
	}
}
