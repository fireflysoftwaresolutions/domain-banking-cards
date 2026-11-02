package io.fireflysoftware.domain.banking.cards.core.card.activation.impl;

import io.fireflysoftware.domain.banking.cards.core.card.activation.CardActivationClientFactory;
import io.fireflysoftware.domain.banking.cards.core.card.activation.CardActivationRecoveryService;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationResponse;
import io.fireflysoftware.core.banking.cards.sdk.model.CardActivationReceipt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public final class CardActivationRecoveryServiceImpl implements CardActivationRecoveryService {
    private final Optional<CardActivationClientFactory> clients;
    public CardActivationRecoveryServiceImpl(Optional<CardActivationClientFactory> clients) {
        this.clients = clients;
    }

    @Override
    public Mono<CardActivationResponse> recover(UUID cardId, UUID actionId) {
        return Mono.defer(() -> {
            if (cardId == null || actionId == null || new UUID(0, 0).equals(cardId) || new UUID(0, 0).equals(actionId)) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Original card and action are required"));
            }
            if (clients.isEmpty()) return Mono.error(unavailable());
            return clients.get().current(cardId, actionId).switchIfEmpty(Mono.error(unavailable()))
                    .flatMap(client -> client.revalidate().then(Mono.defer(() -> client.api()
                            .recoverCardActivation(cardId, actionId, null).timeout(client.remaining())))
                            .switchIfEmpty(Mono.error(invalidReceipt()))
                            .map(receipt -> checked(cardId, actionId, receipt))
                            .flatMap(receipt -> client.revalidate().thenReturn(receipt)))
                    .onErrorMap(java.util.concurrent.TimeoutException.class, failure -> unavailable())
                    .onErrorMap(WebClientRequestException.class, failure -> unavailable())
                    .onErrorMap(WebClientResponseException.class, failure -> new ResponseStatusException(
                            failure.getStatusCode(), failure.getStatusCode().value() >= 500
                                    || failure.getStatusCode().value() == 408 || failure.getStatusCode().value() == 429
                                    ? "Original activation receipt unavailable; outcome remains unknown"
                                    : "Original activation receipt is not available to the current caller"));
        });
    }

    private static CardActivationResponse checked(UUID card, UUID action, CardActivationReceipt receipt) {
        if (receipt == null || !card.equals(receipt.getCardId()) || !action.equals(receipt.getActionId()) || receipt.getState() == null) {
            throw invalidReceipt();
        }
        return new CardActivationResponse(receipt.getActionId(), receipt.getCardId(),
                CardActivationResponse.State.valueOf(receipt.getState().name()),
                receipt.getActivationTime() == null ? null : receipt.getActivationTime().atOffset(java.time.ZoneOffset.UTC));
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Original activation receipt unavailable; outcome remains unknown");
    }
    private static ResponseStatusException invalidReceipt() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Matching original activation receipt required");
    }
}
