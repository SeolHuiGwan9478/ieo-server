package com.ieo.server.domain.delivery.service;

import com.ieo.server.global.error.BusinessException;
import com.ieo.server.global.error.ErrorCode;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/** 인수증 사진 검사. content type만 믿지 않고 파일 앞부분(매직 바이트)으로 PNG·JPG인지 본다 */
final class ProofFiles {

	static final String PNG = "image/png";
	static final String JPEG = "image/jpeg";
	private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

	private ProofFiles() {
	}

	record Photo(byte[] content, String contentType) {
	}

	static Photo readPhoto(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) {
			throw new BusinessException(ErrorCode.DELIVERY_INVALID_FILE);
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.DELIVERY_INVALID_FILE);
		}
		String type = detect(bytes);
		if (type == null) {
			throw new BusinessException(ErrorCode.DELIVERY_INVALID_FILE);
		}
		return new Photo(bytes, type);
	}

	private static String detect(byte[] b) {
		if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
			return PNG;
		}
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return JPEG;
		}
		return null;
	}
}
