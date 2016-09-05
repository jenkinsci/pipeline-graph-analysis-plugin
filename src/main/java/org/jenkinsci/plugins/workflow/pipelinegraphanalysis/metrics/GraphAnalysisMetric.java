package org.jenkinsci.plugins.workflow.pipelinegraphanalysis.metrics;

import org.jenkinsci.plugins.workflow.graphanalysis.ChunkFinder;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowChunkWithContext;

import java.util.Collection;

/**
 * Holds info about what analysis metrics we care about for pipeline graph analysis
 * TODO use describable/descriptor APIs here?
 * @author Sam Van Oort
 */
public abstract class GraphAnalysisMetric <MetricDataType> {

    /** How to combine parallel values */
    public abstract MetricDataType combineParallel(Collection<MetricDataType> values);

    /** How to combine serial values */
    public abstract MetricDataType combineSerial(Collection<MetricDataType> values);

    /** What am I called? */
    public abstract String getDisplayName();

    /** Initialize a new metrics store for our use */
    public MetricsStore<MetricDataType> createMetricsStore() {
        return new MetricsStore<MetricDataType>();
    }

    /** Defines how this metric finds chunks */
    public abstract ChunkFinder createChunkFinder();

    /** Try to find the metric value from chunk */
    public abstract MetricDataType extractValue(FlowChunkWithContext chunk);
}
