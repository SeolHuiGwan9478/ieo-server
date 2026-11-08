package com.ieo.server.domain.user.service.social;

import com.ieo.server.domain.user.enums.SocialProvider;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로그인 확인. 콘솔이 받아 온 인가 코드를 액세스 토큰으로 바꾸고(kauth), 그 토큰으로 사용자 정보(kapi)를 읽는다.
 * 사용자 ID는 카카오 회원번호(id)다. 이메일은 사용자가 동의했을 때만 온다.
 */
@Slf4j
@Component
public class KakaoCodeVerifier implements SocialIdentityVerifier {

	private final SocialProperties.Kakao kakao;
	private final RestClient auth;
	private final RestClient api;

	public KakaoCodeVerifier(SocialProperties properties) {
		SocialProperties.Kakao settings = properties.kakao();
		boolean configured = settings != null && settings.restApiKey() != null && !settings.restApiKey().isBlank();
		this.kakao = configured ? settings : null;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.auth = RestClient.builder().baseUrl(configured ? settings.authBaseUrl() : "https://kauth.kakao.com").requestFactory(factory).build();
		this.api = RestClient.builder().baseUrl(configured ? settings.apiBaseUrl() : "https://kapi.kakao.com").requestFactory(factory).build();
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.KAKAO;
	}

	@Override
	public boolean isConfigured() {
		return kakao != null;
	}

	@Override
	public SocialProfile verify(String code, String redirectUri) {
		if (kakao == null) {
			throw new BusinessException(ErrorCode.AUTH_SOCIAL_NOT_CONFIGURED);
		}
		if (redirectUri == null || redirectUri.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "카카오 로그인에는 redirectUri가 필요합니다.");
		}
		try {
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("grant_type", "authorization_code");
			form.add("client_id", kakao.restApiKey());
			form.add("redirect_uri", redirectUri);
			form.add("code", code);
			if (kakao.clientSecret() != null && !kakao.clientSecret().isBlank()) {
				form.add("client_secret", kakao.clientSecret());
			}
			TokenBody token = auth.post().uri("/oauth/token")
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(TokenBody.class);
			if (token == null || token.access_token() == null) {
				throw rejected();
			}
			UserBody user = api.get().uri("/v2/user/me")
					.header("Authorization", "Bearer " + token.access_token())
					.retrieve()
					.body(UserBody.class);
			if (user == null || user.id() == null) {
				throw rejected();
			}
			String email = user.kakao_account() == null ? null : user.kakao_account().email();
			return new SocialProfile(SocialProvider.KAKAO, String.valueOf(user.id()), email);
		} catch (RestClientException e) {
			// 응답 본문에 토큰이 들어 있을 수 있어 종류만 남긴다
			log.info("카카오 로그인 확인 실패: {}", e.getClass().getSimpleName());
			throw rejected();
		}
	}

	private static BusinessException rejected() {
		return new BusinessException(ErrorCode.AUTH_SOCIAL_PROVIDER_REJECTED);
	}

	/** 카카오 응답 필드 이름 그대로 (snake_case) */
	record TokenBody(String access_token) {
	}

	record UserBody(Long id, Map<String, Object> properties, KakaoAccount kakao_account) {
	}

	record KakaoAccount(String email) {
	}
}
