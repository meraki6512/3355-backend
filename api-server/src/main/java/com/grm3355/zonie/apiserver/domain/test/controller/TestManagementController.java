package com.grm3355.zonie.apiserver.domain.test.controller;

import java.net.URI;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grm3355.zonie.apiserver.domain.auth.dto.AuthResponse;
import com.grm3355.zonie.apiserver.domain.auth.dto.LocationDto;
import com.grm3355.zonie.apiserver.domain.auth.dto.LoginResponse;
import com.grm3355.zonie.apiserver.domain.auth.service.AuthService;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalCreateRequest;
import com.grm3355.zonie.apiserver.domain.festival.dto.FestivalResponse;
import com.grm3355.zonie.apiserver.domain.festival.service.FestivalService;
import com.grm3355.zonie.apiserver.domain.test.service.TestManagementService;
import com.grm3355.zonie.apiserver.global.swagger.ApiError400;
import com.grm3355.zonie.apiserver.global.swagger.ApiError405;
import com.grm3355.zonie.apiserver.global.swagger.ApiError415;
import com.grm3355.zonie.apiserver.global.swagger.ApiError429;
import com.grm3355.zonie.commonlib.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 개발 및 QA용 테스트 API
 */
// @Profile("!prod") // prod 환경 -> 비활성화 (이 빈을 로드하지 않음) // 추후 주석 제거 예정
@RestController
@Hidden
@Tag(name = "Test Management (Dev)", description = "개발 및 QA용 데이터 강제 생성/삭제 API (테스트용; 운영 시 삭제 예정)")
@RequestMapping("/api/v1/test-management")
@RequiredArgsConstructor
@ApiError400 // 공통 400
@ApiError405 // 공통 405
@ApiError415 // 공통 415
@ApiError429 // 공통 429
public class TestManagementController {

	private final AuthService authService;
	private final FestivalService festivalService;
	private final TestManagementService testManagementService;

	@Operation(summary = "[AUTH] 테스트 유저 로그인 (OAuth 생략)",
		description = "테스트용 유저 ID (e.g., test_user_01_kakao)로 즉시 JWT 토큰을 발급받습니다.<br>"
			+ "'nonExpiring=true'로 요청 시, 비만료(10년 만기)의 토큰을 발급합니다."
	)
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "테스트 로그인 성공. 토큰 발급 완료.",
			content = @Content(mediaType = "application/json",
				schema = @Schema(implementation = LoginResponse.class),
				examples = @ExampleObject(
					name = "LoginSuccess",
					value = "{\"success\":true,\"data\":{\"accessToken\":\"eyJ...\",\"refreshToken\":\"eyJ...\"},\"error\":null,\"timestamp\":\"2025-11-16T12:05:00Z\"}"
				)
			)
		),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
	})
	@PostMapping("/auth/login-as")
	public ResponseEntity<ApiResponse<LoginResponse>> getTestToken(
		@RequestParam(defaultValue = "test_user_01_kakao") String userId,
		@RequestParam(defaultValue = "false") boolean nonExpiring
	) {
		LoginResponse response = authService.generateTestToken(userId, nonExpiring);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "[AUTH] 임시 사용자 토큰 발급 (기존 /api/auth/tokens)",
		description = "위경도 정보를 입력받아 임시 사용자 Access 토큰을 발급합니다."
	)
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "201",
			description = "Guest 유저 생성 및 토큰 발급 성공",
			content = @Content(mediaType = "application/json",
				schema = @Schema(implementation = AuthResponse.class),
				examples = @ExampleObject(
					name = "CREATED",
					value = "{\"success\":true,\"data\":{\"accessToken\":\"eyJ...\",\"refreshToken\":null},\"error\":null,\"timestamp\":\"2025-11-16T12:10:00Z\"}"
				)
			)
		)
		// 400은 클래스 레벨에서 처리
	})
	@PostMapping("/auth/tokens")
	public ResponseEntity<?> registerGuest(@Valid @RequestBody LocationDto locationDto, HttpServletRequest request) {
		URI location = URI.create(Objects.requireNonNull(request.getRequestURI()));
		return ResponseEntity.created(location)
			.body(ApiResponse.success(authService.register(locationDto)));
	}

	@Operation(summary = "[FESTIVAL] 테스트용 축제 생성 (기존 /api/v1/festivals)",
		description = "개발 테스트를 위해 새로운 축제 레코드를 생성합니다."
	)
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "테스트 축제 생성 성공",
			content = @Content(mediaType = "application/json",
				schema = @Schema(implementation = FestivalResponse.class),
				examples = @ExampleObject(
					name = "FestivalCreated",
					value = "{\"success\":true,\"data\":{\"festivalId\":999,\"title\":\"테스트 축제 #12345\",\"addr1\":\"자동 생성된 임시 주소 (테스트)\",\"eventStartDate\":\"2025-11-13\",\"eventEndDate\":\"2025-11-19\",\"firstImage\":\"http://test.image.url/sample.jpg\",\"lat\":37.123456,\"lon\":127.123456,\"region\":\"SEOUL\",\"chatRoomCount\":0,\"totalParticipantCount\":0},\"error\":null,\"timestamp\":\"2025-11-16T12:15:00Z\"}"
				)
			)
		)
		// 400은 클래스 레벨에서 처리
	})
	@PostMapping("/festivals")
	public ResponseEntity<?> createFestival(@RequestBody FestivalCreateRequest request) {
		FestivalResponse response = festivalService.createFestival(request);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(summary = "[FESTIVAL] 축제 강제 삭제 (채팅방, 메시지 포함)",
		description = "축제 및 관련 데이터를 강제로 삭제합니다.<br>"
			+ "- **mode=ERROR (default)**: 관련된 채팅방이 1개라도 있으면 에러를 반환합니다. (400 Bad Request)<br>"
			+ "- **mode=CASCADE**: 축제와 관련된 모든 채팅방(JPA), 참여자(JPA), 메시지(MongoDB)를 연쇄적으로 삭제합니다. <b>(DB 부하 주의)</b>"
	)
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", ref = "#/components/responses/NoContent"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
		// 400 (mode=ERROR 실패)은 클래스 레벨에서 처리
	})
	@DeleteMapping("/festivals/{festivalId}")
	public ResponseEntity<ApiResponse<Void>> forceDeleteFestival(
		@PathVariable long festivalId,
		@Parameter(description = "삭제 모드 (ERROR 또는 CASCADE)")
		@RequestParam(defaultValue = "ERROR") String mode
	) {
		if ("CASCADE".equalsIgnoreCase(mode)) {
			testManagementService.deleteFestivalCascade(festivalId);
		} else {
			testManagementService.deleteFestivalSafe(festivalId);
		}
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "[CHAT] 채팅방 강제 삭제 (메시지 포함)",
		description = "채팅방 및 관련 데이터를 강제로 삭제합니다.<br>"
			+ "채팅방(JPA), 참여자(JPA), 메시지(MongoDB)를 연쇄적으로 삭제합니다. <b>(DB 부하 주의)</b>"
	)
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", ref = "#/components/responses/NoContent"),
		// ApiSuccess204
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
		// ApiError404
	})
	@DeleteMapping("/chat-rooms/{chatRoomId}")
	public ResponseEntity<ApiResponse<Void>> forceDeleteChatRoom(
		@PathVariable String chatRoomId
	) {
		testManagementService.deleteChatRoomCascade(chatRoomId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "[REDIS] 채팅/좋아요 관련 Redis 키 전체 삭제",
		description = "테스트 환경에서 쌓인 Redis의 가비지 데이터를 삭제합니다.<br>"
			+ "- `message:*` (좋아요, 좋아요 수)<br>"
			+ "- `chatroom:*` (참여자 수, 마지막 대화 등)<br>"
			+ "<b>(주의: 세션(refreshToken) 및 위치(locationToken) 키는 삭제하지 않습니다.)</b>"
	)
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "200",
			description = "Redis 키 삭제 성공",
			content = @Content(mediaType = "application/json",
				examples = @ExampleObject(
					name = "FlushSuccess",
					value = "{\"success\":true,\"data\":{\"deletedKeysCount\":125},\"error\":null,\"timestamp\":\"2025-11-16T12:30:00Z\"}"
				)
			)
		)
	})
	@DeleteMapping("/redis/flush-chat-keys")
	public ResponseEntity<ApiResponse<Object>> flushRedisChatKeys() {
		long count = testManagementService.flushChatKeysFromRedis();
		return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("deletedKeysCount", count)));
	}
}
