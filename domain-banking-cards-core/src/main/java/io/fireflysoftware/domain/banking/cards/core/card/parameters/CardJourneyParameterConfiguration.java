/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.domain.banking.cards.core.card.parameters;

import io.fireflysoftware.common.config.sdk.api.EnvironmentConfigurationsApi;
import io.fireflysoftware.common.config.sdk.invoker.ApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CardJourneyParameterConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "firefly.cards.parameters",
            name = "adapter",
            havingValue = "sandbox",
            matchIfMissing = true)
    public CardJourneyParameterPort sandboxCardJourneyParameterPort() {
        return new SandboxCardJourneyParameterAdapterImpl();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "firefly.cards.parameters",
            name = "adapter",
            havingValue = "configuration")
    public CardJourneyParameterPort configurationCardJourneyParameterPort(
            @Value("${firefly.cards.parameters.core-base-url:${ENDPOINT_CORE_CONFIG_MGMT:http://localhost:8080}}") String baseUrl) {
        ApiClient client = new ApiClient();
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        client.setBasePath(trimmed);
        return new ConfigurationCardJourneyParameterAdapterImpl(new EnvironmentConfigurationsApi(client));
    }
}
