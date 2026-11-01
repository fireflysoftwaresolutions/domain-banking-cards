/*
 * Copyright 2026 Firefly Software Foundation
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
import io.fireflysoftware.common.config.sdk.model.EnvironmentConfigDTO;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Reads card issuance defaults from core-common-config-mgmt.
 */
public final class ConfigurationCardJourneyParameterAdapterImpl implements CardJourneyParameterPort {

    private final EnvironmentConfigurationsApi environments;

    public ConfigurationCardJourneyParameterAdapterImpl(EnvironmentConfigurationsApi environments) {
        this.environments = environments;
    }

    @Override
    public Mono<CardIssuanceDefaults> issuanceDefaults() {
        return read(SandboxCardJourneyParameterAdapterImpl.DEFAULT_CURRENCY_KEY)
                .zipWith(read(SandboxCardJourneyParameterAdapterImpl.TENURE_YEARS_KEY))
                .map(pair -> new CardIssuanceDefaults(pair.getT1(), Integer.parseInt(pair.getT2())));
    }

    private Mono<String> read(String key) {
        return environments.filterEnvironmentConfigs(0, 200, null, null, null, null, UUID.randomUUID().toString())
                .flatMap(page -> {
                    List<EnvironmentConfigDTO> rows = page.getContent() == null ? List.of() : page.getContent();
                    for (EnvironmentConfigDTO row : rows) {
                        if (key.equals(row.getConfigKey()) && row.getConfigValue() != null && !row.getConfigValue().isBlank()) {
                            return Mono.just(row.getConfigValue());
                        }
                    }
                    return Mono.error(new IllegalArgumentException("Parameter " + key + " was not found"));
                });
    }
}
