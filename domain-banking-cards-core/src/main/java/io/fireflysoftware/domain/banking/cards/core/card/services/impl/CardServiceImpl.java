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


package io.fireflysoftware.domain.banking.cards.core.card.services.impl;

import io.fireflysoftware.domain.banking.cards.core.card.commands.*;
import io.fireflysoftware.domain.banking.cards.core.card.services.CardService;
import io.fireflysoftware.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import io.fireflysoftware.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import io.fireflysoftware.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import io.fireflysoftware.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.fireflyframework.cqrs.command.CommandBus;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationResponse;

import static io.fireflysoftware.domain.banking.cards.core.card.utils.IssueCardConstants.*;

@Service
public class CardServiceImpl implements CardService {

    private final SagaEngine engine;

    private final CommandBus commands;

    public CardServiceImpl(SagaEngine engine, CommandBus commands) {
        this.engine = engine;
        this.commands = commands;
    }

    @Override
    public Mono<SagaResult> issueCard(IssueCardCommand command) {
        return Mono.error(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Core card issuance initiation is not configured"));
    }

    @Override
    public Mono<CardActivationResponse> activateCard(ActivateCardCommand command) {
        return commands.send(command);
    }

    @Override
    public Mono<SagaResult> blockCard(BlockCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCard", command)
                .forStepId("blockCard", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("BlockCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> unblockCard(UnblockCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCard", command)
                .forStepId("unblockCard", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("UnblockCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> replaceCard(ReplaceCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateOldCard", command)
                .forStepId("createReplacementCard", command)
                .forStepId("transferLimits", command)
                .forStepId("transferSecuritySettings", command)
                .forStepId("cancelOldCard", command)
                .forStepId("orderPhysicalReplacement", command)
                .build();
        return engine.execute("ReplaceCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> cancelCard(CancelCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCard", command)
                .forStepId("settleOutstanding", command)
                .forStepId("cancelCard", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("CancelCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> updateCardLimits(UpdateCardLimitsCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateLimits", command)
                .forStepId("updateLimits", command)
                .forStepId("notifyLimitChange", command)
                .build();
        return engine.execute("UpdateCardLimitsSaga", inputs);
    }

    @Override
    public Mono<SagaResult> updateSecuritySettings(UpdateSecuritySettingsCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateSecurityChange", command)
                .forStepId("updateSecuritySettings", command)
                .forStepId("notifySecurityChange", command)
                .build();
        return engine.execute("ManageSecuritySettingsSaga", inputs);
    }

    @Override
    public Mono<SagaResult> setupCreditLine(SetupCreditLineCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCreditRequest", command)
                .forStepId("checkCreditEligibility", command)
                .forStepId("createRevolvingLine", command)
                .forStepId("setupBillingCycle", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("SetupCreditLineSaga", inputs);
    }

    @Override
    public Mono<SagaResult> issueVirtualCard(IssueVirtualCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateParentCard", command)
                .forStepId("validateSpendingLimit", command)
                .forStepId("createVirtualCard", command)
                .forStepId("setupVirtualCardLimits", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("IssueVirtualCardSaga", inputs);
    }
}
