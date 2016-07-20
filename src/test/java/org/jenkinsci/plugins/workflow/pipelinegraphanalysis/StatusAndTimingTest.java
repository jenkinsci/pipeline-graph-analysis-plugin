package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import com.cloudbees.workflow.flownode.FlowNodeUtil;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

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

    FlowNode[] getNodes(FlowExecution exec, int[] ids) throws IOException {
        FlowNode[] output = new FlowNode[ids.length];
        for (int i=0; i < ids.length; i++) {
            output[i] = exec.getNode(Integer.toString(ids[i]));
        }
        return output;
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
        StatusAndTiming.GenericStatus status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[1], n[2]);
        StatusAndTiming.TimingInfo timing = StatusAndTiming.computeChunkTiming(run, 0, null, n[0], n[1], n[2]);
        Assert.assertEquals(StatusAndTiming.GenericStatus.SUCCESS, status);
        Assert.assertEquals(0, timing.getPauseDurationMillis());
        Assert.assertEquals(TimingAction.getStartTime(n[2]) - run.getStartTimeInMillis(), timing.getTotalDurationMillis());

        // Everything but start/end
        status = StatusAndTiming.computeChunkStatus(run, n[0], n[1], n[4], n[5]);
        timing = StatusAndTiming.computeChunkTiming(run, 2, n[0], n[1], n[4], n[5]);
        Assert.assertEquals(StatusAndTiming.GenericStatus.SUCCESS, status);
        Assert.assertEquals(timing.getPauseDurationMillis(), 2);
        Assert.assertEquals(TimingAction.getStartTime(n[5]) - TimingAction.getStartTime(n[1]), timing.getTotalDurationMillis());

        // Whole flow
        status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[5], null);
        timing = StatusAndTiming.computeChunkTiming(run, 0, null, n[0], n[5], null);
        Assert.assertEquals(StatusAndTiming.GenericStatus.SUCCESS, status);
        Assert.assertEquals(0, timing.getPauseDurationMillis());
        Assert.assertEquals(run.getDuration(), timing.getTotalDurationMillis());

        // Custom unstable status
        run.setResult(Result.UNSTABLE);
        status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[1], n[2]);
        Assert.assertEquals(StatusAndTiming.GenericStatus.UNSTABLE, status);

        // Failure should assume last chunk ran is where failure happened
        run.setResult(Result.FAILURE);
        status = StatusAndTiming.computeChunkStatus(run, null, n[0], n[1], n[2]);
        Assert.assertEquals(StatusAndTiming.GenericStatus.SUCCESS, status);

        // First non-start node to final end node
        status = StatusAndTiming.computeChunkStatus(run, n[0], n[1], n[5], null);
        Assert.assertEquals(StatusAndTiming.GenericStatus.FAILED, status);

        // Whole flow except for end... since no errors here, failure must be at end!
        status = StatusAndTiming.computeChunkStatus(run, n[0], n[1], n[4], n[5]);
        Assert.assertEquals(StatusAndTiming.GenericStatus.SUCCESS, status);
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
        QueueTaskFuture<WorkflowRun> build = job.scheduleBuild2(0);
        j.assertBuildStatus(Result.FAILURE, build.get());
        WorkflowRun run = build.get();
        long startTime = run.getStartTimeInMillis();
        FlowExecution exec = run.getExecution();
        printNodes(exec, startTime, true, true);
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

    @Test
    public void inputTest() throws Exception {
        String resource = readResource(pipelinePrefix + "pauseforinput.groovy");
        System.out.println(resource);
    }*/

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
