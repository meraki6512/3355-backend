package com.grm3355.zonie.apiserver.domain.auth.dto;

import com.grm3355.zonie.commonlib.global.enums.ProviderType;

public record LoginRequest(ProviderType providerType, String code) {
}
