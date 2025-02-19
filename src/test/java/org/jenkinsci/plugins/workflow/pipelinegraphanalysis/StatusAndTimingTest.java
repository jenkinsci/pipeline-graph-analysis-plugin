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

import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.DumbSlave;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.QueueItemAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk;
import org.jenkinsci.plugins.workflow.graphanalysis.NoOpChunkFinder;
import org.jenkinsci.plugins.workflow.graphanalysis.StandardChunkVisitor;
import org.jenkinsci.plugins.workflow.graphanalysis.TestVisitor;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.Ignore;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class StatusAndTimingTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    // Helper
    private static FlowNode[] getNodes(FlowExecution exec, int[] ids) throws IOException {
        FlowNode[] output = new FlowNode[ids.length];
        for (int i=0; i < ids.length; i++) {
            output[i] = exec.getNode(Integer.toString(ids[i]));
        }
        return output;
    }

    @Test
    public void testStatusCoercion() throws Exception {
        // Test that we don't modify existing statuses
        for (GenericStatus st : StatusAndTiming.API_V1.getAllowedStatuses()) {
            Assert.assertEquals(st, StatusAndTiming.coerceStatusApi(st, StatusAndTiming.API_V1));
        }
        // Test that we don't modify statuses of the same API versions
        for (GenericStatus st : StatusAndTiming.API_V2.getAllowedStatuses()) {
            Assert.assertEquals(st, StatusAndTiming.coerceStatusApi(st, StatusAndTiming.API_V2));
            Assert.assertTrue(StatusAndTiming.API_V1.getAllowedStatuses().contains(StatusAndTiming.coerceStatusApi(st, StatusAndTiming.API_V1)));
        }
        Assert.assertEquals(GenericStatus.IN_PROGRESS, StatusAndTiming.coerceStatusApi(GenericStatus.QUEUED, StatusAndTiming.API_V1));
    }

    // Helper
    private static long doTiming(FlowExecution exec, int firstNodeId, int nodeAfterEndId) throws  IOException {
        long startTime = TimingAction.getStartTime(exec.getNode(Integer.toString(firstNodeId)));
        long endTime = TimingAction.getStartTime(exec.getNode(Integer.toString(nodeAfterEndId)));
        return endTime-startTime;
    }

    @Test
    public void testBasicPass() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Passes");
        job.setDefinition(new CpsFlowDefinition("" +
                "sleep 1 \n" +
                "echo 'first stage' \n" +
                "sleep 1 \n" +
                "echo 'done' \n", true));

        /* Node dump follows, format:
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

        // Test status handling with the first few nodes
        FlowNode[] n = getNodes(run.getExecution(), new int[]{2, 3, 4, 5, 6, 7});
        GenericStatus status = StatusAndTiming.computeChunkStatus2(run, null, n[0], n[1], n[2]);
        TimingInfo timing = StatusAndTiming.computeChunkTiming(run, 0, n[0], n[1], n[2]);
        assertEquals(GenericStatus.SUCCESS, status);
        assertEquals(0, timing.getPauseDurationMillis());
        assertEquals(run.getStartTimeInMillis(), timing.getStartTimeMillis());
        assertEquals(TimingAction.getStartTime(n[2]) - run.getStartTimeInMillis(), timing.getTotalDurationMillis());

        // Everything but start/end
        status = StatusAndTiming.computeChunkStatus2(run, n[0], n[1], n[4], n[5]);
        timing = StatusAndTiming.computeChunkTiming(run, 2, n[1], n[4], n[5]);
        assertEquals(GenericStatus.SUCCESS, status);
        assertEquals(timing.getPauseDurationMillis(), 2);
        assertEquals(TimingAction.getStartTime(n[5]) - TimingAction.getStartTime(n[1]), timing.getTotalDurationMillis());

        // Whole flow
        status = StatusAndTiming.computeChunkStatus2(run, null, n[0], n[5], null);
        timing = StatusAndTiming.computeChunkTiming(run, 0, n[0], n[5], null);
        assertEquals(GenericStatus.SUCCESS, status);
        assertEquals(0, timing.getPauseDurationMillis());
        assertEquals(run.getDuration(), timing.getTotalDurationMillis());

        // Custom unstable status
        run.setResult(Result.UNSTABLE);
        status = StatusAndTiming.computeChunkStatus2(run, null, n[0], n[1], n[2]);
        assertEquals(GenericStatus.SUCCESS, status); // Overall build status is ignored for chunks.

        // Failure should assume last chunk ran is where failure happened
        run.setResult(Result.FAILURE);
        status = StatusAndTiming.computeChunkStatus2(run, null, n[0], n[1], n[2]);
        assertEquals(GenericStatus.SUCCESS, status);

        // First non-start node to final end node
        status = StatusAndTiming.computeChunkStatus2(run, n[0], n[1], n[5], null);
        assertEquals(GenericStatus.FAILURE, status);

        // Whole flow except for end... since no errors here, failure must be at end!
        status = StatusAndTiming.computeChunkStatus2(run, n[0], n[1], n[4], n[5]);
        assertEquals(GenericStatus.SUCCESS, status);
    }

    /** Tests the assignment of error nodes to flows */
    @Test
    public void testFail() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Fails");
        job.setDefinition(new CpsFlowDefinition("" +
                "sleep 1 \n" +
                "echo 'first stage' \n" +
                "sleep 1 \n" +
                "error('fails') \n", true));
        /*  Node dump follows, format:
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
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus2(
                run, null, exec.getNode("2"), exec.getNode("7"), null));

        // Start through to failure point
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus2(
                run, null, exec.getNode("2"), exec.getNode("6"), exec.getNode("7")));

        // All but first/last node
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus2(
                run, exec.getNode("2"), exec.getNode("3"), exec.getNode("6"), exec.getNode("7")));

        // Before failure node
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus2(
                run, exec.getNode("2"), exec.getNode("3"), exec.getNode("5"), exec.getNode("6")));
    }

    @Test
    public void testBasicParallelFail() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Fails");
        job.setDefinition(new CpsFlowDefinition("" +
                "echo 'primero stage'\n" +
                "def branches = ['failFast': false]\n" +
                "branches['success'] = {sleep 1; echo 'succeed'}\n" +
                "branches['fail'] = {error('autofail');}\n" +
                "parallel branches", true));

        /*
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
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus2(
                run, null, exec.getNode("2"), exec.getNode("14"), null));

        // Failing branch
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus2(
                run, exec.getNode("4"), exec.getNode("7"), exec.getNode("10"), exec.getNode("13")));

        // Passing branch
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus2(
                run, exec.getNode("4"), exec.getNode("6"), exec.getNode("12"), exec.getNode("13")));

        // Check that branch statuses match
        List<BlockStartNode> parallelStarts = Arrays.asList((BlockStartNode) exec.getNode("6"), (BlockStartNode) exec.getNode("7"));
        List<FlowNode> parallelEnds = Arrays.asList(exec.getNode("12"), exec.getNode("10"));
        Map<String, GenericStatus> branchStatuses = StatusAndTiming.computeBranchStatuses2(run, exec.getNode("4"),
                parallelStarts, parallelEnds,
                exec.getNode("13"));

        assertEquals(2, branchStatuses.size());
        String[] branches = {"fail", "success"};
        List<String> outputBranchList = new ArrayList<>(branchStatuses.keySet());
        Collections.sort(outputBranchList);
        Assert.assertArrayEquals(branches, outputBranchList.toArray());
        assertEquals(GenericStatus.FAILURE, branchStatuses.get("fail"));
        assertEquals(GenericStatus.SUCCESS, branchStatuses.get("success"));

        // Verify that overall status returns as failure
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.condenseStatus(branchStatuses.values()));

        // Check timing computation for individual branches
        long[] simulatedPauses = {50L, 5L}; // success, fail
        Map<String, TimingInfo> branchTimings = StatusAndTiming.computeParallelBranchTimings(
            run, exec.getNode("4"), parallelStarts, parallelEnds, exec.getNode("13"), simulatedPauses
        );
        outputBranchList = new ArrayList<>(branchTimings.keySet());
        Collections.sort(outputBranchList);
        Assert.assertArrayEquals(branches, outputBranchList.toArray());

        // Passing branch time, 5 ms pause was a present above
        TimingInfo successTiming = branchTimings.get("success");
        assertEquals(50L, successTiming.getPauseDurationMillis());
        long successRunTime = doTiming(exec, 6, 13);
        assertEquals(successRunTime, successTiming.getTotalDurationMillis());
        assertEquals(TimingAction.getStartTime(exec.getNode("6")), successTiming.getStartTimeMillis());

        // Failing branch time, 50 ms pause was a present above
        TimingInfo failTiming = branchTimings.get("fail");
        long failRunTime = doTiming(exec, 7, 13);
        assertEquals(Math.min(5L, failRunTime), failTiming.getPauseDurationMillis());
        assertEquals(failRunTime, failTiming.getTotalDurationMillis());
        assertEquals(TimingAction.getStartTime(exec.getNode("7")), failTiming.getStartTimeMillis());

        // Check timing computation for overall result
        TimingInfo finalTiming = StatusAndTiming.computeOverallParallelTiming(
                run, branchTimings, exec.getNode("4"), exec.getNode("13")
        );
        long totalBranchTiming = TimingAction.getStartTime(exec.getNode("13")) - TimingAction.getStartTime(exec.getNode("4"));
        assertEquals(50L, finalTiming.getPauseDurationMillis());
        assertEquals(totalBranchTiming, finalTiming.getTotalDurationMillis());
    }

    @Test
    public void testInProgress() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Fails");
        job.setDefinition(new CpsFlowDefinition("" +
                "sleep 1 \n" +
                "echo 'first stage' \n" +
                "sleep 1 \n" +
                "semaphore('wait') \n", true));
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("wait/1", run);
        FlowExecution exec = run.getExecution();
        assertEquals(GenericStatus.IN_PROGRESS, StatusAndTiming.computeChunkStatus2(
                run, null, exec.getNode("2"), exec.getNode("6"), null));
        long currTime = System.currentTimeMillis();
        TimingInfo tim = StatusAndTiming.computeChunkTiming(
                run, 0, exec.getNode("2"), exec.getNode("6"), null);
        assertEquals((double) (currTime - run.getStartTimeInMillis()), (double) tim.getTotalDurationMillis(), 20.0);
        SemaphoreStep.success("wait/1", null);
        j.waitForCompletion(run);
    }


    @Test
    public void timingTest() throws Exception {
        // Problem here: for runs in progress we should be using current time if they're the last run node, aka the in-progress node
        String jobScript = ""+
                "echo 'first stage'\n" +
                "parallel 'long' : { sleep 30; }, \n" +
                "         'short': { sleep 2; }";

        // This must be amateur science fiction because the exposition for the setting goes on FOREVER
        ForkScanner scan = new ForkScanner();
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "parallelTimes");
        job.setDefinition(new CpsFlowDefinition(jobScript, true));
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        try {
        Thread.sleep(12000);  // We need the short branch to be complete so we know timing should exceed its duration
        FlowExecution exec = run.getExecution();
        List<FlowNode> heads = exec.getCurrentHeads();
        assertEquals(GenericStatus.IN_PROGRESS, StatusAndTiming.computeChunkStatus2(
                run, null, exec.getNode("2"), heads.get(0), null));
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus2(
                run, null, exec.getNode("2"), heads.get(1), null));
        TestVisitor visitor = new TestVisitor();
        scan.setup(heads);
        ForkScanner.visitSimpleChunks(heads, visitor, new NoOpChunkFinder());
        TestVisitor.CallEntry entry = visitor.filteredCallsByType(TestVisitor.CallType.PARALLEL_END).get(0);
        FlowNode endNode = exec.getNode(entry.getNodeId().toString());
        assertEquals("sleep", endNode.getDisplayFunctionName());

        // Finally, the heart of the matter: test computing durations
        TimingInfo times = StatusAndTiming.computeChunkTiming(run, 0, exec.getNode("2"), exec.getNode(entry.getNodeId().toString()), null);
        assertTrue("Underestimated duration", times.getTotalDurationMillis() >= 3000);
        } finally {
            j.assertBuildStatusSuccess(j.waitForCompletion(run));
        }
    }

    @Test
    public void testInProgressParallel() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "Fails");
        job.setDefinition(new CpsFlowDefinition("" +
                "echo 'primero stage'\n" +
                "def branches = ['failFast': false]\n" +
                "branches['success'] = {echo 'succeed'}\n" +
                "branches['pause'] = { sleep 1; semaphore 'wait'; }\n" +
                "parallel branches", true));
        /*
         * Node dump follows, format:
         [ID]{parentIds,...} flowNodeClassName displayName [st=startId if a block node]
         Action format:
         - actionClassName actionDisplayName
         ------------------------------------------------------------------------------------------
         [2]{}FlowStartNode Start of Pipeline
         [3]{2}StepAtomNode primero
           -LogActionImpl Console Output
           -LabelAction primero
         -StageActionImpl null
         [4]{3}StepStartNode Execute in parallel : Start
           -LogActionImpl Console Output
         [6]{4}StepStartNode Branch: success
           -BodyInvocationAction null
           -ParallelLabelAction Branch: success
         [7]{4}StepStartNode Branch: pause
           -BodyInvocationAction null
           -ParallelLabelAction Branch: pause
         [8]{6}StepAtomNode Print Message
           -LogActionImpl Console Output
         [9]{8}StepEndNode Execute in parallel : Body : End  [st=6]
           -BodyInvocationAction null
         [10]{7}StepAtomNode Sleep
         [11]{10}StepAtomNode Test step
         */
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        try {
        SemaphoreStep.waitForStart("wait/1", run);
        FlowExecution exec = run.getExecution();

        // Test specific cases for status checking
        assertEquals(GenericStatus.IN_PROGRESS, // Whole flow, semaphore makes it "in-progress"
                StatusAndTiming.computeChunkStatus2(run, null, exec.getNode("2"), exec.getNode("11"), null));
        assertEquals(GenericStatus.SUCCESS, // Completed branch, waiting on parallel semaphore though
                StatusAndTiming.computeChunkStatus2(run, null, exec.getNode("2"), exec.getNode("9"), null));
        assertEquals(GenericStatus.SUCCESS, // Completed branch, just the branching bit
                StatusAndTiming.computeChunkStatus2(run, exec.getNode("4"), exec.getNode("6"), exec.getNode("9"), null));
        assertEquals(GenericStatus.IN_PROGRESS, // Just the in-progress branch
                StatusAndTiming.computeChunkStatus2(run, exec.getNode("4"), exec.getNode("7"), exec.getNode("11"), null));
        assertEquals(GenericStatus.SUCCESS, // All but the in-progress node in the in-progress branch
                StatusAndTiming.computeChunkStatus2(run, exec.getNode("4"), exec.getNode("7"), exec.getNode("10"), exec.getNode("11")));

        List<BlockStartNode> branchStartNodes = new ArrayList<>();
        branchStartNodes.add((BlockStartNode) exec.getNode("6"));
        branchStartNodes.add((BlockStartNode) exec.getNode("7"));
        List<FlowNode> branchEndNodes = Arrays.asList(getNodes(exec, new int[]{9, 11}));

        // All branch statuses
        Map<String, GenericStatus> statuses = StatusAndTiming.computeBranchStatuses2(run, exec.getNode("4"), branchStartNodes, branchEndNodes, null);
        Assert.assertArrayEquals(new String[]{"pause", "success"}, new TreeSet<>(statuses.keySet()).toArray());
        assertEquals(GenericStatus.SUCCESS, statuses.get("success"));
        assertEquals(GenericStatus.IN_PROGRESS, statuses.get("pause"));

        // Timings
        long incompleteBranchTime = System.currentTimeMillis()-TimingAction.getStartTime(exec.getNode("7"));
        Map<String, TimingInfo> timings = StatusAndTiming.computeParallelBranchTimings(run, exec.getNode("4"), branchStartNodes, branchEndNodes, null, new long[]{0, 0});

        // Completed branch uses time from start to end
        TimingInfo time = timings.get("success");
        assertEquals(0, time.getPauseDurationMillis());
        assertEquals((double) (TimingAction.getStartTime(exec.getNode("9")) - TimingAction.getStartTime(exec.getNode("6"))), (double) time.getTotalDurationMillis(), 2.0);

        // In-progress branch uses current time
        time = timings.get("pause");
        assertEquals(0, time.getPauseDurationMillis());

        TimingInfo info = StatusAndTiming.computeOverallParallelTiming(run, timings, exec.getNode("4"), null);
        assertEquals((double) incompleteBranchTime, (double) info.getTotalDurationMillis(), 2.0);

        SemaphoreStep.success("wait/1", null);
        } finally {
            j.assertBuildStatusSuccess(j.waitForCompletion(run));
        }
    }

    @Test
    public void inputTest() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "InputJob");
        job.setDefinition(new CpsFlowDefinition("" + // FlowStartNode: ID 2
                "echo 'first stage' \n" + // FlowNode 3
                "echo 'print something' \n" + // FlowNode 4
                "input 'prompt' \n", true));  // FlowNode 5, end node will be #6
        QueueTaskFuture<WorkflowRun> buildTask = job.scheduleBuild2(0);
        WorkflowRun run = buildTask.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) run.getExecutionPromise().get();
        while (run.getAction(InputAction.class)==null) {
            e.waitForSuspension();
        }
        e = (CpsFlowExecution) run.getExecution();

        // Check that a pipeline paused on input gets the same status, and timing reflects in-progress node running through to current time
        GenericStatus status = StatusAndTiming.computeChunkStatus2(run, null, e.getNode("2"), e.getNode("5"), null);
        assertEquals(GenericStatus.PAUSED_PENDING_INPUT, status);
        long currentTime = System.currentTimeMillis();
        TimingInfo timing = StatusAndTiming.computeChunkTiming(run, 0L, e.getNode("2"), e.getNode("5"), null);
        long runTime = currentTime - run.getStartTimeInMillis();
        assertEquals((double) runTime, (double) timing.getTotalDurationMillis(), 10.0); // Approx b/c depends on when currentTime gathered

        // Test the aborted builds are handled right
        run.doTerm();
        j.waitForCompletion(run);
        FlowExecution exec = run.getExecution();
        status = StatusAndTiming.computeChunkStatus2(run, null, exec.getNode("2"), exec.getNode("6"), null);
        assertEquals(GenericStatus.ABORTED, status);
    }

    @Test
    public void busyStepTest() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "InputJob");
        String sleep = "sh 'sleep 10000'\n";
        if(SystemUtils.IS_OS_WINDOWS){
            sleep = "bat 'timeout /t 30'\n";
        }
        job.setDefinition(new CpsFlowDefinition("node {\n" +
                "    stage(\"parallelStage\"){\n" +
                "      parallel left : {\n" +
                "            echo \"running\"\n" +
                "            input message: 'Please input branch to test against' \n" +
                "        }, \n" +
                "        right : {\n" +
                sleep + //13
                "        }\n" +
                "    }\n" +
                "}", true));
        QueueTaskFuture<WorkflowRun> buildTask = job.scheduleBuild2(0);
        WorkflowRun run = buildTask.getStartCondition().get();
        CpsFlowExecution e = (CpsFlowExecution) run.getExecutionPromise().get();
        while (run.getAction(InputAction.class)==null) {
            e.waitForSuspension();
        }
        e = (CpsFlowExecution) run.getExecution();
        GenericStatus status = StatusAndTiming.computeChunkStatus2(run, null, e.getNode("13"), e.getNode("13"), null);
        assertEquals(GenericStatus.IN_PROGRESS, status);

        status = StatusAndTiming.computeChunkStatus2(run, null, e.getNode("12"), e.getNode("12"), null);
        assertEquals(GenericStatus.PAUSED_PENDING_INPUT, status);

        run.doStop();
        j.waitForMessage("Sending interrupt signal to process", run);
        j.waitForCompletion(run);
        j.assertBuildStatus(Result.ABORTED, run);
    }

    @Issue("JENKINS-44981")
    @Test
    public void queuedAndRunningOnAgent() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "queuedAndRunning");
        job.setDefinition(new CpsFlowDefinition("stage('some-stage') {\n" +
                "  node('test') {\n" +
                "    echo 'hello'\n" +
                "    semaphore 'wait'\n" +
                "  }\n" +
                "}\n", true));

        WorkflowRun b1 = job.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Still waiting to schedule task", b1);

        CpsFlowExecution execution = (CpsFlowExecution) b1.getExecutionPromise().get();

        // node 2: FlowStartNode
        // node 5: first StepStartNode for node
        // node 8: semaphore
        FlowNode stepStart = execution.getNode("5");
        assertNotNull(stepStart);
        GenericStatus status = StatusAndTiming.computeChunkStatus2(b1, null, execution.getNode("2"), execution.getNode("5"), null);
        assertEquals(GenericStatus.QUEUED, status);
        assertEquals(QueueItemAction.QueueState.QUEUED, QueueItemAction.getNodeState(stepStart));

        DumbSlave agent = j.createSlave("test-agent", "test", null);

        SemaphoreStep.waitForStart("wait/1", b1);
        status = StatusAndTiming.computeChunkStatus2(b1, null, execution.getNode("2"), execution.getNode("8"), null);
        assertEquals(GenericStatus.IN_PROGRESS, status);

        stepStart = execution.getNode("5");
        assertNotNull(stepStart);
        assertEquals(QueueItemAction.QueueState.LAUNCHED, QueueItemAction.getNodeState(stepStart));

        SemaphoreStep.success("wait/1", null);
        j.assertBuildStatusSuccess(j.waitForCompletion(b1));
    }

    @Issue("JENKINS-44981")
    @Test
    public void queuedAndCanceled() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "queuedAndCanceled");
        job.setDefinition(new CpsFlowDefinition("stage('some-stage') {\n" +
                "  node('test') {\n" +
                "    echo 'hello'\n" +
                "    semaphore 'wait'\n" +
                "  }\n" +
                "}\n", true));

        WorkflowRun b1 = job.scheduleBuild2(0).waitForStart();
        j.waitForMessage("Still waiting to schedule task", b1);

        CpsFlowExecution execution = (CpsFlowExecution) b1.getExecutionPromise().get();

        // node 2: FlowStartNode
        // node 5: first StepStartNode for node
        FlowNode stepStart = execution.getNode("5");
        assertNotNull(stepStart);
        GenericStatus status = StatusAndTiming.computeChunkStatus2(b1, null, execution.getNode("2"), execution.getNode("5"), null);
        assertEquals(GenericStatus.QUEUED, status);
        assertEquals(QueueItemAction.QueueState.QUEUED, QueueItemAction.getNodeState(stepStart));

        Queue.Item[] items = Queue.getInstance().getItems();
        assertEquals(1, items.length);
        assertEquals(job, items[0].task.getOwnerTask());
        assertTrue(Queue.getInstance().cancel(items[0]));
        j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(b1));

        status = StatusAndTiming.computeChunkStatus2(b1, null, execution.getNode("2"), execution.getNode("5"), null);
        assertEquals(GenericStatus.ABORTED, status);

        stepStart = execution.getNode("5");
        assertNotNull(stepStart);
        assertEquals(QueueItemAction.QueueState.CANCELLED, QueueItemAction.getNodeState(stepStart));
    }

    @Issue("JENKINS-44981")
    @Test
    public void queuedAndParallel() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "queuedAndParallel");
        j.createSlave("first-agent", "first", null);

        /*
            Node dump follows, format:
            [ID]{parent,ids}flowNodeClassName stepDisplayName [st=startId if a block end node]
            ------------------------------------------------------------------------------------------
            [2]{}FlowStartNode Start of Pipeline
            [3]{2}StepStartNode Stage : Start
            [4]{3}StepStartNode some-stage
            [5]{4}StepStartNode Execute in parallel : Start
            [7]{5}StepStartNode Branch: a
            [8]{5}StepStartNode Branch: b
            [9]{7}StepStartNode Allocate node : Start
            [10]{8}StepStartNode Allocate node : Start
            [11]{10}StepStartNode Allocate node : Body : Start
            [12]{11}StepAtomNode Test step
            [13]{12}StepEndNode Allocate node : Body : End  [st=11]
            [14]{13}StepEndNode Allocate node : End  [st=10]
            [15]{14}StepEndNode Execute in parallel : Body : End  [st=8]
            [16]{9}StepStartNode Allocate node : Body : Start
            [17]{16}StepAtomNode Print Message
            [18]{17}StepAtomNode Test step
            [19]{18}StepEndNode Allocate node : Body : End  [st=16]
            [20]{19}StepEndNode Allocate node : End  [st=9]
            [21]{20}StepEndNode Execute in parallel : Body : End  [st=7]
            [22]{21,15}StepEndNode Execute in parallel : End  [st=5]
            [23]{22}StepEndNode Stage : Body : End  [st=4]
            [24]{23}StepEndNode Stage : End  [st=3]
            [25]{24}FlowEndNode End of Pipeline  [st=2]
            ------------------------------------------------------------------------------------------
        */
        job.setDefinition(new CpsFlowDefinition("stage('some-stage') {\n" +
                "  parallel(\n" +
                "    a: {\n" +
                "      node('second') {\n" +
                "        echo 'hello'\n" +
                "        semaphore 'wait-a'\n" +
                "      }\n" +
                "    },\n" +
                "    b: {\n" +
                "      node('first') {\n" +
                "        semaphore 'wait-b'\n" +
                "      }\n" +
                "    }\n" +
                "  )\n" +
                "}\n", true));

        WorkflowRun b1 = job.scheduleBuild2(0).waitForStart();
        try {
        SemaphoreStep.waitForStart("wait-b/1", b1);

        j.waitForMessage("Still waiting to schedule task", b1);

        CpsFlowExecution execution = (CpsFlowExecution) b1.getExecutionPromise().get();

        // Branch start nodes will be consistent across the whole run.
        List<BlockStartNode> branchStartNodes = new ArrayList<>();
        branchStartNodes.add((BlockStartNode) execution.getNode("7"));
        branchStartNodes.add((BlockStartNode) execution.getNode("8"));

        // Branch end nodes will be recreated later, though.
        List<FlowNode> branchEndNodes = Arrays.asList(getNodes(execution, new int[]{9, 12}));

        // All branch statuses
        Map<String, GenericStatus> statuses = StatusAndTiming.computeBranchStatuses2(b1, execution.getNode("5"), branchStartNodes, branchEndNodes, null);

        assertNotNull(statuses);
        assertEquals(GenericStatus.QUEUED, statuses.get("a"));
        assertEquals(GenericStatus.IN_PROGRESS, statuses.get("b"));
        assertEquals(GenericStatus.IN_PROGRESS, StatusAndTiming.condenseStatus(statuses.values()));

        FlowNode stepStart = execution.getNode("9");
        assertNotNull(stepStart);
        assertEquals(QueueItemAction.QueueState.QUEUED, QueueItemAction.getNodeState(stepStart));

        SemaphoreStep.success("wait-b/1", null);
        // Sleep to make sure we get the b branch end node...
        Thread.sleep(1000);
        // Now get the end nodes as of the end of the b branch...
        branchEndNodes = Arrays.asList(getNodes(execution, new int[]{9, 15}));

        statuses = StatusAndTiming.computeBranchStatuses2(b1, execution.getNode("5"), branchStartNodes, branchEndNodes, null);

        assertNotNull(statuses);
        assertEquals(GenericStatus.QUEUED, statuses.get("a"));
        assertEquals(GenericStatus.SUCCESS, statuses.get("b"));
        assertEquals(GenericStatus.QUEUED, StatusAndTiming.condenseStatus(statuses.values()));

        j.createSlave("second-agent", "second", null);
        SemaphoreStep.waitForStart("wait-a/1", b1);


        // Now get the end nodes as of the entry of the semaphore on the a branch...
        branchEndNodes = Arrays.asList(getNodes(execution, new int[]{18, 15}));

        statuses = StatusAndTiming.computeBranchStatuses2(b1, execution.getNode("5"), branchStartNodes, branchEndNodes, null);

        assertNotNull(statuses);
        assertEquals(GenericStatus.IN_PROGRESS, statuses.get("a"));
        assertEquals(GenericStatus.SUCCESS, statuses.get("b"));
        assertEquals(GenericStatus.IN_PROGRESS, StatusAndTiming.condenseStatus(statuses.values()));

        SemaphoreStep.success("wait-a/1", null);
        } finally {
            j.assertBuildStatusSuccess(j.waitForCompletion(b1));
        }
    }

    @Test
    @Issue("JENKINS-47219")
    public void parallelStagesOneSkipped() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "parallel stages, one skipped job");
        job.setDefinition(new CpsFlowDefinition("" +
                "pipeline { \n" +
                "   agent any \n" +
                "   stages { \n" +
                "       stage('Run Tests') { \n"+
                "           parallel { \n" +
                "               stage('Test on Windows') { \n" +
                "                   when { \n" +
                "                       branch 'cake' \n" +
                "                   } \n"+
                "                   steps { \n"+
                "                       echo 'hello world' \n"+
                "                   } \n"+
                "               } \n"+
                "               stage('Test on Linux') { \n" +
                "                   steps { \n"+
                "                       echo 'hello world' \n"+
                "                   } \n"+
                "               } \n"+
                "           } \n"+
                "       } \n"+
                "   } \n"+
                "} \n",
                true));

        WorkflowRun build = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(build);
        assertEquals(3, visitor.chunks.size());
        visitor.assertStageOrBranchStatus("Run Tests", GenericStatus.SUCCESS);
        visitor.assertStageOrBranchStatus("Test on Windows", GenericStatus.NOT_EXECUTED);
        visitor.assertStageOrBranchStatus("Test on Linux", GenericStatus.SUCCESS);
    }

    @Test
    public void catchOutsideFailingStage() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "catchOutsideFailingStage");
        job.setDefinition(new CpsFlowDefinition(
                "try {\n" +
                "  stage('throws-error') {\n" +
                "    error('oops')\n" +
                "  }\n" +
                "} catch(err) {\n" +
                "  echo('caught error')\n" +
                "}\n", true));
        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(run);
        assertEquals(1, visitor.chunks.size());
        visitor.assertStageOrBranchStatus("throws-error", GenericStatus.FAILURE);
    }

    @Test
    @Issue("JENKINS-43292")
    public void parallelFailFast() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "parallelFailFast");
        job.setDefinition(new CpsFlowDefinition(
                "parallel failFast: true,\n" +
                "  aborts: {\n" +
                "    sleep 5\n" +
                "  },\n" +
                "  fails: {\n" +
                "    sleep 1\n" +
                "    error('oops')\n" +
                "  },\n" +
                "  succeeds: {\n" +
                "    echo 'success'" +
                "  }", true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(run);
        assertEquals(3, visitor.chunks.size());
        visitor.assertStageOrBranchStatus("aborts", GenericStatus.ABORTED);
        visitor.assertStageOrBranchStatus("fails", GenericStatus.FAILURE);
        visitor.assertStageOrBranchStatus("succeeds", GenericStatus.SUCCESS);
    }

    @Test
    @Issue("JENKINS-43292")
    public void parallel() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "parallel");
        job.setDefinition(new CpsFlowDefinition(
                "parallel fails: {\n" +
                "  error('oops')\n" +
                "},\n" +
                "succeeds: {\n" +
                "  echo('succeeds')" +
                "}\n", true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(run);
        assertEquals(2, visitor.chunks.size());
        visitor.assertStageOrBranchStatus("succeeds", GenericStatus.SUCCESS);
        visitor.assertStageOrBranchStatus("fails", GenericStatus.FAILURE);
    }

    @Test
    @Issue("JENKINS-39203")
    public void unstableWithWarningAction() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "unstable");
        job.setDefinition(new CpsFlowDefinition(
                "stage('unstable') {\n" +
                "  echo('foo')\n" +
                "  unstable('oops')\n" +
                "  echo('foo')\n" +
                "}\n" +
                "stage('success') {\n" +
                "  echo('no problem')" +
                "}\n" +
                "stage('failure') {\n" +
                "  unstable('second oops')\n" +
                "  error('failure')\n" +
                "}\n", true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(run);
        assertEquals(3, visitor.chunks.size());
        visitor.assertStageOrBranchStatus("unstable", GenericStatus.UNSTABLE);
        visitor.assertStageOrBranchStatus("success", GenericStatus.SUCCESS);
        visitor.assertStageOrBranchStatus("failure", GenericStatus.FAILURE);
    }

    @Test
    @Issue("JENKINS-39203")
    public void unstableInBlockScopeStep() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "unstableInBlockScopeStep");
        job.setDefinition(new CpsFlowDefinition(
                "stage('unstable') {\n" +
                "  timeout(1) {\n" +
                "    timeout(1) {\n" +
                "      unstable('oops')\n" +
                "    }\n" +
                "  }\n" +
                "}\n", true));
        WorkflowRun run = j.assertBuildStatus(Result.UNSTABLE, job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(run);
        assertEquals(1, visitor.chunks.size());
        visitor.assertStageOrBranchStatus("unstable", GenericStatus.UNSTABLE);
    }

    @Test
    @Issue("JENKINS-45579")
    public void catchErrorWithStageResult() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "catchErrorWithStageResult");
        job.setDefinition(new CpsFlowDefinition(
                "stage('failure') {\n" +
                "  catchError(stageResult: 'FAILURE') {\n" +
                "    error('oops')\n" +
                "  }\n" +
                "  unstable('failure takes priority')\n" +
                "}\n" +
                "stage('success') {\n" +
                "  echo('foo')" +
                "}\n", true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(run);
        assertEquals(2, visitor.chunks.size());
        visitor.assertStageOrBranchStatus("failure", GenericStatus.FAILURE);
        visitor.assertStageOrBranchStatus("success", GenericStatus.SUCCESS);
    }

    @Test
    @Ignore
    public void nestedStageParentStatus() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "unstable");
        job.setDefinition(new CpsFlowDefinition(
                "stage('parent') {\n" +
                "  stage('child') {\n" +
                "    error('oops')\n" +
                "  }\n" +
                "}\n", true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        StagesAndParallelBranchesVisitor visitor = new StagesAndParallelBranchesVisitor(run);
        assertEquals(2, visitor.chunks.size());
        // Fails here. The actual status is SUCCESS because the status is computed between the start of
        // parent and child. See note in StagesAndParallelBranchesVisitor.assertStageOrBranchStatus.
        visitor.assertStageOrBranchStatus("parent", GenericStatus.FAILURE);
        visitor.assertStageOrBranchStatus("child", GenericStatus.FAILURE);
    }

    /**
     * Visitor that collects stages and parallel branches into chunks in a way similar to what
     * Blue Ocean does.
     *
     * This visitor should only be used for pipelines which have finished executing and for
     * which all stage and parallel branch names are unique.
     */
    private static class StagesAndParallelBranchesVisitor extends StandardChunkVisitor {
        private final Map<String, MemoryFlowChunk> chunks = new HashMap<>();
        private final WorkflowRun run;

        public StagesAndParallelBranchesVisitor(WorkflowRun run) {
            if (run.isBuilding()) {
                // TODO: This visitor could be made to work for in progress builds with some changes
                // to how parallel branches are handled.
                Assert.fail("Cannot find chunks for a run that is still in progress");
            }
            this.run = run;
            ForkScanner.visitSimpleChunks(run.getExecution().getCurrentHeads(), this, new StageChunkFinder());
        }

        /**
         * Assert that the computed status for a stage or branch matches the expected status.
         */
        public void assertStageOrBranchStatus(String stageOrBranchName, GenericStatus expected) {
            MemoryFlowChunk chunk = chunks.get(stageOrBranchName);
            assertThat(chunk.getNodeBefore(), instanceOf(BlockStartNode.class));
            assertThat(chunk.getFirstNode(), instanceOf(BlockStartNode.class));
            assertThat("The parent of FirstNode should be NodeBefore",
                    chunk.getFirstNode().getParents(), equalTo(Collections.singletonList(chunk.getNodeBefore())));
            if (chunk.getLastNode() instanceof BlockEndNode) {
                assertThat(chunk.getLastNode(), instanceOf(BlockEndNode.class));
                assertThat(chunk.getNodeAfter(), instanceOf(BlockEndNode.class));
                assertThat("A parent of NodeAfter should be LastNode",
                        chunk.getNodeAfter().getParents(), hasItem(chunk.getLastNode()));
                assertThat("NodeBefore and NodeAfter should be the start and end node for the same block",
                        ((BlockStartNode)chunk.getNodeBefore()).getEndNode(), equalTo(chunk.getNodeAfter()));
                assertThat("FirstNode and LastNode should be the start and end node for the same block",
                        ((BlockStartNode)chunk.getFirstNode()).getEndNode(), equalTo(chunk.getLastNode()));
            } else {
                // Note: Nested stages are not handled correctly by StandardChunkVisitor (see StandardChunkVisitor
                // Javadoc). handleChunkDone ends up being called with nodeBefore and firstNode for the parent
                // stage as expected, but lastNode and nodeAfter are the start of the first nested child stage
                // rather than the end nodes for the parent stage.
            }
            assertThat(StatusAndTiming.computeChunkStatus2(run, chunk), equalTo(expected));
        }

        @Override
        public void handleChunkDone(MemoryFlowChunk chunk) {
            LabelAction action = chunk.getFirstNode().getPersistentAction(LabelAction.class);
            assertThat("Chunk does not appear to be a Stage", action, notNullValue());
            chunks.put(action.getDisplayName(), chunk);
            this.chunk = new MemoryFlowChunk();
        }

        @Override
        public void parallelBranchEnd(FlowNode parallelStart, FlowNode branchEnd, ForkScanner scanner) {
            assertThat(branchEnd, instanceOf(BlockEndNode.class));
            FlowNode branchStart = ((BlockEndNode)branchEnd).getStartNode();
            assertThat(parallelStart, instanceOf(BlockStartNode.class));
            FlowNode parallelEnd = ((BlockStartNode)parallelStart).getEndNode();
            ThreadNameAction action = branchStart.getPersistentAction(ThreadNameAction.class);
            assertThat("Cannot determine name of parallel branch", action, notNullValue());
            chunks.put(action.getThreadName(), new MemoryFlowChunk(parallelStart, branchStart, branchEnd, parallelEnd));
        }
    }
}
