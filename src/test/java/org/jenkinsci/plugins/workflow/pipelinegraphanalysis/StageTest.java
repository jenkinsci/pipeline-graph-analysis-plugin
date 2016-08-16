package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import junit.framework.Assert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowChunkWithContext;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk;
import org.jenkinsci.plugins.workflow.graphanalysis.StandardChunkVisitor;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Tests the stage collection
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class StageTest {

    /** Assert that chunk flownode IDs match expected, use 0 or -1 ID for null flownode */
    public static void assertChunkBoundary(FlowChunkWithContext chunk, int beforeId, int firstId, int lastId, int afterId) {
        // First check the chunk boundaries, then the before/after
        Assert.assertNotNull(chunk.getFirstNode());
        Assert.assertEquals(firstId, Integer.parseInt(chunk.getFirstNode().getId()));
        Assert.assertNotNull(chunk.getLastNode());
        Assert.assertEquals(lastId, Integer.parseInt(chunk.getLastNode().getId()));

        if (beforeId > 0) {
            Assert.assertNotNull(chunk.getNodeBefore());
            Assert.assertEquals(beforeId, Integer.parseInt(chunk.getNodeBefore().getId()));
        } else {
            Assert.assertNull(chunk.getNodeBefore());
        }

        if (afterId > 0) {
            Assert.assertNotNull(chunk.getNodeAfter());
            Assert.assertEquals(afterId, Integer.parseInt(chunk.getNodeAfter().getId()));
        } else {
            Assert.assertNull(chunk.getNodeAfter());
        }

    }

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    public static class CollectingChunkVisitor extends StandardChunkVisitor {
        ArrayDeque<MemoryFlowChunk> allChunks = new ArrayDeque<MemoryFlowChunk>();

        public ArrayList<MemoryFlowChunk> getChunks() {
            return new ArrayList<MemoryFlowChunk>(allChunks);
        }

        protected void handleChunkDone(@Nonnull MemoryFlowChunk chunk) {
            allChunks.push(chunk);
            this.chunk = new MemoryFlowChunk();
        }
    }

    @Test
    public void testBlockStage() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "Blocky job");

        job.setDefinition(new CpsFlowDefinition("" +
                "node {" +
                "   stage ('Build') { " +
                "     echo ('Building'); " +
                "   } \n" +
                "   stage ('Test') { " +
                "     echo ('Testing'); " +
                "   } \n" +
                "   stage ('Deploy') { " +
                (hudson.Functions.isWindows() ? "   bat ('rmdir /s/q targs || echo no such dir\\n mkdir targs && echo hello> targs\\\\hello.txt'); "
                        : "   sh ('rm -rf targs && mkdir targs && echo hello > targs/hello.txt'); "
                ) +
                "     archive(includes: 'targs/hello.txt'); " +
                "     echo ('Deploying'); " +
                "   } \n" +
                "}"));
        /**
         * Node dump follows, format:
         [ID]{parent,ids} flowNodeClassName stepDisplayName [st=startId if a block node]
         Action format:
         - actionClassName actionDisplayName
         ------------------------------------------------------------------------------------------
         [2]{}FlowStartNode Start of Pipeline
         [3]{2}StepStartNode Allocate node : Start
           -LogActionImpl Console Output
           -WorkspaceActionImpl Workspace
         [4]{3}StepStartNode Allocate node : Body : Start
           -BodyInvocationAction null
         [5]{4}StepStartNode Stage : Start
         [6]{5}StepStartNode Build
           -BodyInvocationAction null
           -LabelAction Build
         [7]{6}StepAtomNode Print Message
           -LogActionImpl Console Output
         [8]{7}StepEndNode Stage : Body : End  [st=6]
           -BodyInvocationAction null
         [9]{8}StepEndNode Stage : End  [st=5]
         [10]{9}StepStartNode Stage : Start
         [11]{10}StepStartNode Test
           -BodyInvocationAction null
           -LabelAction Test
         [12]{11}StepAtomNode Print Message
           -LogActionImpl Console Output
         [13]{12}StepEndNode Stage : Body : End  [st=11]
           -BodyInvocationAction null
         [14]{13}StepEndNode Stage : End  [st=10]
         [15]{14}StepStartNode Stage : Start
         [16]{15}StepStartNode Deploy
           -BodyInvocationAction null
           -LabelAction Deploy
         [17]{16}StepAtomNode Shell Script
           -LogActionImpl Console Output
         [18]{17}StepAtomNode Archive artifacts
           -LogActionImpl Console Output
         [19]{18}StepAtomNode Print Message
           -LogActionImpl Console Output
         [20]{19}StepEndNode Stage : Body : End  [st=16]
           -BodyInvocationAction null
         [21]{20}StepEndNode Stage : End  [st=15]
         [22]{21}StepEndNode Allocate node : Body : End  [st=4]
           -BodyInvocationAction null
         [23]{22}StepEndNode Allocate node : End  [st=3]
         [24]{23}FlowEndNode End of Pipeline  [st=2]
         */

        WorkflowRun build = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

        ForkScanner scan = new ForkScanner();
        scan.setup(build.getExecution().getCurrentHeads());
        CollectingChunkVisitor visitor = new CollectingChunkVisitor();
        scan.visitSimpleChunks(visitor, new StageChunkFinder());

        ArrayList<MemoryFlowChunk> stages = visitor.getChunks();
        Assert.assertEquals(3, stages.size());
        assertChunkBoundary(stages.get(0), 5, 6, 8, 9);
        assertChunkBoundary(stages.get(1), 10, 11, 13, 14);
        assertChunkBoundary(stages.get(2), 15, 16, 20, 21);
    }

    /** Should find dangling mixes of stages */
    @Test
    public void mixedStageScoping() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "Nasty edge-case finding job");
        job.setDefinition(new CpsFlowDefinition("" +
                "echo 'stuff'\n" +
                "stage 'first'\n" +
                "echo 'ran first'\n" +
                "stage ('second') {\n" +
                "    echo 'ran second'\n" +
                "}\n" +
                "echo 'orphan step'\n" +
                "stage 'third'\n" +
                "echo 'ran third'\n" +
                "stage ('fourth') {\n" +
                "    echo 'ran fourth'\n" +
                "}\n" +
                "echo 'another orphan step'"
        ));
        WorkflowRun build = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

        /**
         * Node dump follows, format:
         [ID]{parent,ids}(millisSinceStartOfRun) flowNodeClassName stepDisplayName [st=startId if a block node]
         Action format:
         - actionClassName actionDisplayName
         ------------------------------------------------------------------------------------------
         [2]{}FlowStartNode Start of Pipeline
         [3]{2}StepAtomNode Print Message
           -LogActionImpl Console Output
         [4]{3}StepAtomNode first
           -LogActionImpl Console Output
           -LabelAction first
           -StageActionImpl null
         [5]{4}StepAtomNode Print Message
           -LogActionImpl Console Output
         [6]{5}StepStartNode Stage : Start
         [7]{6}StepStartNode second
           -BodyInvocationAction null
           -LabelAction second
         [8]{7}StepAtomNode Print Message
           -LogActionImpl Console Output
         [9]{8}StepEndNode Stage : Body : End  [st=7]
           -BodyInvocationAction null
         [10]{9}StepEndNode Stage : End  [st=6]
         [11]{10}StepAtomNode Print Message
           -LogActionImpl Console Output
         [12]{11}StepAtomNode third
           -LogActionImpl Console Output
           -LabelAction third
           -StageActionImpl null
         [13]{12}StepAtomNode Print Message
           -LogActionImpl Console Output
         [14]{13}StepStartNode Stage : Start
         [15]{14}StepStartNode fourth
           -BodyInvocationAction null
           -LabelAction fourth
         [16]{15}StepAtomNode Print Message
           -LogActionImpl Console Output
         [17]{16}StepEndNode Stage : Body : End  [st=15]
           -BodyInvocationAction null
         [18]{17}StepEndNode Stage : End  [st=14]
         [19]{18}StepAtomNode Print Message
           -LogActionImpl Console Output
         [20]{19}FlowEndNode End of Pipeline  [st=2]
         */

        ForkScanner scan = new ForkScanner();
        scan.setup(build.getExecution().getCurrentHeads());
        CollectingChunkVisitor visitor = new CollectingChunkVisitor();
        scan.visitSimpleChunks(visitor, new StageChunkFinder());
        ArrayList<MemoryFlowChunk> stages = visitor.getChunks();

        Assert.assertEquals(4, stages.size());
        assertChunkBoundary(stages.get(0), 3, 4, 6, 7);
        assertChunkBoundary(stages.get(1), 6, 7, 9, 10);
        assertChunkBoundary(stages.get(2), 11, 12, 14, 15);
        assertChunkBoundary(stages.get(3), 14, 15, 17, 18);
    }

    /** Single-step stage markers */
    @Test
    public void testLegacyStage() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "Legacy stage job");

        job.setDefinition(new CpsFlowDefinition("" +
                "node {" +
                "   stage ('Build'); " +
                "   echo ('Building'); " +
                "   stage ('Test'); " +
                "   echo ('Testing'); " +
                "   stage ('Deploy'); " +
                (hudson.Functions.isWindows() ? "   bat ('rmdir /s/q targs || echo no such dir\\n mkdir targs && echo hello> targs\\\\hello.txt'); "
                        : "   sh ('rm -rf targs && mkdir targs && echo hello > targs/hello.txt'); "
                ) +
                "   archive(includes: 'targs/hello.txt'); " +
                "   echo ('Deploying'); " +
                "}"));
        /**
         * Node dump follows, format:
         [ID]{parent,ids} flowNodeClassName stepDisplayName [st=startId if a block node]
         Action format:
         - actionClassName actionDisplayName
         ------------------------------------------------------------------------------------------
         [2]{}FlowStartNode Start of Pipeline
         [3]{2}StepStartNode Allocate node : Start
           -LogActionImpl Console Output
           -WorkspaceActionImpl Workspace
         [4]{3}StepStartNode Allocate node : Body : Start
           -BodyInvocationAction null
         [5]{4}StepAtomNode Build
           -LogActionImpl Console Output
           -LabelAction Build
           -StageActionImpl null
         [6]{5}StepAtomNode Print Message
           -LogActionImpl Console Output
         [7]{6}StepAtomNode Test
           -LogActionImpl Console Output
           -LabelAction Test
           -StageActionImpl null
         [8]{7}StepAtomNode Print Message
           -LogActionImpl Console Output
         [9]{8}StepAtomNode Deploy
           -LogActionImpl Console Output
           -LabelAction Deploy
           -StageActionImpl null
         [10]{9}StepAtomNode Shell Script
           -LogActionImpl Console Output
         [11]{10}StepAtomNode Archive artifacts
           -LogActionImpl Console Output
         [12]{11}StepAtomNode Print Message
           -LogActionImpl Console Output
         [13]{12}StepEndNode Allocate node : Body : End  [st=4]
           -BodyInvocationAction null
         [14]{13}StepEndNode Allocate node : End  [st=3]
         [15]{14}FlowEndNode End of Pipeline  [st=2]
         */

        WorkflowRun build = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

        ForkScanner scan = new ForkScanner();
        scan.setup(build.getExecution().getCurrentHeads());
        CollectingChunkVisitor visitor = new CollectingChunkVisitor();
        scan.visitSimpleChunks(visitor, new StageChunkFinder());
        ArrayList<MemoryFlowChunk> stages = visitor.getChunks();

        Assert.assertEquals(3, stages.size());
        assertChunkBoundary(stages.get(0), 4, 5, 6, 7);
        assertChunkBoundary(stages.get(1), 6, 7, 8, 9);
        assertChunkBoundary(stages.get(2), 8, 9, 15, -1);
    }

}
