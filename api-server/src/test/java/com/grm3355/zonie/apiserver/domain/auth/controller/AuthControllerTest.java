package com.grm3355.zonie.apiserver.domain.auth.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginRequest;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginResponse;
import com.grm3355.zonie.apiserver.domain.auth.service.AuthService;
import com.grm3355.zonie.apiserver.domain.auth.service.RedisTokenService;
import com.grm3355.zonie.apiserver.domain.auth.util.CookieProperties;
import com.grm3355.zonie.apiserver.global.jwt.JwtAccessDeniedHandler;
import com.grm3355.zonie.apiserver.global.jwt.JwtAuthenticationEntryPoint;
import com.grm3355.zonie.apiserver.global.service.RateLimitingService;
import com.grm3355.zonie.commonlib.global.util.JwtTokenProvider;

@DisplayName("토큰 발행 통합테스트")
@WebMvcTest(
	controllers = AuthController.class,
	excludeAutoConfiguration = {
		DataSourceAutoConfiguration.class,
		JpaRepositoriesAutoConfiguration.class
	}
)
@AutoConfigureMockMvc(addFilters = false) //시큐리티 제외
@Import(CookieProperties.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private RateLimitingService rateLimitingService;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private UserDetailsService userDetailsService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@MockitoBean
	private JwtAccessDeniedHandler jwtAccessDeniedHandler;

	@MockitoBean
	private RedisTokenService redisTokenService;

	@Test
	@DisplayName("카카오 로그인후 callback")
	void loginWithKakaoShouldReturnHtmlAndSetCookie() throws Exception {
		// given: 카카오 로그인 코드
		String code = "testCode";

		// 가짜 로그인 응답
		LoginResponse loginResponse = new LoginResponse(
			"mock-access-token",
			"mock-refresh-token"
		);
		given(authService.login(any(LoginRequest.class))).willReturn(loginResponse);

		// when: 컨트롤러 호출
		mockMvc.perform(get("/api/auth/kakao/callback")
				.param("code", code)
				.param("state", "http://localhost:8082/kakao/callback")
			)
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("http://localhost:8082/kakao/callback?accessToken=mock-access-token"));

		// then: authService.login이 호출되었는지 검증
		verify(authService, times(1)).login(any(LoginRequest.class));
	}

	@Test
	@DisplayName("새로운 액세스 토큰 발급")
	void newAccessTokenSuccess() throws Exception {
		// given
		String oldRefreshToken = "mock-old-refresh-token";
		String newAccessToken = "mock-new-access-token";
		String newRefreshToken = "mock-new-refresh-token";

		LoginResponse loginResponse = new LoginResponse(newAccessToken, newRefreshToken);

		// Redis 토큰 검증 모킹
		given(redisTokenService.validateRefreshToken(oldRefreshToken)).willReturn(true);

		// AuthService 토큰 재발급 모킹
		given(authService.refreshAccessToken(oldRefreshToken)).willReturn(loginResponse);

		// when & then
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refreshToken", oldRefreshToken)))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessToken").value(newAccessToken))
			.andExpect(cookie().value("refreshToken", newRefreshToken))
			.andDo(result -> {
				MockHttpServletResponse response = result.getResponse();
				System.out.println("Response Cookie: " + response.getHeader("Set-Cookie"));
				System.out.println("Response Body: " + response.getContentAsString());
			});
	}

	@Test
	@DisplayName("refreshToken 존재여부 실패")
	void refreshTokenFailed() throws Exception {
		mockMvc.perform(post("/api/auth/refresh"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("refreshToken 존재하고 Redis에 존재하지 않아서 실패")
	void refreshTokenSuccessRedisFailed() throws Exception {
		String invalidToken = "invalid-token";

		given(redisTokenService.validateRefreshToken(invalidToken)).willReturn(false);

		// when & then
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refreshToken", invalidToken)))
			.andExpect(status().isUnauthorized());
	}

}
