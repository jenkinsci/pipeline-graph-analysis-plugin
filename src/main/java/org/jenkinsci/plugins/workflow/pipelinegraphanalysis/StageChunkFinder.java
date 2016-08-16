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
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class StageChunkFinder implements ChunkFinder {

    public boolean isStartInsideChunk() {
        return true;
    }

    @Override
    public boolean isChunkStart(@Nonnull FlowNode current, @CheckForNull FlowNode previous) {
        LabelAction label = current.getAction(LabelAction.class);
        return label != null && !(label instanceof ThreadNameAction);
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
            LabelAction label = previous.getAction(LabelAction.class);
            return label != null && !(label instanceof ThreadNameAction);
        }
        return false;
    }
}
