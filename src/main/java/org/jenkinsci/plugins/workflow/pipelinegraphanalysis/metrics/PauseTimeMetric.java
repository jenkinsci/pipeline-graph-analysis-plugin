package org.jenkinsci.plugins.workflow.pipelinegraphanalysis.metrics;

import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowNodeVisitor;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;

/**
 * Collects total pause time
 * @author Sam Van Oort
 */
public class PauseTimeMetric {
    static class PauseVisitor implements FlowNodeVisitor {
        int pauseTime = 0;

        @Override
        public boolean visit(FlowNode f) {
            pauseTime += PauseAction.getPauseDuration(f);
            ErrorAction err = f.getError();
            return true;
        }
    }

    public int collectValue(WorkflowRun run) {
        ForkScanner scan = new ForkScanner();
        scan.setup(run.getExecution().getCurrentHeads());
        PauseVisitor visitor = new PauseVisitor();
        scan.visitAll(run.getExecution().getCurrentHeads(), visitor);
        return visitor.pauseTime;
    }
}
