package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;

/**
 * Faster {@link DepthFirstScanner} variant - more specific test means it stores less nodes in its visited HashSet
 */
public class FastDepthFirstScanner extends DepthFirstScanner {
    @Override
    protected boolean possibleParallelStart(FlowNode f) {
        return f instanceof StepStartNode && ((StepStartNode) f).getDescriptor() instanceof ParallelStep.DescriptorImpl;
    }

}
