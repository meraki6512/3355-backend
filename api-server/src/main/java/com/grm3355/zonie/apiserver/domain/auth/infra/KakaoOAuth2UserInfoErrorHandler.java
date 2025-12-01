package com.grm3355.zonie.apiserver.domain.auth.infra;

import java.io.IOException;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;

import com.grm3355.zonie.apiserver.global.exception.InternalServerException;
import com.grm3355.zonie.commonlib.global.exception.ErrorCode;

public class KakaoOAuth2UserInfoErrorHandler extends DefaultResponseErrorHandler {

	@Override
	public void handleError(ClientHttpResponse response) throws IOException {
		try {
			super.handleError(response);
		} catch (HttpClientErrorException e) {
			throw new InternalServerException(ErrorCode.INTERNAL_SERVER_ERROR, e);
		}
	}
}
