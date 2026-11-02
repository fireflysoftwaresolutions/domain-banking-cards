package io.fireflysoftware.domain.banking.cards.core.card.activation;

import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationResponse;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface CardActivationRecoveryService {
    Mono<CardActivationResponse> recover(UUID cardId, UUID actionId);
}
