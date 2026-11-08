package com.ieo.server.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ieo.server.domain.user.dto.SendCodeRequest;
import com.ieo.server.domain.user.service.AuthService;
import com.ieo.server.domain.user.service.PhoneAuthService;
import com.ieo.server.domain.user.service.SocialAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthDemoCodeTest {
    private final PhoneAuthService phoneAuth = mock(PhoneAuthService.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final AuthController controller = new AuthController(phoneAuth, mock(SocialAuthService.class), mock(AuthService.class), environment);

    @Test
    void 로컬에서_명시적으로_켠_경우에만_시연번호를_보여준다() {
        environment.setActiveProfiles("local");
        environment.setProperty("ieo.auth.show-demo-code", "true");
        when(phoneAuth.sendCode("01012345678")).thenReturn("012345");
        var response = controller.sendCode(new SendCodeRequest("01012345678"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().demoCode()).isEqualTo("012345");
        assertThat(response.getBody().expiresInSeconds()).isEqualTo(180);
    }

    @Test
    void 설정이_없거나_꺼져있으면_번호를_반환하지_않는다() {
        environment.setActiveProfiles("local");
        assertHidden();
        environment.setProperty("ieo.auth.show-demo-code", "false");
        assertHidden();
    }

    @Test
    void 운영과_개발배포에서는_설정을_켜도_번호를_반환하지_않는다() {
        environment.setProperty("ieo.auth.show-demo-code", "true");
        for (String[] profiles : new String[][] {{"prod"}, {"dev"}, {"test"}, {"local", "prod"}, {"local", "dev"}}) {
            environment.setActiveProfiles(profiles);
            assertHidden();
        }
    }

    private void assertHidden() {
        var response = controller.sendCode(new SendCodeRequest("01012345678"));
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
    }
}
