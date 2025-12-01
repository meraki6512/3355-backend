package com.grm3355.zonie.apiserver.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grm3355.zonie.apiserver.domain.auth.util.AESUtil;
import com.grm3355.zonie.commonlib.domain.user.entity.User;
import com.grm3355.zonie.commonlib.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserMigrationService {

	private final UserRepository userRepository;
	private final AESUtil aesUtil; // 새로운 AES/GCM 로직이 주입됨

	/**
	 * 구 버전 AES/ECB 암호화된 이메일을 읽고, 신 버전 AES/GCM으로 마이그레이션하는 메서드
	 * 이 메서드는 오직 1회성으로 사용되어야 하며, 실행 중 트랜잭션 충돌을 방지해야 합니다.
	 * @return 마이그레이션된 사용자 수
	 */
	@Transactional // 쓰기 작업이므로 @Transactional 필요
	public long migrateUserEmailsToGcm() {
		log.warn("=== [SECURITY] STARTING USER EMAIL MIGRATION TO AES/GCM ===");

		// 1. 모든 사용자 데이터 로드
		List<User> users = userRepository.findAll();

		long migratedCount = 0;

		for (User user : users) {
			String encryptedEmail = user.getAccountEmail();

			// 2. 이미 신 버전으로 암호화되었거나, 이메일이 유효하지 않은 스킵
			if (encryptedEmail == null || encryptedEmail.trim().isEmpty() || encryptedEmail.length() < 10
				|| encryptedEmail.length() > 50) {
				continue;
			}

			try {
				// 3. 구 버전 (AES/ECB) 방식으로 복호화 시도
				String decryptedEmail = aesUtil.decryptEcb(encryptedEmail);

				// 4.신 버전 (AES/GCM)으로 재암호화
				String newEncryptedEmail = aesUtil.encrypt(decryptedEmail);

				// 5. DB애 User 엔티티 업데이트 및 저장
				user.updateEmail(newEncryptedEmail);
				userRepository.save(user); // 트랜잭션 내에서 변경사항 반영

				migratedCount++;
				log.info("Migrated User ID: {}", user.getUserId());

			} catch (Exception e) {
				log.error("Failed to decrypt old email for user {}: {}", user.getUserId(), e.getMessage());
				// 복호화 실패(변조 혹은 잘못된 데이터) 시 해당 사용자 건너뛰기
			}
		}

		log.warn("=== [SECURITY] MIGRATION COMPLETED: {} users updated ===", migratedCount);

		return migratedCount;

	}

}
