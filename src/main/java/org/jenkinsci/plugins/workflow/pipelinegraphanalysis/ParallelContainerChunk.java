package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk;
import org.jenkinsci.plugins.workflow.graphanalysis.ParallelMemoryFlowChunk;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Maps to parallel block
 * @author Sam Van Oort
 */
public class ParallelContainerChunk extends MemoryFlowChunk implements ContainerChunk {
    ArrayDeque<ContainerChunk> branches = new ArrayDeque<ContainerChunk>();

    @Override
    public void addChildNodeToStart(@Nonnull FlowNode f) {
        throw new UnsupportedOperationException("Parallel chunks can only have branches added");
    }

    @Override
    public void addChildChunkToStart(@Nonnull ContainerChunk chunk) {
        branches.addFirst(chunk);
    }

    @Override
    public Iterator<FlowNode> childNodeIterator() {
        List<Iterator> iterators = new ArrayList<Iterator>();
        for (ContainerChunk m : branches) {
            iterators.add(m.childNodeIterator());
        }

        return Iterators.filter(
                Iterators.concat(
                        Iterators.singletonIterator(this.firstNode),
                        Iterators.concat((Iterator)(iterators.iterator())),
                        Iterators.singletonIterator(this.lastNode)),
                Predicates.notNull()
        );
    }

    @Override
    public Iterator<ContainerChunk> childChunkIterator() {
        return branches.iterator();
    }

    @Override
    public boolean isBottomLevel() {
        return false;
    }
}
