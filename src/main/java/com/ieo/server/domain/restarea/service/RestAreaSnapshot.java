package com.ieo.server.domain.restarea.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 한 번 받은 휴게소 하나: 도로공사 값에 표준데이터 좌표·상하행을 붙인 것.
 * {@link #hash()}가 같으면 저장한 휴게소에서 바뀐 것이 없다.
 */
public record RestAreaSnapshot(
		String code,
		String name,
		String routeName,
		String direction,
		String roadDirection,
		List<String> facilities,
		boolean truckRestArea,
		String phone,
		String address,
		Double latitude,
		Double longitude) {

	private static final String SEPARATOR = "\u001f";

	public RestAreaSnapshot {
		facilities = facilities == null ? List.of() : List.copyOf(facilities);
	}

	public boolean hasLocation() {
		return latitude != null && longitude != null;
	}

	public RestAreaSnapshot withLocation(Double latitude, Double longitude) {
		return new RestAreaSnapshot(code, name, routeName, direction, roadDirection, facilities, truckRestArea, phone, address,
				latitude, longitude);
	}

	public String hash() {
		String joined = Stream.of(name, routeName, direction, roadDirection, String.join("|", facilities),
						String.valueOf(truckRestArea), phone, address, coordinate(latitude), coordinate(longitude))
				.map(value -> Objects.toString(value, ""))
				.collect(Collectors.joining(SEPARATOR));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** 소수 여섯째 자리(약 10cm)까지만 본다. 같은 좌표가 표기만 달라 바뀐 것으로 보이지 않게 */
	private static String coordinate(Double value) {
		return value == null ? "" : String.format(Locale.ROOT, "%.6f", value);
	}
}
