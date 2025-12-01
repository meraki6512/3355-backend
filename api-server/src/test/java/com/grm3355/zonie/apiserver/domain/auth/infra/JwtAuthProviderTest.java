package com.grm3355.zonie.apiserver.domain.auth.infra;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import com.grm3355.zonie.commonlib.domain.user.entity.User;
import com.grm3355.zonie.commonlib.global.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

@DisplayNameGeneration(ReplaceUnderscores.class)
class JwtAuthProviderTest {
	private static final String SECRET_KEY = "1231231231231231223131231231231231231212312312";
	JwtAuthProvider jwtAuthProvider = new JwtAuthProvider(SECRET_KEY, 360);

	// @Test
	@DisplayName("토큰_생성_성공")
	void tokenSuccess() {
		User member = User.builder()
			.userId("s")
			.build();
		JwtParser parser = Jwts.parser()
			.setSigningKey(SECRET_KEY.getBytes())
			.build();

		String token = jwtAuthProvider.provide(member);

		assertThat(parser.isSigned(token))
			.isTrue();
	}

	@Test
	@DisplayName("토큰_생성_성공_및_클레임_확인")
	void tokenSuccessAndClaimCheck() {
		String testUserId = "test-user-id";
		String testPassword = "encrypted-password";
		Role testRole = Role.USER;

		User member = User.builder()
			.userId(testUserId)
			.role(testRole)
			.password(testPassword)
			.build();

		// 원래는 verifyWith(key) 사용
		JwtParser parser = Jwts.parser()
			.setSigningKey(SECRET_KEY.getBytes())
			.build();

		String token = jwtAuthProvider.provide(member);

		// 1. 토큰 서명 확인
		assertThat(parser.isSigned(token)).isTrue();

		// 2. Payload 클레임 확인
		Claims claims = parser.parseClaimsJws(token).getBody();

		assertThat(claims.get("userId")).isEqualTo(testUserId);
		assertThat(claims.get("role")).isEqualTo(testRole.name());
		assertThat(claims.get("password")).isEqualTo(testPassword);
	}
}
