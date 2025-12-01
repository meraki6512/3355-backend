package com.grm3355.zonie.apiserver.domain.user.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import com.grm3355.zonie.apiserver.domain.auth.dto.UserProfileResponse;
import com.grm3355.zonie.apiserver.domain.auth.dto.UserQuitResponse;
import com.grm3355.zonie.apiserver.domain.auth.util.CookieProperties;
import com.grm3355.zonie.apiserver.domain.user.service.UserService;
import com.grm3355.zonie.apiserver.global.jwt.UserDetailsImpl;
import com.grm3355.zonie.apiserver.global.service.RateLimitingService;
import com.grm3355.zonie.commonlib.global.util.JwtTokenProvider;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false) //시큐리티 제외
@Import(CookieProperties.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private RateLimitingService rateLimitingService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("내 프로필조회 성공")
	@WithMockUser(roles = "USER")
	void userProfileSuccess() throws Exception {

		// given: UserDetailsImpl 생성
		UserDetailsImpl userDetails = new UserDetailsImpl(
			"test-user", "password", "test@example.com",
			null, false, false
		);

		// SecurityContext에 인증 객체 설정
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(userDetails, null, List.of(() -> "ROLE_USER")
			);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		// 서비스 Mocking: getUserProfile 반환값 지정
		UserProfileResponse mockProfile = new UserProfileResponse(
			"test-user", "test@example.com", LocalDateTime.now()
		);
		when(userService.getUserProfile("test-user")).thenReturn(mockProfile);

		// when: /logout 요청
		mockMvc.perform(get("/api/v1/user/me")
				.with(SecurityMockMvcRequestPostProcessors.authentication(authentication)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("success")))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.userId").value("test-user"))
			.andExpect(jsonPath("$.data.accountEmail").value("test@example.com"));

	}

	@Test
	@WithMockUser(username = "test-user", roles = "USER") // SecurityContext 자동 설정
	@DisplayName("회원 탈퇴")
	void quitShouldDeleteUserAndClearCookie() throws Exception {

		// given: UserDetailsImpl 생성
		UserDetailsImpl userDetails = new UserDetailsImpl(
			"test-user", "password", "test@example.com",
			null, false, false
		);

		// SecurityContext에 인증 객체 설정
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(userDetails, null, List.of(() -> "ROLE_USER")
			);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		// given: 사용자 탈퇴 요청 DTO
		UserQuitResponse request = new UserQuitResponse("No longer needed");

		// userService.quit 호출 무시 (void 메서드)
		doNothing().when(userService).quit(eq("test-user"), any(UserQuitResponse.class));

		// when: /api/v1/user/me/quit 요청
		MockHttpServletResponse response = mockMvc.perform(post("/api/v1/user/me/quit")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"" + request + "\"}"))
			.andExpect(status().isNoContent()) // 204 No Content
			.andReturn()
			.getResponse();

		// then: 쿠키 삭제 확인
		String setCookie = response.getHeader("Set-Cookie");
		assert setCookie != null && setCookie.contains("refreshToken=;");

		// 서비스 호출 검증
		verify(userService, times(1)).quit(eq("test-user"), any(UserQuitResponse.class));
	}
}
