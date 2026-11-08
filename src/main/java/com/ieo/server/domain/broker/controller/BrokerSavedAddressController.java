package com.ieo.server.domain.broker.controller;

import com.ieo.server.domain.broker.dto.SaveBrokerAddressRequest;
import com.ieo.server.domain.broker.dto.SavedBrokerAddressResponse;
import com.ieo.server.domain.broker.service.BrokerSavedAddressService;
import com.ieo.server.global.auth.AuthInterceptor;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers/me/saved-addresses")
@RequiredArgsConstructor
public class BrokerSavedAddressController {
    private final BrokerSavedAddressService service;

    @GetMapping
    public List<SavedBrokerAddressResponse> list(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return service.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedBrokerAddressResponse save(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
            @Valid @RequestBody SaveBrokerAddressRequest request) {
        return service.save(userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }
}
