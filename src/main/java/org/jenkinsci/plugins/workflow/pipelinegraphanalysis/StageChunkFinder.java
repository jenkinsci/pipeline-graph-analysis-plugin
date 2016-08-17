package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.ChunkFinder;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Finds both block-scoped and legacy stages
 * @author Sam Van Oort
 */
public class StageChunkFinder implements ChunkFinder {

    public boolean isStartInsideChunk() {
        return true;
    }

    @Override
    public boolean isChunkStart(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        return current.getAction(LabelAction.class) != null && current.getAction(ThreadNameAction.class) == null;
    }

    /** End is where you have a label marker before it... or  */
    @Override
    public boolean isChunkEnd(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        // First a block-scoped stage
        if (current instanceof StepEndNode && ((StepEndNode) current).getDescriptor() instanceof StageStep.DescriptorImpl) {
            // We have to look for the labelaction because block-scoped stage creates two nested blocks
            return ((StepEndNode) current).getStartNode().getAction(LabelAction.class) != null;
        }
        // Then a marker-scoped stage
        if (previous != null) {
            return previous.getAction(LabelAction.class) != null && previous.getAction(ThreadNameAction.class) == null;
        }
        return false;
    }
}
