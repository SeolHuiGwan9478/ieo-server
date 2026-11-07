package com.ieo.server.domain.site.dto;

public record GeocodedAddressResponse(String address, double lat, double lng) {
}