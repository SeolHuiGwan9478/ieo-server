package com.ieo.server.domain.broker.dto;

import com.ieo.server.domain.broker.entity.BrokerSavedAddress;

public record SavedBrokerAddressResponse(Long id, String name, String address, String detailAddress, double lat, double lng) {
    public static SavedBrokerAddressResponse from(BrokerSavedAddress saved) {
        return new SavedBrokerAddressResponse(saved.getId(), saved.getName(), saved.getAddress(),
                saved.getDetailAddress(), saved.getLat(), saved.getLng());
    }
}
