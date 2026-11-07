package com.ieo.server.domain.site.service;

import com.ieo.server.domain.site.dto.GeocodedAddressResponse;
import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 주소 검색으로 선택한 기본 주소를 좌표로 변환한다. API 키는 서버에서만 사용한다. */
@Service
public class AddressGeocodingService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final KakaoLocalProperties properties;
    private final RestClient client;

    public AddressGeocodingService(KakaoLocalProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    public GeocodedAddressResponse geocode(String address) {
        if (address == null || address.isBlank() || address.length() > 200) {
            throw new BusinessException(ErrorCode.ADDRESS_INVALID);
        }
        if (properties.restApiKey() == null || properties.restApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.ADDRESS_UNAVAILABLE);
        }
        try {
            String body = client.get()
                    .uri(uri -> uri.path("/v2/local/search/address.json")
                            .queryParam("query", address.trim()).build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.restApiKey())
                    .retrieve().body(String.class);
            return parse(body);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            // 외부 응답·예외에는 주소나 인증 정보가 포함될 수 있어 노출하지 않는다.
            throw new BusinessException(ErrorCode.ADDRESS_UNAVAILABLE);
        }
    }

    static GeocodedAddressResponse parse(String body) {
        JsonNode documents = JSON.readTree(body == null ? "{}" : body).path("documents");
        if (!documents.isArray()) throw new BusinessException(ErrorCode.ADDRESS_UNAVAILABLE);
        if (documents.isEmpty()) throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        JsonNode first = documents.get(0);
        // 카카오의 x는 경도, y는 위도다.
        double lat = Double.parseDouble(first.path("y").asString());
        double lng = Double.parseDouble(first.path("x").asString());
        String address = first.path("address_name").asString();
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90
                || lng < -180 || lng > 180 || address.isBlank()) {
            throw new BusinessException(ErrorCode.ADDRESS_UNAVAILABLE);
        }
        return new GeocodedAddressResponse(address, lat, lng);
    }
}