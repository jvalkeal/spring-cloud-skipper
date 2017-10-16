/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.skipper.client;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.cloud.skipper.ReleaseNotFoundException;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;

/**
 * A {@link ResponseErrorHandler} used in clients RestTemplate to throw
 * various exceptions.
 *
 * @author Janne Valkealahti
 *
 */
public class SkipperClientResponseErrorHandler extends DefaultResponseErrorHandler {

	private ObjectMapper objectMapper;

	/**
	 * Instantiates a new skipper client response error handler.
	 *
	 * @param objectMapper the object mapper
	 */
	public SkipperClientResponseErrorHandler(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "'objectMapper' must be set");
		this.objectMapper = objectMapper;
	}

	@Override
	public void handleError(ClientHttpResponse response) throws IOException {
		String releaseName = null;
		Integer releaseVersion = null;
		try {
			String body = new String(getResponseBody(response));
			@SuppressWarnings("unchecked")
			Map<String, String> map = objectMapper.readValue(body, Map.class);
			if (ObjectUtils.nullSafeEquals(map.get("exception"), ReleaseNotFoundException.class.getName())) {
				releaseName = map.get("releaseName");
				releaseVersion = Integer.parseInt(map.get("releaseVersion"));
			}
		}
		catch (Exception e) {
			// don't want to error here
		}
		if (StringUtils.hasText(releaseName)) {
			if (releaseVersion != null) {
				throw new ReleaseNotFoundException(releaseName, releaseVersion);
			}
			else {
				throw new ReleaseNotFoundException(releaseName);
			}
		}
		super.handleError(response);
	}
}
