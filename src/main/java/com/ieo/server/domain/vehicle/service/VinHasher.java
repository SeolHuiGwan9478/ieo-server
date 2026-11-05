package com.ieo.server.domain.vehicle.service;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import com.ieo.server.global.util.Hashes;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pleos VIN을 서버 비밀 키로 해시한다. 원래 VIN은 저장하지 않는다.
 * VIN은 형식이 정해져 있어 그냥 SHA-256이면 하나씩 대입해 되돌릴 수 있어서 HMAC을 쓴다.
 * 띄어쓰기를 빼고 대문자로 바꾼 뒤 VIN 형식(17자리, I·O·Q 제외)인지 본다. 차대번호를 모르는 차는
 * "invalid" 같은 글자를 주는데, 그대로 해시하면 그런 차들이 모두 한 트럭으로 묶여 버린다.
 */
@Component
@RequiredArgsConstructor
public class VinHasher {

	private static final Pattern VIN_PATTERN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

	private final VinProperties properties;

	public String hash(String vin) {
		String normalized = vin.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
		if (!VIN_PATTERN.matcher(normalized).matches()) {
			throw new BusinessException(ErrorCode.VEHICLE_INVALID_VIN);
		}
		return Hashes.hmacSha256Hex(properties.hashSecret(), normalized);
	}
}
