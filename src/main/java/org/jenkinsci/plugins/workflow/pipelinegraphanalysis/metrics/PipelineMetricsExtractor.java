package org.jenkinsci.plugins.workflow.pipelinegraphanalysis.metrics;

import org.jenkinsci.plugins.workflow.graphanalysis.ChunkFinder;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.SimpleChunkVisitor;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds information needed to an extraction run against flows, grabbing multiple metrics in one pass
 */
@NotThreadSafe
public class PipelineMetricsExtractor {
    static class ExtractionBinding {
        GraphAnalysisMetric metric;
        ChunkFinder finder;
        MetricsStore store;
    }

    List<ExtractionBinding> bindings = new ArrayList<ExtractionBinding>();
    boolean alreadyRan = false;

    public void addMetric(GraphAnalysisMetric metric) {
        for (ExtractionBinding eb : bindings) {  // Avoid dupe metrics
            if (eb.metric.getDisplayName().equals(metric.getDisplayName())) {
                return;
            }
        }
        ExtractionBinding bind = new ExtractionBinding();
        bind.metric = metric;
        bind.finder = metric.createChunkFinder();
        bind.store = metric.createMetricsStore();
        bindings.add(bind);
    }

    static void runExtraction(WorkflowRun run, ForkScanner scanner, List<ExtractionBinding> binds) {
        // Initial naive implementation here, perhaps a better way is to look for chunkfinders mapping to the same
        // ChunkFinders and have them share a metrics store
        ArrayList<SimpleChunkVisitor> visitors = new ArrayList<SimpleChunkVisitor>(binds.size());
        ArrayList<ChunkFinder> finders = new ArrayList<ChunkFinder>(binds.size());
        for (ExtractionBinding eb : binds) {
            visitors.add(eb.metric.createVisitor());  //TODO might use simplified form?
            finders.add(eb.finder);
        }
        scanner.visitMultiChunks(visitors, finders);
    }

    public List<MetricsStore> doExtraction(WorkflowRun run, ForkScanner scanner) {
        if (alreadyRan) {
            for (ExtractionBinding eb : this.bindings) {
                eb.store.clear();
            }
        }
        runExtraction(run, scanner, this.bindings);
        List<MetricsStore> stores = new ArrayList<MetricsStore>();
        for (ExtractionBinding eb : this.bindings) {
            stores.add(eb.store.copy());
        }
        alreadyRan = true;
        return stores;
    }
}
