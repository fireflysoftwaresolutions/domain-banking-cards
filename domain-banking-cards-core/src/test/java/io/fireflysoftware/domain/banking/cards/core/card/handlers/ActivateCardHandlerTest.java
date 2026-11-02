package io.fireflysoftware.domain.banking.cards.core.card.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fireflysoftware.core.banking.cards.sdk.api.CardsApi;
import io.fireflysoftware.core.banking.cards.sdk.model.CardActivationReceipt;
import io.fireflysoftware.core.banking.cards.sdk.model.CardActivationRequest;
import io.fireflysoftware.domain.banking.cards.core.card.commands.ActivateCardCommand;
import java.util.UUID;
import java.util.Optional;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import io.fireflysoftware.domain.banking.cards.core.card.activation.CardActivationClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ActivateCardHandlerTest {
    private final CardsApi api = mock(CardsApi.class);
    private final CardActivationClientFactory clients = mock(CardActivationClientFactory.class);
    private final CardActivationClientFactory.Client client = mock(CardActivationClientFactory.Client.class);
    private final ActivateCardHandler handler = new ActivateCardHandler(Optional.of(clients));

    @BeforeEach
    void currentAuthority() {
        when(clients.current(any(), any())).thenReturn(Mono.just(client));
        when(client.api()).thenReturn(api);
        when(client.remaining()).thenReturn(Duration.ofSeconds(5));
        when(client.revalidate()).thenReturn(Mono.empty());
    }
    private final UUID card = UUID.randomUUID();
    private final UUID action = UUID.randomUUID();
    private final String proof = "syntheticIssuedPossessionProofForTransportOnly0123456789";

    private ActivateCardCommand command() {
        return ActivateCardCommand.builder().cardId(card).actionId(action).activationCode(proof).build();
    }

    private CardActivationReceipt receipt(CardActivationReceipt.StateEnum state) {
        var receipt = new CardActivationReceipt();
        receipt.setCardId(card);
        receipt.setActionId(action);
        receipt.setState(state);
        return receipt;
    }

    @Test
    void delegatesTheExactIssuedProofAndStableActionWithoutGenericCardUpdates() {
        when(api.activateCard(eq(card), eq(action), any(CardActivationRequest.class)))
                .thenReturn(Mono.just(receipt(CardActivationReceipt.StateEnum.CONFIRMED)));
        for (int retry = 0; retry < 2; retry++) {
            StepVerifier.create(handler.doHandle(command())).assertNext(result -> {
                assertEquals(card, result.cardId());
                assertEquals(action, result.actionId());
                assertEquals("CONFIRMED", result.state().name());
            }).verifyComplete();
        }
        verify(api, times(2)).activateCard(eq(card), eq(action), argThat(request -> proof.equals(request.getActivationProof())));
        verifyNoMoreInteractions(api);
    }

    @Test
    void everyNonconfirmedOwnerStateRemainsNonconfirmed() {
        for (var state : CardActivationReceipt.StateEnum.values()) {
            when(api.activateCard(eq(card), eq(action), any(CardActivationRequest.class))).thenReturn(Mono.just(receipt(state)));
            StepVerifier.create(handler.doHandle(command()))
                    .assertNext(result -> assertEquals(state.name(), result.state().name())).verifyComplete();
        }
    }

    @Test
    void missingOrDifferentReceiptIdentityCannotCompleteActivation() {
        var missing = receipt(CardActivationReceipt.StateEnum.CONFIRMED);
        missing.setActionId(null);
        var different = receipt(CardActivationReceipt.StateEnum.CONFIRMED);
        different.setCardId(UUID.randomUUID());
        var noState = receipt(null);
        for (var invalid : new CardActivationReceipt[] {missing, different, noState}) {
            when(api.activateCard(eq(card), eq(action), any(CardActivationRequest.class))).thenReturn(Mono.just(invalid));
            StepVerifier.create(handler.doHandle(command())).expectError(ResponseStatusException.class).verify();
        }
    }

    @Test
    void emptyOwnerResponseCannotBecomeSuccessfulCompletion() {
        when(api.activateCard(eq(card), eq(action), any(CardActivationRequest.class))).thenReturn(Mono.empty());
        StepVerifier.create(handler.doHandle(command())).expectError(ResponseStatusException.class).verify();
    }

    @Test
    void malformedOrMissingCallerInputsNeverReachTheOwner() {
        for (var invalid : new ActivateCardCommand[] {
                ActivateCardCommand.builder().cardId(card).activationCode(proof).build(),
                ActivateCardCommand.builder().actionId(action).activationCode(proof).build(),
                ActivateCardCommand.builder().cardId(card).actionId(action).activationCode("123456").build()}) {
            StepVerifier.create(handler.doHandle(invalid)).expectError(ResponseStatusException.class).verify();
        }
        verifyNoInteractions(api);
    }

    @Test
    void absentDelegationDoesNotInvokeCoreOrReportActivation() {
        StepVerifier.create(new ActivateCardHandler(Optional.empty()).doHandle(command()))
                .expectErrorMatches(error -> error instanceof ResponseStatusException refusal
                        && refusal.getStatusCode().value() == 503).verify();
        verifyNoInteractions(api);
    }

    @Test
    void withdrawnAuthorityPreventsTheOwnerInvocation() {
        when(client.revalidate()).thenReturn(Mono.error(new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN)));
        StepVerifier.create(handler.doHandle(command())).expectError(ResponseStatusException.class).verify();
        verifyNoInteractions(api);
    }

    @Test
    void commandDiagnosticStringDoesNotDisclosePossessionProof() {
        assertFalse(command().toString().contains(proof));
    }

    @Test
    void unavailableOwnerOutcomeIsUnknownAndNeverRetried() {
        when(api.activateCard(eq(card), eq(action), any(CardActivationRequest.class)))
                .thenReturn(Mono.error(org.springframework.web.reactive.function.client.WebClientResponseException.create(
                        503, "Unavailable", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null)));
        StepVerifier.create(handler.doHandle(command())).expectErrorMatches(error ->
                error instanceof ResponseStatusException status && status.getStatusCode().value() == 503
                && status.getReason().contains("outcome is unknown") && !status.getReason().contains("refused")).verify();
        verify(api).activateCard(eq(card), eq(action), any(CardActivationRequest.class));
        verifyNoMoreInteractions(api);
    }

    @Test
    void timedOutOwnerCallNeverInventsReceiptOrChangesTheAction() {
        when(api.activateCard(eq(card), eq(action), any(CardActivationRequest.class)))
                .thenReturn(Mono.error(new java.util.concurrent.TimeoutException()));
        StepVerifier.create(handler.doHandle(command())).expectErrorMatches(error -> error instanceof ResponseStatusException status
                && status.getStatusCode().value() == 503 && status.getReason().contains("outcome is unknown")).verify();
        verify(api).activateCard(eq(card), eq(action), any(CardActivationRequest.class));
        verifyNoMoreInteractions(api);
    }

    @Test
    void historicalActivationTimeIsCopiedExactlyFromTheOwnerReceipt() {
        var expected = java.time.Instant.parse("2026-10-31T12:34:56.123456Z");
        var owner = receipt(CardActivationReceipt.StateEnum.CONFIRMED);
        owner.setActivationTime(expected);
        when(api.activateCard(eq(card), eq(action), any(CardActivationRequest.class))).thenReturn(Mono.just(owner));
        StepVerifier.create(handler.doHandle(command())).assertNext(result -> assertEquals(expected, result.activationTime().toInstant())).verifyComplete();
    }
    @Test void transportIoFailureRemainsUnknownWithoutRetryOrSecretDisclosure() {
        
        var failure = new org.springframework.web.reactive.function.client.WebClientRequestException(
                new java.io.IOException("sensitive-upstream-detail"), org.springframework.http.HttpMethod.POST,
                java.net.URI.create("https://owner.example/cards"), org.springframework.http.HttpHeaders.EMPTY);
        when(api.activateCard(eq(card), eq(action), any())).thenReturn(Mono.error(failure));
        StepVerifier.create(handler.doHandle(command())).expectErrorMatches(error -> error instanceof ResponseStatusException status
                && status.getStatusCode().value() == 503 && status.getReason().contains("outcome is unknown")
                && !status.getReason().contains("sensitive-upstream-detail")).verify();
        verify(api).activateCard(eq(card), eq(action), any());verifyNoMoreInteractions(api);
    }
}
