package io.github.mortogo321.recon.domain.match;

import java.util.List;
import java.util.Objects;

/** Everything the batch step needs to persist for one partition of one run. */
public record ReconciliationResult(List<MatchOutcome> outcomes, ReconciliationSummary summary) {

    public ReconciliationResult {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(summary, "summary");
        outcomes = List.copyOf(outcomes);
    }

    public List<MatchOutcome> exceptions() {
        return outcomes.stream().filter(MatchOutcome::isException).toList();
    }

    public List<MatchOutcome> triaged() {
        return MatchOutcome.triageOrder(exceptions());
    }
}
