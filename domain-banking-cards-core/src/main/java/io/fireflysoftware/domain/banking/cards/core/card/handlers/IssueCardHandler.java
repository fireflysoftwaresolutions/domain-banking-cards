package io.fireflysoftware.domain.banking.cards.core.card.handlers;

import io.fireflysoftware.domain.banking.cards.core.card.commands.IssueCardCommand;
import java.util.UUID;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@CommandHandlerComponent
public class IssueCardHandler extends CommandHandler<IssueCardCommand, UUID> {
    @Override
    protected Mono<UUID> doHandle(IssueCardCommand command) {
        return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Core card issuance initiation is not configured"));
    }
}
