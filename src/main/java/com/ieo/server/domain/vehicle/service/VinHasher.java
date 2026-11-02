package com.ieo.server.domain.vehicle.service;

import com.ieo.server.global.util.Hashes;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pleos VIN을 서버 비밀 키로 해시한다. 원래 VIN은 저장하지 않는다.
 * VIN은 형식이 정해져 있어 그냥 SHA-256이면 하나씩 대입해 되돌릴 수 있어서 HMAC을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class VinHasher {

	private final VinProperties properties;

	public String hash(String vin) {
		String normalized = vin.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
		return Hashes.hmacSha256Hex(properties.hashSecret(), normalized);
	}
}
