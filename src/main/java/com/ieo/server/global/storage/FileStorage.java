package com.ieo.server.global.storage;

/**
 * 파일(서명 이미지, 사진) 저장소. 지금은 로컬 디스크(LocalFileStorage)를 쓰고,
 * 서버를 클라우드에 올릴 때 S3·NCP Object Storage 구현체로 바꾼다.
 * key는 저장소 안의 경로(예: delivery/2026/11/03/xxxx.png)이고, DB에는 key만 저장한다.
 */
public interface FileStorage {

	/** 저장하고 key를 돌려준다. folder는 key 앞부분(예: delivery) */
	String store(String folder, byte[] content, String contentType);

	StoredFile load(String key);

	void delete(String key);

	record StoredFile(byte[] content, String contentType) {
	}
}
