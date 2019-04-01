/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.cloud.skipper.server.deployer.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;

public class DeployerConfigurationMetadataResolver implements ApplicationContextAware {

	private static final String CONFIGURATION_METADATA_PATTERN = "classpath*:/META-INF/spring-configuration-metadata.json";
	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	public List<ConfigurationMetadataProperty> resolve() {
		List<ConfigurationMetadataProperty> metadataProperties = new ArrayList<>();
		ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();
		try {
			Resource[] resources = applicationContext.getResources(CONFIGURATION_METADATA_PATTERN);
			for (Resource resource : resources) {
				builder.withJsonResource(resource.getInputStream());
			}
		}
		catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		ConfigurationMetadataRepository metadataRepository = builder.build();
		Map<String, ConfigurationMetadataProperty> properties = metadataRepository.getAllProperties();
		properties.entrySet().stream().forEach(e -> {
			if (e.getKey().startsWith("spring.cloud.deployer.")) {
				metadataProperties.add(e.getValue());
				//System.out.println("XXX " + e.getKey() + " " + e.getValue().getId());
			}
		});
		return metadataProperties;
	}

}
