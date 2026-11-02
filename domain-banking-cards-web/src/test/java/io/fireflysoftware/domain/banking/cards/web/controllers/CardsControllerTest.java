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


package io.fireflysoftware.domain.banking.cards.web.controllers;

import io.fireflysoftware.domain.banking.cards.core.card.commands.*;
import io.fireflysoftware.domain.banking.cards.core.card.services.CardService;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.IssueCardResponse;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.ReplaceCardResponse;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationRequest;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationResponse;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardsController Tests")
class CardsControllerTest {

    @Mock
    private CardService cardService;

    @Mock
    private io.fireflysoftware.domain.banking.cards.core.card.activation.CardActivationRecoveryService recovery;

    @Mock
    private SagaResult sagaResult;

    private CardsController controller;

    @BeforeEach
    void setUp() {
        controller = new CardsController(cardService, recovery);
    }

    @Test
    @DisplayName("Should issue card successfully")
    void testIssueCard_ShouldReturnOkResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();

        when(sagaResult.resultOf(eq("createCard"), eq(UUID.class)))
                .thenReturn(Optional.of(cardId));
        when(sagaResult.correlationId()).thenReturn(correlationId);
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.issueCard(any(IssueCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<IssueCardResponse>> result = controller.issueCard(command);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    IssueCardResponse body = response.getBody();
                    assertNotNull(body);
                    assertEquals(cardId, body.getCardId());
                    assertEquals("COMPLETED", body.getStatus());
                })
                .verifyComplete();

        verify(cardService).issueCard(any(IssueCardCommand.class));
    }

    @Test
    @DisplayName("Should handle issue card errors")
    void testIssueCard_ShouldHandleErrors() {
        // Given
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();
        RuntimeException error = new RuntimeException("Service error");
        when(cardService.issueCard(any(IssueCardCommand.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<ResponseEntity<IssueCardResponse>> result = controller.issueCard(command);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(cardService).issueCard(any(IssueCardCommand.class));
    }

    @Test
    @DisplayName("Activation returns each actual owner state without treating pending as success")
    void testActivateCard_ShouldReturnExactOwnerStatus() {
        UUID cardId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        String proof = "syntheticIssuedPossessionProofForTransportOnly0123456789";
        for (var state : CardActivationResponse.State.values()) {
            var receipt = new CardActivationResponse(actionId, cardId, state, null);
            when(cardService.activateCard(any(ActivateCardCommand.class))).thenReturn(Mono.just(receipt));
            int expected = switch (state) {
                case CONFIRMED -> 200;
                case RESERVED, UNCERTAIN -> 202;
                case REJECTED -> 422;
                case RECONCILIATION_REQUIRED -> 409;
            };
            StepVerifier.create(controller.activateCard(cardId, actionId, new CardActivationRequest(proof),
                    org.springframework.mock.http.server.reactive.MockServerHttpRequest.post("/api/v1/cards/" + cardId + "/activate")
                            .header("Authorization", "Bearer synthetic.original.token").build()))
                    .assertNext(response -> {
                        assertEquals(expected, response.getStatusCode().value());
                        assertEquals(receipt, response.getBody());
                    }).verifyComplete();
        }
        verify(cardService, times(5)).activateCard(argThat(command ->
                cardId.equals(command.getCardId()) && actionId.equals(command.getActionId())
                        && proof.equals(command.getActivationCode())));
    }

    @Test
    @DisplayName("Should block card successfully")
    void testBlockCard_ShouldReturnNoContentResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String reason = "Suspected fraud";
        String blockedBy = "CUSTOMER";
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.blockCard(any(BlockCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<Void>> result = controller.blockCard(cardId, reason, blockedBy);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();

        verify(cardService).blockCard(any(BlockCardCommand.class));
    }

    @Test
    @DisplayName("Should unblock card successfully")
    void testUnblockCard_ShouldReturnNoContentResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String unblockedBy = "CUSTOMER";
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.unblockCard(any(UnblockCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<Void>> result = controller.unblockCard(cardId, unblockedBy);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();

        verify(cardService).unblockCard(any(UnblockCardCommand.class));
    }

    @Test
    @DisplayName("Should replace card successfully")
    void testReplaceCard_ShouldReturnOkResponse() {
        // Given
        UUID oldCardId = UUID.randomUUID();
        UUID newCardId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String reason = "Lost";

        when(sagaResult.resultOf(eq("createReplacementCard"), eq(UUID.class)))
                .thenReturn(Optional.of(newCardId));
        when(sagaResult.correlationId()).thenReturn(correlationId);
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.replaceCard(any(ReplaceCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<ReplaceCardResponse>> result = controller.replaceCard(oldCardId, reason, true, true);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    ReplaceCardResponse body = response.getBody();
                    assertNotNull(body);
                    assertEquals(oldCardId, body.getOldCardId());
                    assertEquals(newCardId, body.getNewCardId());
                    assertEquals("COMPLETED", body.getStatus());
                })
                .verifyComplete();

        verify(cardService).replaceCard(any(ReplaceCardCommand.class));
    }

    @Test
    @DisplayName("Should cancel card successfully")
    void testCancelCard_ShouldReturnNoContentResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String reason = "Customer request";
        String cancelledBy = "CUSTOMER";
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.cancelCard(any(CancelCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<Void>> result = controller.cancelCard(cardId, reason, cancelledBy);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();

        verify(cardService).cancelCard(any(CancelCardCommand.class));
    }

    @Test
    @DisplayName("Constructor should set service dependency")
    void testConstructor_ShouldSetService() {
        // When
        CardsController newController = new CardsController(cardService, recovery);

        // Then
        assertNotNull(newController);

        // Verify it works by calling a method
        UUID cardId = UUID.randomUUID();
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.blockCard(any(BlockCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        Mono<ResponseEntity<Void>> result = newController.blockCard(cardId, "test", "CUSTOMER");

        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();
    }
    @Test void originalReceiptRecoveryIsUncachedAndPreservesUncertainState() {
        UUID card=UUID.randomUUID(),action=UUID.randomUUID();
        var receipt=new CardActivationResponse(action,card,CardActivationResponse.State.UNCERTAIN,null);
        when(recovery.recover(card,action)).thenReturn(Mono.just(receipt));
        var request=org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/v1/cards/"+card+"/activation/"+action).header("Authorization","Bearer synthetic.original.token").build();
        StepVerifier.create(controller.recoverCardActivation(card,action,request)).assertNext(response->{assertEquals(200,response.getStatusCode().value());assertEquals("no-store",response.getHeaders().getCacheControl());assertEquals(receipt,response.getBody());}).verifyComplete();
        verify(recovery).recover(card,action);verifyNoInteractions(cardService);
    }
    @Test void receiptRecoveryWithoutBearerCannotInvokeTheReadService() {
        var request=org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/").build();
        StepVerifier.create(controller.recoverCardActivation(UUID.randomUUID(),UUID.randomUUID(),request)).expectErrorMatches(error->error instanceof org.springframework.web.server.ResponseStatusException status&&status.getStatusCode().value()==401).verify();verifyNoInteractions(recovery,cardService);
    }
}
