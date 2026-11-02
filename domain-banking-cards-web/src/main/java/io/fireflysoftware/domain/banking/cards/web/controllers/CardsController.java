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

import io.fireflysoftware.domain.banking.cards.core.card.activation.CardActivationCallerToken;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import io.fireflysoftware.domain.banking.cards.core.card.commands.*;
import io.fireflysoftware.domain.banking.cards.core.card.services.CardService;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.IssueCardResponse;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.ReplaceCardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationRequest;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CardActivationResponse;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@org.springframework.context.annotation.Import(io.fireflysoftware.domain.banking.cards.web.openapi.CardActivationOpenApiConfiguration.class)
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Card lifecycle management - issue, activate, block, replace, and cancel cards")
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "cardBearer",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class CardsController {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final CardService cardService;
    private final io.fireflysoftware.domain.banking.cards.core.card.activation.CardActivationRecoveryService activationRecovery;

    @PostMapping
    @Operation(summary = "Issue a new card", description = "Issues a new debit or credit card for a customer")
    public Mono<ResponseEntity<IssueCardResponse>> issueCard(@Valid @RequestBody IssueCardCommand command) {
        return cardService.issueCard(command)
                .map(result -> {
                    UUID cardId = result.resultOf("createCard", UUID.class).orElse(null);
                    IssueCardResponse body = IssueCardResponse.builder()
                            .cardId(cardId)
                            .executionId(result.correlationId())
                            .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                            .build();
                    HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
                    return ResponseEntity.status(status).body(body);
                });
    }

    @PostMapping(value = "/{cardId}/activate", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Request card activation", description = "Submits issued possession proof to the card owner; pending and uncertain receipts do not confirm activation")
    @SecurityRequirement(name = "cardBearer")
    @DisableIdempotency
    public Mono<ResponseEntity<CardActivationResponse>> activateCard(
            @PathVariable UUID cardId,
            @RequestHeader("X-Idempotency-Key") UUID actionId,
            @Valid @RequestBody CardActivationRequest request, ServerHttpRequest transport) {
        var authorization = transport.getHeaders().get(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.size() != 1 || !authorization.getFirst().startsWith("Bearer ")) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Original card activation bearer required"));
        }
        final CardActivationCallerToken caller;
        try {
            caller = new CardActivationCallerToken(authorization.getFirst().substring(7));
        } catch (IllegalArgumentException invalid) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Original card activation bearer required"));
        }
        ActivateCardCommand command = ActivateCardCommand.builder()
                .cardId(cardId)
                .actionId(actionId)
                .activationCode(request.activationProof())
                .build();
        return cardService.activateCard(command)
                .map(receipt -> ResponseEntity.status(switch (receipt.state()) {
                    case CONFIRMED -> 200;
                    case RESERVED, UNCERTAIN -> 202;
                    case REJECTED -> 422;
                    case RECONCILIATION_REQUIRED -> 409;
                }).body(receipt)).contextWrite(context -> context.put(CardActivationCallerToken.class, caller));
    }

    @GetMapping(value = "/{cardId}/activation/{actionId}", produces = "application/json")
    @Operation(summary = "Read the original card activation receipt", description = "Requires current ownership. This read creates no action, sends no proof or issuer command, and leaves uncertain receipts uncertain.")
    @SecurityRequirement(name = "cardBearer")
    @DisableIdempotency
    public Mono<ResponseEntity<CardActivationResponse>> recoverCardActivation(
            @PathVariable UUID cardId, @PathVariable UUID actionId, ServerHttpRequest transport) {
        var authorization = transport.getHeaders().get(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.size() != 1 || !authorization.getFirst().startsWith("Bearer ")) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Original card activation bearer required"));
        }
        final CardActivationCallerToken caller;
        try {
            caller = new CardActivationCallerToken(authorization.getFirst().substring(7));
        } catch (IllegalArgumentException invalid) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Original card activation bearer required"));
        }
        return activationRecovery.recover(cardId, actionId)
                .map(receipt -> ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(receipt))
                .contextWrite(context -> context.put(CardActivationCallerToken.class, caller));
    }

    @PostMapping("/{cardId}/block")
    @Operation(summary = "Block a card", description = "Temporarily blocks a card")
    public Mono<ResponseEntity<Void>> blockCard(
            @PathVariable UUID cardId,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "CUSTOMER") String blockedBy) {
        BlockCardCommand command = BlockCardCommand.builder()
                .cardId(cardId)
                .reason(reason)
                .blockedBy(blockedBy)
                .build();
        return cardService.blockCard(command)
                .map(CardsController::toVoidResponse);
    }

    @PostMapping("/{cardId}/unblock")
    @Operation(summary = "Unblock a card", description = "Removes a temporary block from a card")
    public Mono<ResponseEntity<Void>> unblockCard(
            @PathVariable UUID cardId,
            @RequestParam(required = false, defaultValue = "CUSTOMER") String unblockedBy) {
        UnblockCardCommand command = UnblockCardCommand.builder()
                .cardId(cardId)
                .unblockedBy(unblockedBy)
                .build();
        return cardService.unblockCard(command)
                .map(CardsController::toVoidResponse);
    }

    @PostMapping("/{cardId}/replace")
    @Operation(summary = "Replace a card", description = "Replaces a lost, stolen, or damaged card")
    public Mono<ResponseEntity<ReplaceCardResponse>> replaceCard(
            @PathVariable UUID cardId,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "true") boolean transferLimits,
            @RequestParam(required = false, defaultValue = "true") boolean transferSecuritySettings) {
        ReplaceCardCommand command = ReplaceCardCommand.builder()
                .oldCardId(cardId)
                .replacementReason(reason)
                .transferLimits(transferLimits)
                .transferSecuritySettings(transferSecuritySettings)
                .build();
        return cardService.replaceCard(command)
                .map(result -> {
                    UUID newCardId = result.resultOf("createReplacementCard", UUID.class).orElse(null);
                    ReplaceCardResponse body = ReplaceCardResponse.builder()
                            .oldCardId(cardId)
                            .newCardId(newCardId)
                            .executionId(result.correlationId())
                            .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                            .build();
                    HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
                    return ResponseEntity.status(status).body(body);
                });
    }

    @PostMapping("/{cardId}/cancel")
    @Operation(summary = "Cancel a card", description = "Permanently cancels a card")
    public Mono<ResponseEntity<Void>> cancelCard(
            @PathVariable UUID cardId,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "CUSTOMER") String cancelledBy) {
        CancelCardCommand command = CancelCardCommand.builder()
                .cardId(cardId)
                .reason(reason)
                .cancelledBy(cancelledBy)
                .build();
        return cardService.cancelCard(command)
                .map(CardsController::toVoidResponse);
    }

    /**
     * Maps a saga outcome to an HTTP response for endpoints that do not return a body.
     * Success → 204 No Content. Failure → 422 Unprocessable Entity.
     */
    private static ResponseEntity<Void> toVoidResponse(SagaResult result) {
        if (result.isSuccess()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
    }
}
