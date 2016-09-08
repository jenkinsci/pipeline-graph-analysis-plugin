package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Simple container for a linear run of nodes
 * This stores nodes in reversed order, so you can add to the end
 * @author Sam Van Oort
 */
public class ContainerFlowChunk extends MemoryFlowChunk implements ContainerChunk {
    ArrayDeque<FlowNode> contents = new ArrayDeque<FlowNode>();

    public void addChildNodeToStart(FlowNode f) {
        contents.add(f);
    }

    @Override
    public void addChildChunkToStart(@Nonnull ContainerChunk chunk) {
        throw new UnsupportedOperationException("Can't add a chunk to a ContainerFlowChunk, use a NestingFlowChunk");
    }

    @Override
    public Iterator<FlowNode> childNodeIterator() {
        return contents.iterator();
    }

    @Override
    public Iterator childChunkIterator() {
        return Iterators.emptyIterator();
    }

    public boolean isBottomLevel() {
        return true;
    }

    public int getChildCount() {
        return contents.size();
    }
}
