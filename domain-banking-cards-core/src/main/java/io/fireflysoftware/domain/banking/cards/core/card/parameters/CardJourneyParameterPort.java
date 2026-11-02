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

import reactor.core.publisher.Mono;

/**
 * Card issuance defaults read from configuration.
 * The default adapter is {@link SandboxCardJourneyParameterAdapterImpl}.
 * An installer replaces it with {@link ConfigurationCardJourneyParameterAdapterImpl}
 * by setting {@code firefly.cards.parameters.adapter=configuration}.
 */
public interface CardJourneyParameterPort {

    Mono<CardIssuanceDefaults> issuanceDefaults();

    /**
     * @param currencyCode ISO 4217 code, three letters
     * @param tenureYears years until expiration when the caller does not set one
     */
    record CardIssuanceDefaults(String currencyCode, int tenureYears) {
    }
}
