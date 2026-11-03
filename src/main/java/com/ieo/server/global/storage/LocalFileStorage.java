package com.ieo.server.global.storage;

import com.ieo.server.global.util.RandomTokens;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 로컬 디스크 저장소. 파일 옆에 .type 파일로 content type을 남긴다.
 * TODO: 운영(클라우드)에서는 S3·NCP Object Storage 구현체로 바꾼다. 서버를 여러 대 띄우면 로컬 디스크는 공유되지 않는다
 */
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

	private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final StorageProperties properties;
	private final Clock clock;

	@Override
	public String store(String folder, byte[] content, String contentType) {
		String date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).format(DATE_PATH);
		String key = folder + "/" + date + "/" + RandomTokens.urlSafe();
		Path path = resolve(key);
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, content);
			Files.writeString(typePath(path), contentType);
		} catch (IOException e) {
			throw new UncheckedIOException("파일을 저장하지 못했습니다: " + key, e);
		}
		return key;
	}

	@Override
	public StoredFile load(String key) {
		Path path = resolve(key);
		try {
			return new StoredFile(Files.readAllBytes(path), Files.readString(typePath(path)));
		} catch (IOException e) {
			throw new UncheckedIOException("파일을 읽지 못했습니다: " + key, e);
		}
	}

	@Override
	public void delete(String key) {
		Path path = resolve(key);
		try {
			Files.deleteIfExists(path);
			Files.deleteIfExists(typePath(path));
		} catch (IOException e) {
			throw new UncheckedIOException("파일을 지우지 못했습니다: " + key, e);
		}
	}

	/** key가 기준 폴더 밖을 가리키지 못하게 막는다 */
	private Path resolve(String key) {
		Path base = Path.of(properties.localDir()).toAbsolutePath().normalize();
		Path path = base.resolve(key).normalize();
		if (!path.startsWith(base)) {
			throw new IllegalArgumentException("잘못된 파일 경로입니다: " + key);
		}
		return path;
	}

	private static Path typePath(Path path) {
		return path.resolveSibling(path.getFileName() + ".type");
	}
}
