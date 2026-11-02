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
import io.fireflysoftware.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import io.fireflysoftware.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import io.fireflysoftware.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import io.fireflysoftware.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import org.fireflyframework.cqrs.command.CommandBus;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationResponse;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardServiceImpl Tests")
class CardServiceImplTest {

    @Mock
    private SagaEngine sagaEngine;

    @Mock
    private SagaResult sagaResult;

    @Mock
    private CommandBus commands;

    private CardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CardServiceImpl(sagaEngine, commands);
    }

    @Test
    void unconfiguredIssuanceDoesNotStartFabricatedSaga() {
        var command = IssueCardCommand.builder().customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID()).cardProgramId(UUID.randomUUID()).build();
        for (int attempt = 0; attempt < 2; attempt++) {
            StepVerifier.create(service.issueCard(command)).expectErrorMatches(error ->
                    error instanceof org.springframework.web.server.ResponseStatusException failure
                    && failure.getStatusCode().value() == 503).verify();
        }
        verifyNoInteractions(sagaEngine, commands);
    }

    @Test
    @DisplayName("Activation preserves the actual owner receipt without a synthetic saga result")
    void testActivateCard_ShouldReturnOwnerReceipt() {
        ActivateCardCommand command = ActivateCardCommand.builder()
                .cardId(UUID.randomUUID()).actionId(UUID.randomUUID())
                .activationCode("syntheticIssuedPossessionProofForTransportOnly0123456789").build();
        var receipt = new CardActivationResponse(command.getActionId(), command.getCardId(),
                CardActivationResponse.State.UNCERTAIN, null);
        when(commands.<CardActivationResponse>send(command)).thenReturn(Mono.just(receipt));
        StepVerifier.create(service.activateCard(command)).expectNext(receipt).verifyComplete();
        verify(commands).send(command);
        verifyNoInteractions(sagaEngine);
    }

    @Test
    @DisplayName("Should block card successfully")
    void testBlockCard_ShouldExecuteSaga() {
        // Given
        BlockCardCommand command = BlockCardCommand.builder()
                .cardId(UUID.randomUUID())
                .reason("Suspected fraud")
                .blockedBy("CUSTOMER")
                .build();
        when(sagaEngine.execute(eq("BlockCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.blockCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("BlockCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should unblock card successfully")
    void testUnblockCard_ShouldExecuteSaga() {
        // Given
        UnblockCardCommand command = UnblockCardCommand.builder()
                .cardId(UUID.randomUUID())
                .unblockedBy("CUSTOMER")
                .build();
        when(sagaEngine.execute(eq("UnblockCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.unblockCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("UnblockCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should replace card successfully")
    void testReplaceCard_ShouldExecuteSaga() {
        // Given
        ReplaceCardCommand command = ReplaceCardCommand.builder()
                .oldCardId(UUID.randomUUID())
                .replacementReason("Lost")
                .transferLimits(true)
                .transferSecuritySettings(true)
                .build();
        when(sagaEngine.execute(eq("ReplaceCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.replaceCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("ReplaceCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should cancel card successfully")
    void testCancelCard_ShouldExecuteSaga() {
        // Given
        CancelCardCommand command = CancelCardCommand.builder()
                .cardId(UUID.randomUUID())
                .reason("Customer request")
                .cancelledBy("CUSTOMER")
                .build();
        when(sagaEngine.execute(eq("CancelCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.cancelCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("CancelCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should update card limits successfully")
    void testUpdateCardLimits_ShouldExecuteSaga() {
        // Given
        UpdateCardLimitsCommand command = UpdateCardLimitsCommand.builder()
                .cardId(UUID.randomUUID())
                .limitId(UUID.randomUUID())
                .dailyLimit(BigDecimal.valueOf(10000))
                .build();
        when(sagaEngine.execute(eq("UpdateCardLimitsSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.updateCardLimits(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("UpdateCardLimitsSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should update security settings successfully")
    void testUpdateSecuritySettings_ShouldExecuteSaga() {
        // Given
        UpdateSecuritySettingsCommand command = UpdateSecuritySettingsCommand.builder()
                .cardId(UUID.randomUUID())
                .securitySettingId(UUID.randomUUID())
                .enabled(true)
                .build();
        when(sagaEngine.execute(eq("ManageSecuritySettingsSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.updateSecuritySettings(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("ManageSecuritySettingsSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should setup credit line successfully")
    void testSetupCreditLine_ShouldExecuteSaga() {
        // Given
        SetupCreditLineCommand command = SetupCreditLineCommand.builder()
                .cardId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .creditLimit(BigDecimal.valueOf(50000))
                .interestRate(BigDecimal.valueOf(0.18))
                .build();
        when(sagaEngine.execute(eq("SetupCreditLineSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.setupCreditLine(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("SetupCreditLineSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should issue virtual card successfully")
    void testIssueVirtualCard_ShouldExecuteSaga() {
        // Given
        IssueVirtualCardCommand command = IssueVirtualCardCommand.builder()
                .parentCardId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .spendingLimit(BigDecimal.valueOf(1000))
                .build();
        when(sagaEngine.execute(eq("IssueVirtualCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.issueVirtualCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("IssueVirtualCardSaga"), any(StepInputs.class));
    }

    @Test
    void unconfiguredIssuanceRemainsUnavailableAcrossSubscriptions() {
        var command = IssueCardCommand.builder().customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID()).cardProgramId(UUID.randomUUID()).build();
        for (int attempt = 0; attempt < 2; attempt++) {
            StepVerifier.create(service.issueCard(command)).expectErrorMatches(error ->
                    error instanceof org.springframework.web.server.ResponseStatusException failure
                    && failure.getStatusCode().value() == 503).verify();
        }
        verifyNoInteractions(sagaEngine, commands);
    }

    @Test
    void constructorDoesNotAuthorizeOrStartIssuance() {
        var command = IssueCardCommand.builder().customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID()).cardProgramId(UUID.randomUUID()).build();
        for (int attempt = 0; attempt < 2; attempt++) {
            StepVerifier.create(service.issueCard(command)).expectErrorMatches(error ->
                    error instanceof org.springframework.web.server.ResponseStatusException failure
                    && failure.getStatusCode().value() == 503).verify();
        }
        verifyNoInteractions(sagaEngine, commands);
    }

}
