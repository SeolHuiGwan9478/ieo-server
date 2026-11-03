package com.ieo.server.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** localDir: 로컬 디스크 저장소의 기준 폴더 */
@ConfigurationProperties("ieo.storage")
public record StorageProperties(String localDir) {
}
