package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests the stage collection
 * @author <samvanoort@gmail.com>Sam Van Oort</samvanoort@gmail.com>
 */
public class StageTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testBlockStage() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "Blocky job");

        job.setDefinition(new CpsFlowDefinition("" +
                "node {" +
                "   stage ('Build') { " +
                "     echo ('Building'); " +
                "   } \n"+
                "   stage ('Test') { " +
                "     echo ('Testing'); " +
                "   } \n"+
                "   stage ('Deploy') { " +
                (hudson.Functions.isWindows() ? "   bat ('rmdir /s/q targs || echo no such dir\\n mkdir targs && echo hello> targs\\\\hello.txt'); "
                        : "   sh ('rm -rf targs && mkdir targs && echo hello > targs/hello.txt'); "
                ) +
                "     archive(includes: 'targs/hello.txt'); " +
                "     echo ('Deploying'); " +
                "   } \n"+
                "}"));

        WorkflowRun build = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

    }

    @Test
    public void testLegacyStage() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "Blocky job");

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

        WorkflowRun build = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

    }

}
