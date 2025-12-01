package com.grm3355.zonie.apiserver.domain.test.controller;

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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grm3355.zonie.apiserver.domain.auth.dto.AuthResponse;
import com.grm3355.zonie.apiserver.domain.auth.dto.LocationDto;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginResponse;
import com.grm3355.zonie.apiserver.domain.auth.service.AuthService;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalCreateRequest;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalResponse;
import com.grm3355.zonie.apiserver.domain.festival.service.FestivalService;
import com.grm3355.zonie.apiserver.domain.test.service.TestManagementService;
import com.grm3355.zonie.apiserver.global.jwt.JwtAccessDeniedHandler;
import com.grm3355.zonie.apiserver.global.jwt.JwtAuthenticationEntryPoint;
import com.grm3355.zonie.apiserver.global.service.RateLimitingService;
import com.grm3355.zonie.commonlib.global.util.JwtTokenProvider;

@DisplayName("TestManagementController 통합 테스트")
@WebMvcTest(
	controllers = TestManagementController.class,
	excludeAutoConfiguration = {
		DataSourceAutoConfiguration.class,
		JpaRepositoriesAutoConfiguration.class
	}
)
@AutoConfigureMockMvc(addFilters = false) //시큐리티 제외
class TestManagementControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private RateLimitingService rateLimitingService;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private FestivalService festivalService;

	@MockitoBean
	private TestManagementService testManagementService;

	@MockitoBean
	private UserDetailsService userDetailsService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@MockitoBean
	private JwtAccessDeniedHandler jwtAccessDeniedHandler;

	@Test
	@DisplayName("[AUTH] 테스트 유저 로그인 성공")
	void getTestTokenSuccess() throws Exception {
		LoginResponse mockResponse = new LoginResponse("test-access-token", "test-refresh-token");
		given(authService.generateTestToken(eq("test_user_01_kakao"), eq(false)))
			.willReturn(mockResponse);

		mockMvc.perform(post("/api/v1/test-management/auth/login-as")
				.param("userId", "test_user_01_kakao")
				.param("nonExpiring", "false")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessToken").value("test-access-token"));
	}

	@Test
	@DisplayName("[AUTH] Guest 토큰 발급 성공")
	void registerGuestSuccess() throws Exception {
		LocationDto locationDto = new LocationDto(37.5665, 126.9780);

		AuthResponse mockResponse = new AuthResponse("guest-access-token-123", null);
		given(authService.register(any(LocationDto.class))).willReturn(mockResponse);

		mockMvc.perform(post("/api/v1/test-management/auth/tokens")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(locationDto)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessToken").value("guest-access-token-123"))
			.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	@DisplayName("[FESTIVAL] 테스트 축제 생성 성공")
	void createFestivalSuccess() throws Exception {
		FestivalCreateRequest createRequest = FestivalCreateRequest.builder().lat(37.123).lon(127.123).build();
		FestivalResponse mockResponse = FestivalResponse.builder().festivalId(999L).title("테스트 축제").build();
		given(festivalService.createFestival(any(FestivalCreateRequest.class))).willReturn(mockResponse);

		mockMvc.perform(post("/api/v1/test-management/festivals")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createRequest)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.festivalId").value(999L));
	}

	@Test
	@DisplayName("[FESTIVAL] 축제 강제 삭제 (mode=CASCADE) 성공")
	void forceDeleteFestivalCascadeSuccess() throws Exception {
		// given: void 반환
		doNothing().when(testManagementService).deleteFestivalCascade(anyLong());

		mockMvc.perform(delete("/api/v1/test-management/festivals/1")
				.param("mode", "CASCADE")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent()); // 204
	}

	@Test
	@DisplayName("[CHAT] 채팅방 강제 삭제 (CASCADE) 성공")
	void forceDeleteChatRoomCascadeSuccess() throws Exception {
		// given: void 반환
		doNothing().when(testManagementService).deleteChatRoomCascade(anyString());

		mockMvc.perform(delete("/api/v1/test-management/chat-rooms/chat_room_active_01")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isNoContent()); // 204
	}

	@Test
	@DisplayName("[REDIS] Redis 채팅 키 삭제 성공")
	void flushRedisChatKeysSuccess() throws Exception {
		// given
		given(testManagementService.flushChatKeysFromRedis()).willReturn(125L);

		mockMvc.perform(delete("/api/v1/test-management/redis/flush-chat-keys")
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.deletedKeysCount").value(125));
	}
}
