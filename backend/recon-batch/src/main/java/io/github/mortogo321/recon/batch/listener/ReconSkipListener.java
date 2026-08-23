package io.github.mortogo321.recon.batch.listener;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;

import io.github.mortogo321.recon.batch.support.ReconCandidate;
import io.github.mortogo321.recon.domain.match.MatchOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Records what the job chose to skip. A skip that is only visible in the application log is a skip
 * nobody will ever reconcile, so each one is counted by reason and by stage — read, process or
 * write — which is enough to tell "one merchant sends bad data" apart from "the feed changed".
 */
public class ReconSkipListener implements SkipListener<ReconCandidate, List<MatchOutcome>> {

    private static final Logger log = LoggerFactory.getLogger(ReconSkipListener.class);

    private final MeterRegistry meters;

    public ReconSkipListener(MeterRegistry meters) {
        this.meters = meters;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        record("read", "n/a", t);
    }

    @Override
    public void onSkipInProcess(ReconCandidate item, Throwable t) {
        record("process", item == null ? "n/a" : item.key().toString(), t);
    }

    @Override
    public void onSkipInWrite(List<MatchOutcome> item, Throwable t) {
        String key = item == null || item.isEmpty() ? "n/a" : item.getFirst().key().toString();
        record("write", key, t);
    }

    private void record(String stage, String key, Throwable t) {
        String reason = t == null ? "unknown" : t.getClass().getSimpleName();
        meters.counter("recon.skips", Tags.of("stage", stage, "reason", reason)).increment();
        log.warn("Skipped {} during {} ({}): {}", key, stage, reason, t == null ? "" : t.getMessage());
    }
}
