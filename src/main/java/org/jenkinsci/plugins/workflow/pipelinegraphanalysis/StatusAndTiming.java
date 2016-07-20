package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Provides common APIs for doing status and timing computations on flows
 * Concepts: a chunk, which is a set of {@link FlowNode}s in the same exection with a first and last node.
 * Chunks exist in a context
 * @author Sam Van Oort
 */
public class StatusAndTiming {

    private static final Logger LOGGER = Logger.getLogger(StatusAndTiming.class.getName());

    // Sorted in increasing priority
    public enum GenericStatus {
        UNKNOWN,
        NOT_EXECUTED,
        SUCCESS,
        UNSTABLE,
        IN_PROGRESS,
        FAILED,
        ABORTED,
        PAUSED_PENDING_INPUT
    }

    public static class TimingInfo {
        private long totalDurationMillis;
        private long pauseDurationMillis;

        public TimingInfo() {
            this.totalDurationMillis = 0;
            this.pauseDurationMillis = 0;
        }

        public TimingInfo(long totalDurationMillis, long pauseDurationMillis) {
            this.totalDurationMillis = totalDurationMillis;
            this.pauseDurationMillis = pauseDurationMillis;
        }

        public long getTotalDurationMillis() {
            return totalDurationMillis;
        }

        public void setTotalDurationMillis(long totalDurationMillis) {
            this.totalDurationMillis = totalDurationMillis;
        }

        public long getPauseDurationMillis() {
            return pauseDurationMillis;
        }

        public void setPauseDurationMillis(long pauseDurationMillis) {
            this.pauseDurationMillis = pauseDurationMillis;
        }
    }

    /**
     * Get the start time for node, or null if not present
     * @param node Node to get time for
     * @return Start time of node in millis, or null if no timing present
     */
    @CheckForNull
    public static Long getStartTime(@Nonnull FlowNode node) {
        TimingAction time = node.getAction(TimingAction.class);
        return (time == null) ? null : time.getStartTime();
    }

    /**
     * Check that all the flownodes belong to the same execution as run
     * @param run Run that nodes must belong to
     * @param nodes Nodes to match to run
     * @throws IllegalArgumentException For the first flownode that doesn't belong to the FlowExectuon of run
     */
    public static void verifySameRun(@Nonnull WorkflowRun run, @CheckForNull FlowNode... nodes) throws IllegalArgumentException {
        if (nodes == null || nodes.length == 0) {
            return;
        }
        FlowExecution exec = run.getExecution();
        int i =0;
        for (FlowNode n : nodes) {
            if (n!= null && n.getExecution() != exec) {
                throw new IllegalArgumentException("FlowNode not part of the same execution found, at index "+i+" with ID "+n.getId());
            }
            i++;
        }
    }

    public static boolean isPendingInput(WorkflowRun run) {
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
        verifySameRun(run, before, firstNode, lastNode, after);
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

    /**
     * Compute timing for a chunk of nodes
     * @param run WorkflowRun they all belong to
     * @param pauseDuration Millis paused (collected beforehand)
     * @param before Node before the chunk, if null assume this is the first piece of the flow and has nothing before
     * @param firstNode First node in the chunk
     * @param lastNode Last node in the chunk
     * @param after Node after the chunk, if null we assume this chunk is at the end of the flow
     * @return Best guess at timing, or null if we can't compute anything
     */
    @CheckForNull
    public static TimingInfo computeChunkTiming(@Nonnull WorkflowRun run, long pauseDuration,
                                        @CheckForNull FlowNode before, @Nonnull FlowNode firstNode,
                                        @Nonnull FlowNode lastNode, @CheckForNull FlowNode after) {
        verifySameRun(run, before, firstNode, lastNode, after);
        long startTime = TimingAction.getStartTime(firstNode);
        long endTime = (after != null) ? TimingAction.getStartTime(after) : System.currentTimeMillis();

        if (!NotExecutedNodeAction.isExecuted(lastNode)) {
            return new TimingInfo(0,0);  // Nothing ran
        }  else if (before == null) {
            startTime = run.getStartTimeInMillis();
        } else if (after == null && run.getExecution().isComplete()) {
            // Completed flow
            endTime = TimingAction.getStartTime(lastNode);
        }
        //TODO log me if startTime is 0 or handle missing TimingAction

        return new TimingInfo((endTime-startTime), Math.min(Math.abs(pauseDuration), (endTime-startTime)));
    }

    /**
     * Computes the branch timings for a set of parallel branches
     * @param run
     * @param branchTimings Map of branch name : precomputed timing info
     * @param parallelStart
     * @param parallelEnd
     * @return
     */
    public static TimingInfo computeTimingForParallel(@Nonnull WorkflowRun run,
                                                     @Nonnull Map<String, TimingInfo> branchTimings,
                                                     @Nonnull FlowNode parallelStart, @CheckForNull FlowNode parallelEnd) {
        long overallDuration = 0;
        long maxPause = 0;
        boolean isIncomplete = parallelEnd == null;
        for (TimingInfo t : branchTimings.values()) {
            maxPause = Math.max(maxPause, t.getPauseDurationMillis());
            if (isIncomplete) {
                overallDuration = Math.max(overallDuration, t.getTotalDurationMillis());
            }
        }
        if (!isIncomplete) {
            overallDuration = TimingAction.getStartTime(parallelEnd) - TimingAction.getStartTime(parallelStart);
        }
        return new TimingInfo(overallDuration, maxPause);
    }

    public static Map<String, TimingInfo> computeParallelBranchTimings(@Nonnull WorkflowRun run,
                                                                       @Nonnull List<BlockStartNode> branchStarts,
                                                                       @Nonnull List<FlowNode> branchEnds,
                                                                       @Nonnull long[] pauseDurations,
                                                                       @Nonnull FlowNode parallelStart, @CheckForNull FlowNode parallelEnd) {

        verifySameRun(run, branchStarts.toArray(new FlowNode[0]));
        verifySameRun(run, branchEnds.toArray(new FlowNode[0]));
        if (branchStarts.size() != branchEnds.size()) {
            throw new IllegalArgumentException("Mismatched start and stop node counts: "+branchStarts.size()+","+branchEnds.size());
        }
        if (branchStarts.size() != pauseDurations.length) {
            throw new IllegalArgumentException("Mismatched node count and pause duration array: "+branchStarts.size()+","+pauseDurations.length);
        }
        HashMap<String, TimingInfo> timings = new HashMap<String,TimingInfo>();
        for (int i=0; i<branchEnds.size(); i++) {
            BlockStartNode start = branchStarts.get(i);
            FlowNode end = branchEnds.get(i);
            if (end instanceof BlockEndNode && start != ((BlockEndNode)end).getStartNode()) {
                throw new IllegalArgumentException("Mismatched parallel branch start/end nodes: "
                        + start.getId()+','
                        + end.getId());
            }
            LabelAction label = start.getAction(LabelAction.class);
            assert label != null;
            timings.put(label.getDisplayName(), computeChunkTiming(run, pauseDurations[i], parallelStart, start, end, parallelEnd));
        }
        return timings;
    }

    /**
     * Compute status codes for a set of parallel branches
     * @param run Run containing these nodes
     * @param branchStarts The nodes starting off each parallel branch (BlockStartNode)
     * @param branchEnds Last node in each parallel branch - currentHeads if currently executing and not a BlockEndNode if currently executing the parallels
     * @param parallelStart Start node for  overall parallel block
     * @param parallelEnd End node for the overall parallelBlock (null if not complete)
     * @return
     */
    public static Map<String, GenericStatus> computeBranchStatuses(@Nonnull WorkflowRun run,
                                                               @Nonnull List<BlockStartNode> branchStarts,
                                                               @Nonnull List<FlowNode> branchEnds,
                                                               @Nonnull FlowNode parallelStart, @CheckForNull FlowNode parallelEnd) {
        verifySameRun(run, branchStarts.toArray(new FlowNode[0]));
        verifySameRun(run, branchEnds.toArray(new FlowNode[0]));
        if (branchStarts.size() != branchEnds.size()) {
            throw new IllegalArgumentException("Mismatched start and stop node counts: "+branchStarts.size()+","+branchEnds.size());
        }
        HashMap<String, GenericStatus> statusMappings = new HashMap<String, GenericStatus>();
        for (int i=0; i<branchEnds.size(); i++) {
            BlockStartNode start = branchStarts.get(i);
            FlowNode end = branchEnds.get(i);
            if (end instanceof BlockEndNode && start != ((BlockEndNode)end).getStartNode()) {
                throw new IllegalArgumentException("Mismatched parallel branch start/end nodes: "
                        + start.getId()+','
                        + end.getId());
            }
            LabelAction label = start.getAction(LabelAction.class);
            assert label != null;
            statusMappings.put(label.getDisplayName(), getChunkStatus(run, parallelStart, start, end, parallelEnd));
        }
        return statusMappings;
    }

    /** Combines the status results from a list of parallel branches to report a single overall status */
    public static GenericStatus condenseStatus(@Nonnull Collection<GenericStatus> statuses) {
        return Collections.max(statuses);
    }
}
