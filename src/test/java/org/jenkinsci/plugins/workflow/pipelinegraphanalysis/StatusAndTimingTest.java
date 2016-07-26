package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import com.cloudbees.workflow.flownode.FlowNodeUtil;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by svanoort on 7/19/16.
 */
public class StatusAndTimingTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    String pipelinePrefix = "pipelines/";

    public String readResource(String location) throws IOException {
        InputStream strm = getClass().getClassLoader().getResourceAsStream(location);
        return IOUtils.toString(strm, "utf8");
    }

    // Helper
    FlowNode[] getNodes(FlowExecution exec, int[] ids) throws IOException {
        FlowNode[] output = new FlowNode[ids.length];
        for (int i=0; i < ids.length; i++) {
            output[i] = exec.getNode(Integer.toString(ids[i]));
        }
        return output;
    }

    // Helper
    public long doTiming(FlowExecution exec, int firstNodeId, int nodeAfterEndId) throws  IOException {
        long startTime = TimingAction.getStartTime(exec.getNode(Integer.toString(firstNodeId)));
        long endTime = TimingAction.getStartTime(exec.getNode(Integer.toString(nodeAfterEndId)));
        return endTime-startTime;
    }

    @Test
    public void testBasicPass() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Passes");
        job.setDefinition(new CpsFlowDefinition("" +
                "sleep 1 \n" +
                "stage 'first' \n" +
                "sleep 1 \n" +
                "echo 'done' \n"));

        /** Node dump follows, format:
         [ID]{parent,ids}(millisSinceStartOfRun) flowClassName displayName [st=startId if a block node]
         Action format:
         - actionClassName actionDisplayName

         [2]{}(N/A)FlowStartNode Start of Pipeline
         [3]{2}(814)StepAtomNode Sleep
         [4]{3}(1779)StepAtomNode first
         -LogActionImpl Console Output
         -LabelAction first
         -StageActionImpl null
         [5]{4}(1787)StepAtomNode Sleep
         [6]{5}(2793)StepAtomNode Print Message
         -LogActionImpl Console Output
         [7]{6}(2796)FlowEndNode End of Pipeline  [st=2]
         */
        QueueTaskFuture<WorkflowRun> build = job.scheduleBuild2(0);
        j.assertBuildStatusSuccess(build);
        WorkflowRun run = build.get();
        long startTime = run.getStartTimeInMillis();

        // Test status handling with the first few nodes
        FlowNode[] n = getNodes(run.getExecution(), new int[]{2, 3, 4, 5, 6, 7});
        GenericStatus status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[1], n[2]);
        TimingInfo timing = StatusAndTiming.computeChunkTiming(run, 0, null, n[0], n[1], n[2]);
        Assert.assertEquals(GenericStatus.SUCCESS, status);
        Assert.assertEquals(0, timing.getPauseDurationMillis());
        Assert.assertEquals(TimingAction.getStartTime(n[2]) - run.getStartTimeInMillis(), timing.getTotalDurationMillis());

        // Everything but start/end
        status = StatusAndTiming.computeChunkStatus(run, n[0], n[1], n[4], n[5]);
        timing = StatusAndTiming.computeChunkTiming(run, 2, n[0], n[1], n[4], n[5]);
        Assert.assertEquals(GenericStatus.SUCCESS, status);
        Assert.assertEquals(timing.getPauseDurationMillis(), 2);
        Assert.assertEquals(TimingAction.getStartTime(n[5]) - TimingAction.getStartTime(n[1]), timing.getTotalDurationMillis());

        // Whole flow
        status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[5], null);
        timing = StatusAndTiming.computeChunkTiming(run, 0, null, n[0], n[5], null);
        Assert.assertEquals(GenericStatus.SUCCESS, status);
        Assert.assertEquals(0, timing.getPauseDurationMillis());
        Assert.assertEquals(run.getDuration(), timing.getTotalDurationMillis());

        // Custom unstable status
        run.setResult(Result.UNSTABLE);
        status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[1], n[2]);
        Assert.assertEquals(GenericStatus.UNSTABLE, status);

        // Failure should assume last chunk ran is where failure happened
        run.setResult(Result.FAILURE);
        status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[1], n[2]);
        Assert.assertEquals(GenericStatus.SUCCESS, status);

        // First non-start node to final end node
        status = StatusAndTiming.computeChunkStatus(run, n[0], n[1], n[5], null);
        Assert.assertEquals(GenericStatus.FAILURE, status);

        // Whole flow except for end... since no errors here, failure must be at end!
        status = StatusAndTiming.computeChunkStatus(run, n[0], n[1], n[4], n[5]);
        Assert.assertEquals(GenericStatus.SUCCESS, status);
    }

    /** Tests the assignment of error nodes to flows */
    @Test
    public void testFail() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Fails");
        job.setDefinition(new CpsFlowDefinition("" +
                "sleep 1 \n" +
                "stage 'first' \n" +
                "sleep 1 \n" +
                "error('fails') \n"));
        /**  Node dump follows, format:
        [ID]{parent,ids} flowClassName displayName [st=startId if a block node]
        Action format:
        - actionClassName actionDisplayName
                ------------------------------------------------------------------------------------------
        [2]{}FlowStartNode Start of Pipeline
        [3]{2}StepAtomNode Sleep
        [4]{3}StepAtomNode first
             -LogActionImpl Console Output
             -LabelAction first
             -StageActionImpl null
        [5]{4}StepAtomNode Sleep
        [6]{5}StepAtomNode Error signal
             -ErrorAction fails
             -ErrorAction fails
        [7]{6}FlowEndNode End of Pipeline  [st=2]
             -ErrorAction fails
        */
        QueueTaskFuture<WorkflowRun> build = job.scheduleBuild2(0);
        WorkflowRun run = build.get();
        FlowExecution exec = run.getExecution();
        j.assertBuildStatus(Result.FAILURE, run);

        // Whole flow
        Assert.assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(
                run, null, exec.getNode("2"), exec.getNode("7"), null));

        // Start through to failure point
        Assert.assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(
                run, null, exec.getNode("2"), exec.getNode("6"), exec.getNode("7")));

        // All but first/last node
        Assert.assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(
                run, exec.getNode("2"), exec.getNode("3"), exec.getNode("6"), exec.getNode("7")));

        // Before failure node
        Assert.assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(
                run, exec.getNode("2"), exec.getNode("3"), exec.getNode("5"), exec.getNode("6")));
    }

    @Test
    public void testBasicParallelFail() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Fails");
        job.setDefinition(new CpsFlowDefinition("" +
                "stage 'primero'\n" +
                "def branches = ['failFast': false]\n" +
                "branches['success'] = {sleep 1; echo 'succeed'}\n" +
                "branches['fail'] = {error('autofail');}\n" +
                "parallel branches"));

        /**
         * Node dump from a run follows, format:
         [ID]{parent,ids}(millisSinceStartOfRun) flowClassName displayName [st=startId if a block node]
         Action format: (key actions only)
         - actionClassName actionDisplayName
         ------------------------------------------------------------------------------------------
         [2]{}(N/A)FlowStartNode Start of Pipeline
         [3]{2}StepAtomNode primero
         -LabelAction primero,
         -StageActionImpl null
         [4]{3}(924)StepStartNode Execute in parallel : Start
         [6]{4}(926)StepStartNode Branch: success
         -ParallelLabelAction Branch: success
         [7]{4}(928)StepStartNode Branch: fail
         -ParallelLabelAction Branch: fail
         [8]{6}(930)StepAtomNode Sleep
         [9]{7}(932)StepAtomNode Error signal
         -ErrorAction autofail
         [10]{9}(938)StepEndNode Execute in parallel : Body : End  [st=7]
         -ErrorAction autofail
         [11]{8}(1827)StepAtomNode Print Message
         [12]{11}(1829)StepEndNode Execute in parallel : Body : End  [st=6]
         [13]{12,10}(1845)StepEndNode Execute in parallel : End  [st=4]
         -ErrorAction Parallel step fail failed
         -ErrorAction Parallel step fail failed
         [14]{13}(1867)FlowEndNode End of Pipeline  [st=2]
         -ErrorAction Parallel step fail failed
         */

        QueueTaskFuture<WorkflowRun> build = job.scheduleBuild2(0);
        WorkflowRun run = build.get();
        j.assertBuildStatus(Result.FAILURE, run);
        FlowExecution exec = run.getExecution();

        // Overall flow
        Assert.assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(
                run, null, exec.getNode("2"), exec.getNode("14"), null));

        // Failing branch
        Assert.assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(
                run, exec.getNode("4"), exec.getNode("7"), exec.getNode("10"), exec.getNode("13")));

        // Passing branch
        Assert.assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(
                run, exec.getNode("4"), exec.getNode("6"), exec.getNode("12"), exec.getNode("13")));

        // Check that branch statuses match
        List<BlockStartNode> parallelStarts = Arrays.asList((BlockStartNode) (exec.getNode("6")), (BlockStartNode) (exec.getNode("7")));
        List<FlowNode> parallelEnds = Arrays.asList(exec.getNode("12"), exec.getNode("10"));
        Map<String, GenericStatus> branchStatuses = StatusAndTiming.computeBranchStatuses(run, exec.getNode("4"),
                parallelStarts, parallelEnds,
                exec.getNode("13"));

        Assert.assertEquals(2, branchStatuses.size());
        String[] branches = {"fail", "success"};
        List<String> outputBranchList = new ArrayList<String>(branchStatuses.keySet());
        Collections.sort(outputBranchList);
        Assert.assertArrayEquals(branches, outputBranchList.toArray());
        Assert.assertEquals(GenericStatus.FAILURE, branchStatuses.get("fail"));
        Assert.assertEquals(GenericStatus.SUCCESS, branchStatuses.get("success"));

        // Verify that overall status returns as failure
        Assert.assertEquals(GenericStatus.FAILURE, StatusAndTiming.condenseStatus(branchStatuses.values()));

        // Check timing computation for individual branches
        long[] simulatedPauses = {50L, 5L}; // success, fail
        Map<String, TimingInfo> branchTimings = StatusAndTiming.computeParallelBranchTimings(
            run, exec.getNode("4"), parallelStarts, parallelEnds, exec.getNode("13"), simulatedPauses
        );
        outputBranchList = new ArrayList<String>(branchTimings.keySet());
        Collections.sort(outputBranchList);
        Assert.assertArrayEquals(branches, outputBranchList.toArray());

        // Passing branch time, 5 ms pause was a present above
        TimingInfo successTiming = branchTimings.get("success");
        Assert.assertEquals(50L, successTiming.getPauseDurationMillis());
        long successRunTime = doTiming(exec, 6, 13);
        Assert.assertEquals(successRunTime, successTiming.getTotalDurationMillis());

        // Failing branch time, 50 ms pause was a present above
        TimingInfo failTiming = branchTimings.get("fail");
        long failRunTime = doTiming(exec, 7, 13);
        Assert.assertEquals(Math.min(5L, failRunTime), failTiming.getPauseDurationMillis());
        Assert.assertEquals(failRunTime, failTiming.getTotalDurationMillis());

        // Check timing computation for overall result
        TimingInfo finalTiming = StatusAndTiming.computeOverallParallelTiming(
                run, branchTimings, exec.getNode("4"), exec.getNode("13")
        );
        long totalBranchTiming = TimingAction.getStartTime(exec.getNode("13")) - TimingAction.getStartTime(exec.getNode("4"));
        Assert.assertEquals(50L, finalTiming.getPauseDurationMillis());
        Assert.assertEquals(totalBranchTiming, finalTiming.getTotalDurationMillis());
    }

    /*@Test
    public void testInProgress() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Fails");
        job.setDefinition(new CpsFlowDefinition("" +
                "sleep 1 \n" +
                "stage 'first' \n" +
                "sleep 1 \n" +
                "semaphore('fails') \n"));
        QueueTaskFuture<WorkflowRun> build = job.scheduleBuild2(0);
        j.assertBuildStatusSuccess(build);
        WorkflowRun run = job.getLastBuild();
        long startTime = run.getStartTimeInMillis();
        FlowExecution exec = run.getExecution();
        printNodes(exec, startTime, true, true);
    }

    // TODO test case to test NotExecutedNodeAction, in-progress normal with timing, in-progress parallels with timing
    */

    @Test
    public void inputTest() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "InputJob");
        job.setDefinition(new CpsFlowDefinition("" + // FlowStartNode: ID 2
                "stage 'first' \n" + // FlowNode 3
                "echo 'print something' \n" + // FlowNode 4
                "input 'prompt' \n"));  // FlowNode 5, end node will be #6
        QueueTaskFuture<WorkflowRun> buildTask = job.scheduleBuild2(0);
        WorkflowRun run = buildTask.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) run.getExecutionPromise().get();
        while (run.getAction(InputAction.class)==null) {
            e.waitForSuspension();
        }
        e = (CpsFlowExecution)(run.getExecution());

        // Check that a pipeline paused on input gets the same status, and timing reflects in-progress node running through to current time
        GenericStatus status = StatusAndTiming.computeChunkStatus(run, null, e.getNode("2"), e.getNode("5"), null);
        Assert.assertEquals(GenericStatus.PAUSED_PENDING_INPUT, status);
        long currentTime = System.currentTimeMillis();
        TimingInfo timing = StatusAndTiming.computeChunkTiming(run, 0L, null, e.getNode("2"), e.getNode("5"), null);
        long runTime = currentTime - run.getStartTimeInMillis();
        Assert.assertEquals((double) (runTime), (double) (timing.getTotalDurationMillis()), 10.0); // Approx b/c depends on when currentTime gathered

        // Test the aborted builds are handled right
        // TODO figure out why the FlowExecution is null here...
        /*
        run.doKill();
        j.waitForCompletion(run);
        FlowExecution exec = run.getExecution();
        status = StatusAndTiming.computeChunkStatus(run, null, exec.getNode("2"), exec.getNode("6"), null);
        Assert.assertEquals(StatusAndTiming.GenericStatus.ABORTED, status);
        */
    }

    /** Helper, prints flow graph in some detail */
    public void printNodes(FlowExecution exec, long startTime, boolean showTiming, boolean showActions) {
        List<FlowNode> sorted = FlowNodeUtil.getIdSortedExecutionNodeList(exec);
        System.out.println("Node dump follows, format:");
        System.out.println("[ID]{parent,ids}(millisSinceStartOfRun) flowClassName displayName [st=startId if a block node]");
        System.out.println("Action format: ");
        System.out.println("\t- actionClassName actionDisplayName");
        System.out.println("------------------------------------------------------------------------------------------");
        for (FlowNode node : sorted) {
            StringBuilder formatted = new StringBuilder();
            formatted.append('[').append(node.getId()).append(']');
            formatted.append('{').append(StringUtils.join(node.getParentIds(), ',')).append('}');
            if (showTiming) {
                formatted.append('(');
                if (node.getAction(TimingAction.class) != null) {
                    formatted.append(TimingAction.getStartTime(node)-startTime);
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
                        formatted.append("\n\t-").append(a.getClass().getSimpleName()).append(' ').append(a.getDisplayName());
                    }
                }
            }
            System.out.println(formatted);
        }
        System.out.println("------------------------------------------------------------------------------------------");
    }
}
