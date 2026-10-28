package ch.paynash.pos.order;

import java.time.Instant;

public record OrderRound(long id, long orderId, int number, long sentBy, String sentByName, Instant sentAt) {
}
