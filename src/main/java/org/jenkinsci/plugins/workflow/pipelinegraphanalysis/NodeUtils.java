package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import hudson.model.Action;
import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.StageAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * @author cliffmeyers
 */
public class NodeUtils {

    public static boolean isStage(FlowNode node) {
        return node !=null && ((node.getAction(StageAction.class) != null && !isSyntheticStage(node))
                || (node.getAction(LabelAction.class) != null && node.getAction(ThreadNameAction.class) == null));

    }

    public static boolean isSyntheticStage(@Nullable FlowNode node){
        return node!= null && getSyntheticStage(node) != null;
    }

    @CheckForNull
    public static TagsAction getSyntheticStage(@Nullable FlowNode node){
        if(node != null) {
            for (Action action : node.getActions()) {
                if (action instanceof TagsAction && ((TagsAction) action).getTagValue(SyntheticStage.TAG_NAME) != null) {
                    return (TagsAction) action;
                }
            }
        }
        return null;
    }
}
