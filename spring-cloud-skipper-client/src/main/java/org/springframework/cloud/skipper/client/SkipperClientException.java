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

import org.springframework.core.NestedRuntimeException;

/**
 * Base class for exceptions thrown by {@link SkipperClient} whenever it
 * encounters client-side errors.
 *
 * @author Janne Valkealahti
 *
 */
public class SkipperClientException extends NestedRuntimeException {

	private static final long serialVersionUID = 8821698104247234216L;

	/**
	 * Construct a new instance of {@code RestClientException} with the given
	 * message.
	 *
	 * @param msg the message
	 */
	public SkipperClientException(String msg) {
		super(msg);
	}

	/**
	 * Construct a new instance of {@code RestClientException} with the given
	 * message and exception.
	 *
	 * @param msg the message
	 * @param cause the exception
	 */
	public SkipperClientException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
