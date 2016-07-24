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

import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;

/**
 * Provides common APIs for doing status and timing computations on flows
 * <p/> <strong>Concepts:</strong> a chunk, which is a set of {@link FlowNode}s in the same {@link FlowExecution} with a first and last node.
 * <p/> Chunks exist in a context: the FlowNode before and the FlowNode after.  These follow common-sense rules:
 * <ol>
 *     <li>If a chunk has a null before node, then its first node must be the {@link FlowStartNode} for that execution</li>
 *     <li>If a chunk has a null after node, then its last node must be the {@link FlowEndNode} for that execution</li>
 *     <li>Both may be true at once (then the chunk contains the entire execution)</li>
 *     <li>First nodes must always occur before last nodes</li>
 *     <li>Where a {@link WorkflowRun} is a parameter, it and the FlowNodes must all belong to the same execution</li>
 * </ol>
 * @author Sam Van Oort
 */
public class StatusAndTiming {

    // Sorted in increasing priority
    public enum GenericStatus {
        /** Can't be determined for whatever reason, possibly in an undefined or transitory state */
        UNKNOWN,

        /** We resumed from checkpoint or {@link Result#NOT_BUILT} status */
        NOT_EXECUTED,

        /** Success, ex {@link Result#SUCCESS} */
        SUCCESS,

        /** Recoverable failures, such as noncritical tests, ex {@link Result#UNSTABLE} */
        UNSTABLE,

        /** Still executing, waiting for a result */
        IN_PROGRESS,

        /** Ran and explicitly failed, i.e. {@link Result#FAILURE} */
        FAILURE,

        /** Aborted while running, no way to determine final outcome {@link Result#ABORTED} */
        ABORTED,

        /** We are waiting for user input to continue (special case IN_PROGRESS */
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

    // TODO create a version of the core APIs that takes a FlowChunk from the workflow-api library

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
     * Check that all the flownodes & run describe the same pipeline run/execution
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

    /** Return true if the run is paused on input */
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

    /**
     * Compute the overall status for a chunk, see assumptions at top
     * @param run
     * @param before
     * @param firstNode
     * @param lastNode
     * @param after
     * @return
     */
    @Nonnull
    public static GenericStatus computeChunkStatus(@Nonnull WorkflowRun run,
                                                   @CheckForNull FlowNode before, @Nonnull FlowNode firstNode,
                                                   @Nonnull FlowNode lastNode, @CheckForNull FlowNode after) {
        FlowExecution exec = run.getExecution();
        verifySameRun(run, before, firstNode, lastNode, after);
        if (!NotExecutedNodeAction.isExecuted(lastNode) || exec == null) {
            return GenericStatus.NOT_EXECUTED;
        }
        // TODO handle assignment of errors to a run of nodes where there is an ErrorAction
        boolean isLastChunk = after == null && exec.isCurrentHead(lastNode);
        if (isLastChunk) {
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
                    return GenericStatus.FAILURE;
                } else if (r == Result.UNSTABLE ) {
                    return GenericStatus.UNSTABLE;
                } else if (r == Result.SUCCESS) {
                    return GenericStatus.SUCCESS;
                } else {
                    return GenericStatus.FAILURE;
                }
            }
        }

        // Previous chunk before end. If flow continued beyond this, it didn't fail.
        // TODO check that previous assertion... what about blocks where the lastNode doesn't include BlockEndNode?
        return (run.getResult() == Result.UNSTABLE) ? GenericStatus.UNSTABLE : GenericStatus.SUCCESS;
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
        FlowExecution exec = run.getExecution();
        if (exec == null) {
            return null; // Haven't begun, timing is invalid
        }
        verifySameRun(run, before, firstNode, lastNode, after);
        long startTime = TimingAction.getStartTime(firstNode);
        long endTime = (after != null) ? TimingAction.getStartTime(after) : System.currentTimeMillis();

        if (!NotExecutedNodeAction.isExecuted(lastNode)) {
            return new TimingInfo(0,0);  // Nothing ran
        }
        if (before == null) {
            startTime = run.getStartTimeInMillis();
        }
        if (after == null && exec.isComplete()) {
            endTime = run.getDuration() + run.getStartTimeInMillis();
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
    @CheckForNull
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

    @Nonnull
    public static Map<String, TimingInfo> computeParallelBranchTimings(@Nonnull WorkflowRun run,
                                                                       @Nonnull FlowNode parallelStart,
                                                                       @Nonnull List<BlockStartNode> branchStarts,
                                                                       @Nonnull List<FlowNode> branchEnds,
                                                                       @CheckForNull FlowNode parallelEnd,
                                                                       @Nonnull long[] pauseDurations) {

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
    @Nonnull
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
            statusMappings.put(label.getDisplayName(), computeChunkStatus(run, parallelStart, start, end, parallelEnd));
        }
        return statusMappings;
    }

    /** Combines the status results from a list of parallel branches to report a single overall status */
    @Nonnull
    public static GenericStatus condenseStatus(@Nonnull Collection<GenericStatus> statuses) {
        if (statuses.isEmpty()) {
            return GenericStatus.UNKNOWN; // Undefined
        }
        return Collections.max(statuses);
    }
}
