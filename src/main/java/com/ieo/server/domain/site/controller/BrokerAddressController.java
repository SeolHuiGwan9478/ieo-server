package com.ieo.server.domain.site.controller;

import com.ieo.server.domain.broker.service.BrokerService;
import com.ieo.server.domain.site.dto.GeocodedAddressResponse;
import com.ieo.server.domain.site.service.AddressGeocodingService;
import com.ieo.server.global.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/me/addresses")
@RequiredArgsConstructor
public class BrokerAddressController {
    private final BrokerService brokerService;
    private final AddressGeocodingService geocodingService;

    @GetMapping("/geocode")
    public GeocodedAddressResponse geocode(
            @RequestAttribute(AuthInterceptor.USER_ID) Long userId,
            @RequestParam String address) {
        brokerService.getMine(userId); // 활성 주선사 소속을 확인한 뒤 외부 API를 호출한다.
        return geocodingService.geocode(address);
    }
}