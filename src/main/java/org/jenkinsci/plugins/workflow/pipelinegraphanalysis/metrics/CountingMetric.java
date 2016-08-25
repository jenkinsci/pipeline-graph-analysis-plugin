package org.jenkinsci.plugins.workflow.pipelinegraphanalysis.metrics;

import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowNodeVisitor;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * @author Sam Van Oort
 */
public class CountingMetric {
    static class CountingVisitor implements FlowNodeVisitor {
        int count = 0;

        @Override
        public boolean visit(FlowNode f) {
            count++;
            return true;
        }
    }

    public int collectValue(WorkflowRun run) {
        ForkScanner scan = new ForkScanner();
        scan.setup(run.getExecution().getCurrentHeads());
        CountingVisitor visitor = new CountingVisitor();
        scan.visitAll(run.getExecution().getCurrentHeads(), visitor);
        return visitor.count;
    }
}
