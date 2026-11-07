package com.ieo.server.domain.site.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 외부 API는 로컬 HTTP 스텁으로 대체한다. Spring과 DB를 시작하지 않는다. */
class AddressGeocodingServiceTest {
    private HttpServer server;
    private AddressGeocodingService service;
    private final AtomicReference<String> body = new AtomicReference<>("""
            {"documents":[{"address_name":"경기 평택시 포승읍 평택항로 156","x":"126.8337","y":"36.9631"}]}
            """);
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/search/address.json", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status.get(), response.length);
            try (var stream = exchange.getResponseBody()) { stream.write(response); }
        });
        server.start();
        service = new AddressGeocodingService(new KakaoLocalProperties("test-key", baseUrl()));
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    @AfterEach
    void cleanup() { server.stop(0); }

    @Test
    void 한글주소와_인증헤더를_보내고_x는_경도_y는_위도로_반환한다() {
        var result = service.geocode("  경기 평택시 포승읍 평택항로 156  ");
        assertThat(result.lat()).isEqualTo(36.9631);
        assertThat(result.lng()).isEqualTo(126.8337);
        assertThat(result.address()).isEqualTo("경기 평택시 포승읍 평택항로 156");
        assertThat(authorization.get()).isEqualTo("KakaoAK test-key");
        assertThat(query.get()).isEqualTo("query=경기 평택시 포승읍 평택항로 156");
    }

    @Test
    void 빈_검색결과는_주소없음으로_반환한다() {
        body.set("{\"documents\":[]}");
        assertError(ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void 인증실패_외부응답을_사용자에게_노출하지_않는다() {
        status.set(403);
        body.set("{\"message\":\"private provider details\"}");
        assertError(ErrorCode.ADDRESS_UNAVAILABLE);
    }

    @Test
    void 잘못된_응답은_조회실패로_반환한다() {
        body.set("not json");
        assertError(ErrorCode.ADDRESS_UNAVAILABLE);
    }

    @Test
    void 잘못된_좌표를_저장하지_않는다() {
        body.set("{\"documents\":[{\"address_name\":\"주소\",\"x\":\"NaN\",\"y\":\"37\"}]}");
        assertError(ErrorCode.ADDRESS_UNAVAILABLE);
    }

    @Test
    void 키가_없으면_외부API를_부르지_않는다() {
        service = new AddressGeocodingService(new KakaoLocalProperties("", baseUrl()));
        assertError(ErrorCode.ADDRESS_UNAVAILABLE);
        assertThat(calls.get()).isZero();
    }

    @Test
    void 빈주소나_너무긴주소는_외부API를_부르지_않는다() {
        for (String address : new String[] {" ", "가".repeat(201)}) {
            assertThatThrownBy(() -> service.geocode(address)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADDRESS_INVALID));
        }
        assertThat(calls.get()).isZero();
    }

    private void assertError(ErrorCode code) {
        assertThatThrownBy(() -> service.geocode("경기 평택시"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}