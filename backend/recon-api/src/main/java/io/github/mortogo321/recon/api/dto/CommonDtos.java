package io.github.mortogo321.recon.api.dto;

import java.math.BigDecimal;
import java.util.List;

import io.github.mortogo321.recon.domain.money.Money;

/** Shared wire shapes. Records so the JSON contract is visible in one place and immutable. */
public final class CommonDtos {

    private CommonDtos() {}

    /**
     * Money always crosses the wire as amount plus currency, never as a bare number. The amount is
     * serialised as a string so a JavaScript client cannot lose precision on a large ticket —
     * {@code Number} is a double, and a double cannot hold every value a DECIMAL(19,4) can.
     */
    public record MoneyDto(String amount, String currency) {
        public static MoneyDto of(Money money) {
            return money == null ? null : new MoneyDto(money.amount().toPlainString(), money.currencyCode());
        }
    }

    /**
     * Cursor-paged envelope. The console never asks for "page 27" — it asks for "more after this
     * id", which is what keeps the query flat as the exception table grows.
     */
    public record CursorPage<T>(List<T> items, Long nextCursor, boolean hasMore) {
        public static <T> CursorPage<T> of(List<T> items, int requestedLimit, java.util.function.Function<T, Long> id) {
            boolean hasMore = items.size() == requestedLimit && !items.isEmpty();
            Long next = items.isEmpty() ? null : id.apply(items.getLast());
            return new CursorPage<>(items, hasMore ? next : null, hasMore);
        }
    }

    /** Offset-paged envelope, used only where the console genuinely needs a total count. */
    public record PagedResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    public record CountByName(String name, long count) {}

    public record AmountByName(String name, long count, BigDecimal amount) {}
}
