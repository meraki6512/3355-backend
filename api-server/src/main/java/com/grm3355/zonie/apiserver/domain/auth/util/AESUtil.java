package com.grm3355.zonie.apiserver.domain.auth.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * AES/GCM 암호화 유틸리티 (IV와 인증 태그를 사용하여 안전성 확보)
 */
@Slf4j
@Component
public class AESUtil {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding"; // GCM 모드
	private static final int GCM_IV_LENGTH = 12; // 12 bytes (96 bits)
	private static final int GCM_TAG_LENGTH = 16; // 16 bytes (128 bits)
	private final SecretKeySpec secretKeySpec;
	private final String keyString;    // 구 버전 복호화용

	public AESUtil(@Value("${aes.key}") String base64Key) {
		// Base64로 인코딩된 키를 디코딩해 사용
		byte[] keyBytes = Base64.getDecoder().decode(base64Key);
		if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
			log.error("AES 키 길이가 유효하지 않습니다. 현재 길이: {} 바이트. 16, 24, 32 바이트 중 하나를 사용해야 합니다.", keyBytes.length);
		}
		this.secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
		this.keyString = base64Key;
	}

	/**
	 * 데이터를 AES/GCM으로 암호화하고 IV를 포함하여 Base64 문자열로 반환합니다.
	 * 반환 형식: [IV(12 bytes) | CipherText+Tag]
	 */
	//AES (대칭키, 양방향)
	public String encrypt(String plainText) throws Exception {
		if (plainText == null || plainText.isEmpty()) {
			return null;
		}

		// 1. IV 생성 - random 하게
		byte[] iv = new byte[GCM_IV_LENGTH];
		new SecureRandom().nextBytes(iv);

		// 2. Cipher 초기화
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv); // tLen: 128 bits
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec);

		// 3. 암호화 실행
		byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

		// 4. IV와 암호화된 데이터 결합
		ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
		byteBuffer.put(iv);
		byteBuffer.put(cipherText);

		// 5. Base64 인코딩해 반환
		return Base64.getEncoder().encodeToString(byteBuffer.array());
	}

	/**
	 * IV가 포함된 Base64 문자열을 복호화하여 평문 문자열로 반환합니다.
	 */
	public String decrypt(String encryptedText) throws Exception {
		if (encryptedText == null || encryptedText.isEmpty()) {
			return null;
		}

		// 1. Base64 디코딩
		byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);

		if (decodedBytes.length < GCM_IV_LENGTH) {
			// 데이터 길이가 IV 길이보다 작으면 에러
			// throw new IllegalArgumentException("암호화된 데이터가 너무 짧습니다.");
			log.info("[decrypt] 암호화된 데이터가 너무 짧습니다.");
		}

		if (decodedBytes.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
			log.warn("[decrypt] 구 버전(ECB) 데이터로 추정, decryptECB로 복호화 시도.");
			return decryptEcb(encryptedText);
		}

		// 2. IV와 암호화 데이터 분리
		ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
		byte[] iv = new byte[GCM_IV_LENGTH];
		byteBuffer.get(iv);    // IV 추출
		byte[] cipherTextWithTag = new byte[byteBuffer.remaining()];
		byteBuffer.get(cipherTextWithTag);    // 암호문 + 인증 태그 추출

		// 3. Cipher 초기화
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
		cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec);

		// 4. 복호화 실행
		// 인증 실패 시 BadPaddingException 또는 AEADBadTagException 발생
		byte[] plainTextBytes = cipher.doFinal(cipherTextWithTag);

		return new String(plainTextBytes, StandardCharsets.UTF_8);
	}

	/**
	 * [임시] 구 버전 AES/ECB/PKCS5Padding 방식으로 복호화합니다.
	 * : 데이터 마이그레이션 완료 후 삭제될 예정입니다.
	 */
	public String decryptEcb(String encryptedText) throws Exception {
		SecretKeySpec keySpec = new SecretKeySpec(keyString.getBytes(), ALGORITHM);
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, keySpec);
		byte[] decoded = Base64.getDecoder().decode(encryptedText);
		return new String(cipher.doFinal(decoded));
	}
}