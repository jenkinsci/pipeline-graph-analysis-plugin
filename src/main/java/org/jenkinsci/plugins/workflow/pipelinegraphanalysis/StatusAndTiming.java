package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.List;

/**
 * Provides common APIs for doing status and timing computations
 * @author Sam Van Oort
 */
public class StatusAndTiming {

    public enum GenericStatus {
        NOT_EXECUTED,
        ABORTED,
        SUCCESS,
        IN_PROGRESS,
        PAUSED_PENDING_INPUT,
        FAILED,
        UNSTABLE,
        CUSTOM // Not currently implemented, but allows us to support custom status annotations
    }

    private static boolean isPendingInput(WorkflowRun run) {
        // Logic borrowed from Pipeline Stage View plugin, RuneEx
        InputAction inputAction = run.getAction(InputAction.class);
        if (inputAction != null) {
            List<InputStepExecution> executions = inputAction.getExecutions();
            if (executions != null && !executions.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static GenericStatus getChunkStatus(@Nonnull WorkflowRun run,
                                        @CheckForNull FlowNode before, @Nonnull FlowNode firstNode,
                                        @Nonnull FlowNode lastNode, @CheckForNull FlowNode after) {
        boolean isLastChunk = after == null && run.getExecution().isCurrentHead(lastNode);
        if (isLastChunk && run.isBuilding()) {
            if (run.isBuilding()) {
                return (isPendingInput(run)) ? GenericStatus.PAUSED_PENDING_INPUT : GenericStatus.IN_PROGRESS;
            } else {
                // Final chunk on completed build
                Result r = run.getResult();
                if (r == Result.NOT_BUILT) {
                    return GenericStatus.NOT_EXECUTED;
                } else if (r == Result.ABORTED) {
                    return GenericStatus.ABORTED;
                } else if (r == Result.FAILURE ) {
                    return GenericStatus.FAILED;
                } else if (r == Result.UNSTABLE ) {
                    return GenericStatus.UNSTABLE;
                } else if (r == Result.SUCCESS) {
                    return GenericStatus.SUCCESS;
                } else {
                    return GenericStatus.FAILED;
                }
            }
        }

        // Previous chunk before end. If flow continued beyond this, it didn't fail.
        // TODO check that previous assertion... what about blocks where the lastNode doesn't include BlockEndNode?
        if (!NotExecutedNodeAction.isExecuted(lastNode)) {
            return GenericStatus.NOT_EXECUTED;
        } else {
            return (run.getResult() == Result.UNSTABLE) ? GenericStatus.UNSTABLE : GenericStatus.SUCCESS;
        }
    }

    public static Object getChunkTiming(@Nonnull WorkflowRun run,
                                        @CheckForNull FlowNode before, @Nonnull FlowNode firstNode,
                                        @Nonnull FlowNode lastNode, @CheckForNull FlowNode after) {
        // Pause timing
        // Run time, if executed at all
        return null;
    }
}
