package io.fireflysoftware.domain.banking.cards.sdk;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.domain.banking.cards.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.banking.cards.sdk.model.CardActivationResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CardActivationReceiptContractTest {
    @Test void actualGeneratedReceiptPreservesAuthoritativeInstant() throws Exception {
        assertEquals(Instant.class, CardActivationResponse.class.getMethod("getActivationTime").getReturnType());
        var mapper = new ApiClient().getObjectMapper();
        var receipt = mapper.readValue("{\"activationTime\":\"2026-10-31T14:34:56.123456+02:00\"}", CardActivationResponse.class);
        assertEquals(Instant.parse("2026-10-31T12:34:56.123456Z"), receipt.getActivationTime());
        assertEquals(receipt.getActivationTime(), mapper.readValue(mapper.writeValueAsBytes(receipt), CardActivationResponse.class).getActivationTime());
    }
    @Test void offsetlessTimeCannotBeInferredFromTheConsumerTimezone() {
        assertThrows(Exception.class, () -> new ApiClient().getObjectMapper().readValue(
                "{\"activationTime\":\"2026-10-31T12:34:56\"}", CardActivationResponse.class));
    }
}
