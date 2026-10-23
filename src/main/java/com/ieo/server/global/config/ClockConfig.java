package com.ieo.server.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간에 따라 달라지는 로직(만료, 횟수 제한)을 테스트에서 고정할 수 있게 Clock을 주입받아 쓴다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
