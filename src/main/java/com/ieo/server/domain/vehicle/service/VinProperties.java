package com.ieo.server.domain.vehicle.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ieo.vin")
public record VinProperties(String hashSecret) {
}
