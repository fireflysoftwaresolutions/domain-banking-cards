package io.fireflysoftware.domain.banking.cards.core.card.activation.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.fireflysoftware.core.banking.cards.sdk.api.CardsApi;
import io.fireflysoftware.core.banking.cards.sdk.model.CardActivationReceipt;
import io.fireflysoftware.domain.banking.cards.core.card.activation.CardActivationClientFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CardActivationRecoveryServiceTest {
    final UUID card=UUID.randomUUID(),action=UUID.randomUUID();
    final CardsApi api=mock(CardsApi.class);
    final CardActivationClientFactory factory=mock(CardActivationClientFactory.class);
    final CardActivationClientFactory.Client client=mock(CardActivationClientFactory.Client.class);
    final CardActivationRecoveryServiceImpl service=new CardActivationRecoveryServiceImpl(Optional.of(factory));
    void authorize(){when(factory.current(card,action)).thenReturn(Mono.just(client));when(client.revalidate()).thenReturn(Mono.empty());when(client.api()).thenReturn(api);when(client.remaining()).thenReturn(Duration.ofSeconds(5));}
    CardActivationReceipt receipt(CardActivationReceipt.StateEnum state){return new CardActivationReceipt().cardId(card).actionId(action).state(state);}
    @Test void originalActionIsReadWithoutProofOrNewTransportKey(){
        authorize();when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.just(receipt(CardActivationReceipt.StateEnum.UNCERTAIN)));
        StepVerifier.create(service.recover(card,action)).assertNext(r->{assertEquals(card,r.cardId());assertEquals(action,r.actionId());assertEquals("UNCERTAIN",r.state().name());assertNull(r.activationTime());}).verifyComplete();
        verify(api).recoverCardActivation(card,action,null);verifyNoMoreInteractions(api);verify(client,times(2)).revalidate();
    }
    @Test void everyPersistedStateIsReturnedWithoutInferringNewActivation(){
        authorize();for(var state:CardActivationReceipt.StateEnum.values()){when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.just(receipt(state)));StepVerifier.create(service.recover(card,action)).assertNext(r->assertEquals(state.name(),r.state().name())).verifyComplete();}
    }
    @Test void authoritativeHistoricalTimeIsCopiedExactly(){
        authorize();var time=Instant.parse("2026-10-31T12:34:56.123456Z");when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.just(receipt(CardActivationReceipt.StateEnum.CONFIRMED).activationTime(time)));
        StepVerifier.create(service.recover(card,action)).assertNext(r->assertEquals(time,r.activationTime().toInstant())).verifyComplete();
    }
    @Test void absentOrZeroIdsCannotReachAuthorityOrOwner(){
        for(var ids:new UUID[][]{{null,action},{card,null},{new UUID(0,0),action},{card,new UUID(0,0)}})StepVerifier.create(service.recover(ids[0],ids[1])).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==400).verify();verifyNoInteractions(factory,api);
    }
    @Test void absentDelegationCannotReturnAnyReceipt(){StepVerifier.create(new CardActivationRecoveryServiceImpl(Optional.empty()).recover(card,action)).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==503).verify();verifyNoInteractions(api);}
    @Test void withdrawnAuthorityBeforeReadDoesNotDispatch(){
        authorize();when(client.revalidate()).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));StepVerifier.create(service.recover(card,action)).expectError(ResponseStatusException.class).verify();verifyNoInteractions(api);
    }
    @Test void authorityWithdrawnAfterOwnerReadWithholdsTheReceipt(){
        authorize();when(client.revalidate()).thenReturn(Mono.empty(),Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.just(receipt(CardActivationReceipt.StateEnum.CONFIRMED)));
        StepVerifier.create(service.recover(card,action)).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==403).verify();verify(api).recoverCardActivation(card,action,null);verifyNoMoreInteractions(api);
    }
    @Test void missingOwnerReceiptRemains404WithoutInventingAReceipt(){
        authorize();when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.error(org.springframework.web.reactive.function.client.WebClientResponseException.create(404,"Not found",org.springframework.http.HttpHeaders.EMPTY,new byte[0],null)));
        StepVerifier.create(service.recover(card,action)).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==404).verify();verify(api).recoverCardActivation(card,action,null);verifyNoMoreInteractions(api);
    }
    @Test void emptyOrMismatchedOwnerReceiptCannotBeReturned(){
        authorize();when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.empty());StepVerifier.create(service.recover(card,action)).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==502).verify();
        for(var invalid:new CardActivationReceipt[]{receipt(null),receipt(CardActivationReceipt.StateEnum.CONFIRMED).cardId(UUID.randomUUID()),receipt(CardActivationReceipt.StateEnum.CONFIRMED).actionId(UUID.randomUUID())}){when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.just(invalid));StepVerifier.create(service.recover(card,action)).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==502).verify();}
    }
    @Test void lostReadResponseRemainsUnavailableAndIsNotRetried(){
        authorize();when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.error(new java.util.concurrent.TimeoutException()));StepVerifier.create(service.recover(card,action)).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==503&&r.getReason().contains("unknown")).verify();verify(api).recoverCardActivation(card,action,null);verifyNoMoreInteractions(api);
    }
    @Test void ownerFailureCannotBecomeBusinessRefusalOrTerminalReceipt(){
        authorize();when(api.recoverCardActivation(card,action,null)).thenReturn(Mono.error(org.springframework.web.reactive.function.client.WebClientResponseException.create(503,"Unavailable",org.springframework.http.HttpHeaders.EMPTY,new byte[0],null)));
        StepVerifier.create(service.recover(card,action)).expectErrorMatches(e->e instanceof ResponseStatusException r&&r.getStatusCode().value()==503&&r.getReason().contains("unknown")).verify();verify(api).recoverCardActivation(card,action,null);verifyNoMoreInteractions(api);
    }
}
