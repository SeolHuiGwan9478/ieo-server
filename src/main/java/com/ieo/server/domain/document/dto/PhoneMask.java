package com.ieo.server.domain.document.dto;

/** 운영자 화면에는 휴대폰 가운데를 가려서 보여 준다 (07 인계서 8장). 010-****-5678 */
final class PhoneMask {

	private PhoneMask() {
	}

	static String mask(String phone) {
		if (phone == null) {
			return null;
		}
		if (phone.length() == 11) {
			return phone.substring(0, 3) + "-****-" + phone.substring(7);
		}
		if (phone.length() > 4) {
			return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
		}
		return phone;
	}
}
