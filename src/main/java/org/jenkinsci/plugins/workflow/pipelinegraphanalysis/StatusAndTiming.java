/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import hudson.model.Action;
import hudson.model.Result;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.NotExecutedNodeAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk;
import org.jenkinsci.plugins.workflow.graphanalysis.ParallelMemoryFlowChunk;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides common, comprehensive set of APIs for doing status and timing computations on pieces of a pipeline execution.
 *
 * <p> <strong>Concepts:</strong> a chunk, which is a set of {@link FlowNode}s in the same {@link FlowExecution} with a first and last node. </p>
 * <p> Chunks exist in a context: the FlowNode before and the FlowNode after.  These follow common-sense rules: </p>
 * <ol>
 *     <li>If a chunk has a null before node, then its first node must be the {@link FlowStartNode} for that execution</li>
 *     <li>If a chunk has a null after node, then its last node must be the {@link FlowEndNode} for that execution</li>
 *     <li>Both may be true at once (then the chunk contains the entire execution)</li>
 *     <li>First nodes must always occur before last nodes</li>
 *     <li>Where a {@link WorkflowRun} is a parameter, it and the FlowNodes must all belong to the same execution</li>
 * </ol>
 * <p> <strong>Parallel branch handling:</strong> </p>
 * <ol>
 *     <li>Each branch is considered independent</li>
 *     <li>Branches may succeed, fail, or be in-progress/waiting for input.</li>
 * </ol>
 * @author Sam Van Oort
 */
public class StatusAndTiming {

    /**
     * Check that all the flownodes &amp; run describe the same pipeline run/execution
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

    /**
     * Return true if the run is paused on input
     * @param run
     * @return
     */
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
     * Return status or null if not executed all (null FlowExecution)
     * @param run
     * @param chunk
     * @return Status or null if not executed all (null FlowExecution)
     */
    @CheckForNull
    public static GenericStatus computeChunkStatus(@Nonnull WorkflowRun run, @Nonnull MemoryFlowChunk chunk) {
        FlowExecution exec = run.getExecution();
        if (exec == null) {
            return null;
        }
        if (chunk instanceof ParallelMemoryFlowChunk) {
            ParallelMemoryFlowChunk par = ((ParallelMemoryFlowChunk) chunk);
            return condenseStatus(computeBranchStatuses(run, par).values());
        } else {
            return computeChunkStatus(run, chunk.getNodeBefore(), chunk.getFirstNode(), chunk.getLastNode(), chunk.getNodeAfter());
        }
    }

    /**
     * Compute the overall status for a chunk comprising firstNode through lastNode, inclusive
     * <p> All nodes must be in the same execution </p>
     * <p> Note: for in-progress builds with parallel branches, if the branch is done, it has its own status. </p>
     * @param run Run that nodes belong to
     * @param before Node before the first node in this piece
     * @param firstNode First node of this piece
     * @param lastNode Last node of this piece (if lastNode==firstNode, it's a single FlowNode)
     * @param after Node after this piece, null if the lastNode is the currentHead of the flow
     * @return Status for the piece, or null if the FlowExecution is null.
     */
    @CheckForNull
    public static GenericStatus computeChunkStatus(@Nonnull WorkflowRun run,
                                                   @CheckForNull FlowNode before, @Nonnull FlowNode firstNode,
                                                   @Nonnull FlowNode lastNode, @CheckForNull FlowNode after) {
        FlowExecution exec = run.getExecution();
        verifySameRun(run, before, firstNode, lastNode, after);
        if (exec == null) {
            return null;
        }
        if (!NotExecutedNodeAction.isExecuted(lastNode)) {
            return GenericStatus.NOT_EXECUTED;
        }
        boolean isLastChunk = after == null || exec.isCurrentHead(lastNode);
        if (isLastChunk) {
            if (run.isBuilding()) {
                if (exec.getCurrentHeads().size() > 1 && lastNode instanceof BlockEndNode) {  // Check to see if all the action is on other branches
                    BlockStartNode start = ((BlockEndNode)lastNode).getStartNode();
                    if (start.getAction(ThreadNameAction.class) != null) {
                        return (lastNode.getError() == null) ? GenericStatus.SUCCESS : GenericStatus.FAILURE;
                    }
                }
                PauseAction pauseAction = lastNode.getAction(PauseAction.class);
                return (isPendingInput(run) &&
                        pauseAction != null && pauseAction.getCause().equals("Input"))
                        ? GenericStatus.PAUSED_PENDING_INPUT : GenericStatus.IN_PROGRESS;
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
                } else {
                    return GenericStatus.SUCCESS;
                }
            }
        }
        ErrorAction err = lastNode.getError();
        if (err != null) {
            // If next node lacks and ErrorAction, the error was caught in a catch block
            return (after.getError() == null) ? GenericStatus.SUCCESS : GenericStatus.FAILURE;
        }

        // Previous chunk before end. If flow continued beyond this, it didn't fail.
        return (run.getResult() == Result.UNSTABLE) ? GenericStatus.UNSTABLE : GenericStatus.SUCCESS;
    }

    @CheckForNull
    public static TimingInfo computeChunkTiming(@Nonnull WorkflowRun run, long internalPauseDuration, @Nonnull MemoryFlowChunk chunk) {
        return computeChunkTiming(run, internalPauseDuration, chunk.getFirstNode(), chunk.getLastNode(), chunk.getNodeAfter());
    }

    /**
     * Compute timing for a chunk of nodes
     * <p> Note: for in-progress builds with parallel branches, the running branches end at the current time.
     *      Completed branches use the time at which the {@link BlockEndNode} terminating the branch was created. </p>
     * @param run WorkflowRun they all belong to
     * @param internalPauseDuration Millis paused in the chunk (including the ends)
     * @param firstNode First node in the chunk
     * @param lastNode Last node in the chunk
     * @param after Node after the chunk, if null we assume this chunk is at the end of the flow
     * @return Best guess at timing, or null if we can't compute anything (no FlowExecution exists)
     */
    @CheckForNull
    public static TimingInfo computeChunkTiming(@Nonnull WorkflowRun run, long internalPauseDuration,
                                        @Nonnull FlowNode firstNode,
                                        @Nonnull FlowNode lastNode, @CheckForNull FlowNode after) {
        FlowExecution exec = run.getExecution();
        if (exec == null) {
            return null; // Haven't begun execution, or execution was hard-killed, timing is invalid
        }
        if (!NotExecutedNodeAction.isExecuted(lastNode)) {
            return new TimingInfo(0,0,0);  // Nothing ran
        }
        verifySameRun(run, firstNode, lastNode, after);
        long endTime = (after != null) ? TimingAction.getStartTime(after) : System.currentTimeMillis();

        // Fudge
        boolean isLastChunk = after == null || exec.isCurrentHead(lastNode);
        if (isLastChunk && run.isBuilding()) {
            if (exec.getCurrentHeads().size() > 1 && lastNode instanceof BlockEndNode) {  // Check to see if all the action is on other branches
                BlockStartNode start = ((BlockEndNode)lastNode).getStartNode();
                if (start.getAction(ThreadNameAction.class) != null) {
                    endTime = TimingAction.getStartTime(lastNode);  // Completed parallel branch, use the block end time
                }
            }
        }
        // What about null startTime???
        long startTime = (firstNode instanceof FlowStartNode) ? run.getStartTimeInMillis() : TimingAction.getStartTime(firstNode);
        if (after == null && exec.isComplete()) {
            endTime = run.getDuration() + run.getStartTimeInMillis();
        }

        return new TimingInfo((endTime-startTime), Math.min(Math.abs(internalPauseDuration), (endTime-startTime)), startTime);
    }

    /**
     * Computes the branch timings for a set of parallel branches.
     * This will comprise the longest pause time from any branch, and overall runtime.
     * @param run
     * @param branchTimings Map of branch name : precomputed timing info
     * @param parallelStart
     * @param parallelEnd
     * @return
     */
    @CheckForNull
    public static TimingInfo computeOverallParallelTiming(@Nonnull WorkflowRun run,
                                                          @Nonnull Map<String, TimingInfo> branchTimings,
                                                          @Nonnull FlowNode parallelStart, @CheckForNull FlowNode parallelEnd) {
        long overallDuration = 0;
        long maxPause = 0;
        boolean isIncomplete = parallelEnd == null || run.isBuilding();
        for (TimingInfo t : branchTimings.values()) {
            maxPause = Math.max(maxPause, t.getPauseDurationMillis());
            if (isIncomplete) {
                overallDuration = Math.max(overallDuration, t.getTotalDurationMillis());
            }
        }
        long start = TimingAction.getStartTime(parallelStart);
        if (!isIncomplete) {
            overallDuration = TimingAction.getStartTime(parallelEnd) - start;
        }
        return new TimingInfo(overallDuration, maxPause, start);
    }

    /**
     * Compute timing for all branches of a parallel
     * @param run Run the branches belong to
     * @param parallelStart Start of parallel block
     * @param branchStarts Nodes that begin each parallel branch
     * @param branchEnds Nodes that represent the "tip" of each parallel branch (may be the end node, or just the latest)
     * @param parallelEnd End of parallel block (null if in progress)
     * @param pauseDurations Accumulated pause durations for each of the branches, in order
     * @return Map of branch name to timing.
     */
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
            ThreadNameAction branchName = start.getAction(ThreadNameAction.class);
            assert branchName != null;
            timings.put(branchName.getThreadName(), computeChunkTiming(run, pauseDurations[i], start, end, parallelEnd));
        }
        return timings;
    }

    @Nonnull
    /** Get statuses for each branch - note: some statuses may be null, */
    public static Map<String, GenericStatus> computeBranchStatuses(@Nonnull WorkflowRun run, @Nonnull ParallelMemoryFlowChunk parallel) {
        Map<String,MemoryFlowChunk> branches = parallel.getBranches();
        List<BlockStartNode> starts = new ArrayList<BlockStartNode>(branches.size());
        List<FlowNode> ends = new ArrayList<FlowNode>(branches.size());
        // We can optimize this if needed by not fetching the LabelAction below
        for (MemoryFlowChunk chunk : branches.values()) {
            starts.add((BlockStartNode)chunk.getFirstNode());
            ends.add(chunk.getLastNode());
        }
        return computeBranchStatuses(run, parallel.getFirstNode(), starts, ends, parallel.getLastNode());
    }

    /**
     * Compute status codes for a set of parallel branches.
     * <p> Note per {@link #computeChunkStatus(WorkflowRun, MemoryFlowChunk)} for in-progress builds with
     *     parallel branches, if the branch is done, it has its own status. </p>
     * @param run Run containing these nodes
     * @param branchStarts The nodes starting off each parallel branch (BlockStartNode)
     * @param branchEnds Last node in each parallel branch - might be the end of the branch, or might just be the latest step run
     * @param parallelStart Start node for  overall parallel block
     * @param parallelEnd End node for the overall parallelBlock (null if not complete)
     * @return Map of branch name to its status
     */
    @Nonnull
    public static Map<String, GenericStatus> computeBranchStatuses(@Nonnull WorkflowRun run,
                                                                   @Nonnull FlowNode parallelStart,
                                                                   @Nonnull List<BlockStartNode> branchStarts,
                                                                   @Nonnull List<FlowNode> branchEnds,
                                                                   @CheckForNull FlowNode parallelEnd) {
        verifySameRun(run, branchStarts.toArray(new FlowNode[0]));
        verifySameRun(run, branchEnds.toArray(new FlowNode[0]));
        verifySameRun(run, parallelStart, parallelEnd);
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
            ThreadNameAction branchName = start.getAction(ThreadNameAction.class);
            assert branchName != null;
            statusMappings.put(branchName.getThreadName(), computeChunkStatus(run, parallelStart, start, end, parallelEnd));
        }
        return statusMappings;
    }

    /**
     * Combines the status results from a list of parallel branches to report a single overall status
     * @param statuses
     * @return Status, or null if none can be defined
     */
    @CheckForNull
    public static GenericStatus condenseStatus(@Nonnull Collection<GenericStatus> statuses) {
        if (statuses.isEmpty()) {
            return null;
        }
        return Collections.max(statuses);
    }


    /**
     * Helper, prints flow graph in some detail - now a common utility so others don't have to reinvent it
     * @param run Run to show nodes for
     * @param showTiming
     * @param showActions
     */
    @Restricted(DoNotUse.class)
    public static void printNodes(@Nonnull WorkflowRun run, boolean showTiming, boolean showActions) {
        long runStartTime = run.getStartTimeInMillis();
        FlowExecution exec = run.getExecution();
        if (exec == null) {
            return;
        }
        DepthFirstScanner scanner = new DepthFirstScanner();
        List<FlowNode> sorted = scanner.filteredNodes(exec.getCurrentHeads(), (Predicate) Predicates.alwaysTrue());
        Collections.sort(sorted, new Comparator<FlowNode>() {
            @Override
            public int compare(FlowNode node1, FlowNode node2) {
                int node1Iota = parseIota(node1);
                int node2Iota = parseIota(node2);

                if (node1Iota < node2Iota) {
                    return -1;
                } else if (node1Iota > node2Iota) {
                    return 1;
                }
                return 0;
            }

            private int parseIota(FlowNode node) {
                try {
                    return Integer.parseInt(node.getId());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        });
        System.out.println("Node dump follows, format:");
        System.out.println("[ID]{parent,ids}(millisSinceStartOfRun) flowNodeClassName stepDisplayName [st=startId if a block end node]");
        System.out.println("Action format: ");
        System.out.println("\t- actionClassName actionDisplayName");
        System.out.println("------------------------------------------------------------------------------------------");
        Function<FlowNode, String> flowNodeToId = new Function<FlowNode, String>(){
            @Override
            public String apply(@Nullable FlowNode input) {
                return (input != null) ? input.getId() : null;
            }
        };
        for (FlowNode node : sorted) {
            StringBuilder formatted = new StringBuilder();
            formatted.append('[').append(node.getId()).append(']');
            formatted.append('{').append(StringUtils.join(Collections2.transform(node.getParents(), flowNodeToId), ',')).append('}');
            if (showTiming) {
                formatted.append('(');
                if (node.getAction(TimingAction.class) != null) {
                    formatted.append(TimingAction.getStartTime(node)-runStartTime);
                } else {
                    formatted.append("N/A");
                }
                formatted.append(')');
            }
            formatted.append(node.getClass().getSimpleName()).append(' ').append(node.getDisplayName());
            if (node instanceof BlockEndNode) {
                formatted.append("  [st=").append(((BlockEndNode)node).getStartNode().getId()).append(']');
            }
            if (showActions) {
                for (Action a : node.getActions()) {
                    if (!(a instanceof TimingAction)) {
                        formatted.append("\n  -").append(a.getClass().getSimpleName()).append(' ').append(a.getDisplayName());
                    }
                }
            }
            System.out.println(formatted);
        }
        System.out.println("------------------------------------------------------------------------------------------");
    }
}
