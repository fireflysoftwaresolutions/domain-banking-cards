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


package io.fireflysoftware.domain.banking.cards.core.card.services;

import io.fireflysoftware.domain.banking.cards.core.card.commands.*;
import io.fireflysoftware.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import io.fireflysoftware.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import io.fireflysoftware.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import io.fireflysoftware.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import reactor.core.publisher.Mono;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationResponse;

public interface CardService {

    Mono<SagaResult> issueCard(IssueCardCommand command);

    Mono<CardActivationResponse> activateCard(ActivateCardCommand command);

    Mono<SagaResult> blockCard(BlockCardCommand command);

    Mono<SagaResult> unblockCard(UnblockCardCommand command);

    Mono<SagaResult> replaceCard(ReplaceCardCommand command);

    Mono<SagaResult> cancelCard(CancelCardCommand command);

    Mono<SagaResult> updateCardLimits(UpdateCardLimitsCommand command);

    Mono<SagaResult> updateSecuritySettings(UpdateSecuritySettingsCommand command);

    Mono<SagaResult> setupCreditLine(SetupCreditLineCommand command);

    Mono<SagaResult> issueVirtualCard(IssueVirtualCardCommand command);
}
