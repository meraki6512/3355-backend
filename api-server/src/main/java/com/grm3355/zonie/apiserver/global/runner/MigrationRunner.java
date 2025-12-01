package com.grm3355.zonie.apiserver.global.runner;

import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.grm3355.zonie.apiserver.domain.user.service.UserMigrationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationRunner implements ApplicationRunner {
	private final UserMigrationService migrationService;
	private final Environment environment;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (Arrays.asList(environment.getActiveProfiles()).contains("migrate")) {
			log.warn("!!! [CRITICAL] 마이그레이션 프로필 활성화. 데이터 마이그레이션 시작. !!!");
			long migratedCount = migrationService.migrateUserEmailsToGcm();
			log.warn("!!! [CRITICAL] MIGRATION COMPLETED: {} users updated !!!", migratedCount);
		}
	}
}
