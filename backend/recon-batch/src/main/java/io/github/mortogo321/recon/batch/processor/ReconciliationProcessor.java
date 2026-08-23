package io.github.mortogo321.recon.batch.processor;

import java.util.List;

import org.springframework.batch.infrastructure.item.ItemProcessor;

import io.github.mortogo321.recon.batch.support.ReconCandidate;
import io.github.mortogo321.recon.domain.match.MatchOutcome;
import io.github.mortogo321.recon.domain.match.ReconciliationEngine;
import io.github.mortogo321.recon.domain.match.ToleranceRule;

/**
 * Applies the domain engine to one match key. Thin by design: all the interesting logic lives in
 * {@link ReconciliationEngine}, where it is unit-testable without Spring Batch, a database or a
 * clock. One key can legitimately yield more than one outcome — a duplicated txn plus a clean
 * aggregate match, for instance — hence the list.
 */
public class ReconciliationProcessor implements ItemProcessor<ReconCandidate, List<MatchOutcome>> {

    private final ReconciliationEngine engine;
    private final ToleranceRule tolerance;

    public ReconciliationProcessor(ReconciliationEngine engine, ToleranceRule tolerance) {
        this.engine = engine;
        this.tolerance = tolerance;
    }

    @Override
    public List<MatchOutcome> process(ReconCandidate candidate) {
        List<MatchOutcome> outcomes =
                engine.reconcile(candidate.settlements(), candidate.ledgerEntries(), tolerance).outcomes();
        // Returning null would filter the item out of the chunk; an empty list is only possible when
        // every settlement row for the key was a dropped duplicate, which is genuinely nothing to write.
        return outcomes.isEmpty() ? null : outcomes;
    }
}
