package com.grm3355.zonie.apiserver.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.grm3355.zonie.commonlib.global.enums.ProviderType;

public record OAuth2LoginRequest(
	@NotNull ProviderType providerType,
	@NotBlank String code) {
}
