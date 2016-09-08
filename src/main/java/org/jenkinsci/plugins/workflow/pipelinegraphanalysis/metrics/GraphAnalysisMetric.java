package org.jenkinsci.plugins.workflow.pipelinegraphanalysis.metrics;

import org.jenkinsci.plugins.workflow.graphanalysis.ChunkFinder;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowChunkWithContext;
import org.jenkinsci.plugins.workflow.graphanalysis.SimpleChunkVisitor;

import java.util.Collection;

/**
 * Holds info about what analysis metrics we care about for pipeline graph analysis
 * TODO Use describable/descriptor APIs here?
 * TODO Describable created with all the extraction information from descriptor?
 * @author Sam Van Oort
 */
public abstract class GraphAnalysisMetric <MetricDataType> {

    /** How to combine parallel values, i.e. between branches of a parallel block */
    public abstract MetricDataType combineParallel(Collection<MetricDataType> values);

    /** How to combine serial values, i.e. a sequence of chunks*/
    public abstract MetricDataType combineSerial(Collection<MetricDataType> values);

    /** What am I called?
     *  TODO attach to descriptor
     */
    public abstract String getDisplayName();

    /** Initialize a new metrics store for our use
     *  TODO attach to descriptor
     */
    public MetricsStore<MetricDataType> createMetricsStore() {
        return new MetricsStore<MetricDataType>();
    }

    /** Defines how this metric finds chunks
     *  TODO attach to descriptor
     */
    public abstract ChunkFinder createChunkFinder();

    /** Creates a visitor class that operates upon chunk
     *  TODO attach to descriptor
     */
    public abstract SimpleChunkVisitor createVisitor();

    /** Try to find the metric value from chunk
     *  TODO attach to descriptor
     */
    public abstract MetricDataType extractValue(FlowChunkWithContext chunk);

    // TODO perhaps we need some method for updating metrics node by node?
}
