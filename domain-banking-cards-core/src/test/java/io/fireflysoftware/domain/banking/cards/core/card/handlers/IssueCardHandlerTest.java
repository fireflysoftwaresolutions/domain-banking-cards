package io.fireflysoftware.domain.banking.cards.core.card.handlers;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.domain.banking.cards.core.card.commands.IssueCardCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class IssueCardHandlerTest {
    final IssueCardHandler handler = new IssueCardHandler();
    IssueCardCommand command() {
        return IssueCardCommand.builder().customerId(UUID.randomUUID()).accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID()).build();
    }
    void unavailable(IssueCardCommand command) {
        StepVerifier.create(handler.doHandle(command)).expectErrorMatches(error ->
                error instanceof ResponseStatusException status && status.getStatusCode().value() == 503
                && status.getReason().equals("Core card issuance initiation is not configured")).verify();
    }
    @Test void minimalCommandCannotInventPanIssuerOrActiveInstrument() { unavailable(command()); }
    @Test void callerIssuingMetadataCannotBypassMissingOwnerInitiation() {
        var command = command();command.setIssuerId(UUID.randomUUID());command.setBinId(UUID.randomUUID());
        command.setCardNetworkId(UUID.randomUUID());unavailable(command);
    }
    @Test void repeatedSubscriptionsCannotTurnUnavailableIntoAnInstrument() {
        var command = command();unavailable(command);unavailable(command);
    }
    @Test void errorDoesNotDiscloseSubmittedCardholderDetails() {
        var command = command();command.setCardHolderName("private-cardholder-value");unavailable(command);
    }
}
