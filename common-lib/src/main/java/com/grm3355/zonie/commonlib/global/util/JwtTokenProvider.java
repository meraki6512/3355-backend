package com.grm3355.zonie.commonlib.global.util;

import java.util.Date;

import javax.crypto.SecretKey;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.grm3355.zonie.commonlib.global.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT(JSON Web Token)의 생성, 유효성 검증 및 토큰에서 정보 추출을 담당하는 유틸리티 클래스.
 * Access Token과 Refresh Token을 발행하고 관리한다.
 */
@Slf4j
@Component
public class JwtTokenProvider {

	private static final String CLAIM_KEY_AUTH = "auth";
	private static final String CLAIM_KEY_ROLE = "role";
	private static final String CLAIM_KEY_PASSWORD = "password";

	@Value("${jwt.secret}") // application.yml에서 JWT 서명에 사용될 비밀 키를 주입받는다.
	private String secret;

	@Setter
	@Getter
	@Value("${jwt.access-token-expiration-time}") // Access Token의 만료 시간을 주입받는다.
	private long accessTokenExpirationTime;

	@Setter
	@Getter
	@Value("${jwt.location-token-expiration-time}") // location Token의 만료 시간을 주입받는다.
	private long locationTokenExpirationTime;

	@Setter
	@Getter
	@Value("${jwt.refresh-token-expiration-time}") // Refresh Token의 만료 시간을 주입받는다.
	private long refreshTokenExpirationTime;

	private SecretKey key;

	@PostConstruct
	public void init() {
		// Base64로 인코딩된 비밀 키 문자열을 디코딩하여 HMAC SHA 키로 변환한다.
		// 이 키는 JWT 서명 및 검증에 사용된다.
		byte[] keyBytes = Decoders.BASE64.decode(secret);
		this.key = Keys.hmacShaKeyFor(keyBytes);
	}

	/**
	 * 사용자 이메일과 역할을 포함하는 Access Token을 생성한다.
	 * Access Token은 짧은 만료 시간을 가지며, 실제 리소스 접근 권한을 부여하는 데 사용된다.
	 */
	public String createAccessToken(String userId, Role role) {
		return createToken(userId, role.name(), accessTokenExpirationTime);
	}

	/**
	 * Access Token 재발급을 위한 Refresh Token을 생성한다.
	 * Refresh Token은 Access Token보다 긴 만료 시간을 가지며, 일반적으로 사용자 역할 등의 민감한 정보를 포함하지 않는다.
	 */
	public String createRefreshToken(String userId) {
		return createToken(userId, null, refreshTokenExpirationTime);
	}

	/**
	 * accessToken 생성
	 * @param subject
	 * @param authClaim
	 * @param expirationTime
	 * @return
	 */
	private String createToken(String subject, String authClaim, long expirationTime) {
		Date now = new Date();
		Date expiryDate = new Date(now.getTime() + expirationTime);

		JwtBuilder builder = Jwts.builder()
			.subject(subject)
			.issuedAt(now)
			.expiration(expiryDate)
			.signWith(key);

		if (authClaim != null && !authClaim.isEmpty()) {
			builder.claim(CLAIM_KEY_AUTH, authClaim);
		}

		return builder.compact();
	}

	public void validateToken(String token) {
		Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
	}

	private Claims extractClaims(String token) {
		return Jwts.parser()
			.verifyWith(key)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public String getUserIdFromToken(String token) {
		return extractClaims(token).getSubject();
	}

	public String getRoleFromToken(String token) {
		return extractClaims(token).get(CLAIM_KEY_ROLE, String.class);
	}

	public String getPasswordFromToken(String token) {
		return extractClaims(token).get(CLAIM_KEY_PASSWORD, String.class);
	}
}
