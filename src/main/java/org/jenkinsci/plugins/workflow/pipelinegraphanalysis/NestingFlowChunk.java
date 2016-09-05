package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Chunk that allows for fairly complex nesting of chunks
 */
public class NestingFlowChunk extends MemoryFlowChunk implements ContainerChunk {
    ArrayDeque<ContainerChunk> chunks = new ArrayDeque<>();

    public void addChildNodeToStart(@Nonnull FlowNode f) {
        ContainerFlowChunk container;
        if (chunks.isEmpty() || !(chunks.getFirst() instanceof ContainerFlowChunk)) {
            container = new ContainerFlowChunk();
            chunks.push(container);
        } else {
            container = (ContainerFlowChunk)chunks.peekFirst();
        }
        container.addChildNodeToStart(f);
    }

    public void addChildChunkToStart(@Nonnull ContainerChunk chunk) {
        chunks.addFirst(chunk);
    }

    @Override
    public Iterator<FlowNode> childNodeIterator() {
        List<Iterator> iterators = new ArrayList<Iterator>();
        for (ContainerChunk m : chunks) {
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
        return chunks.iterator();
    }

    public Collection<ContainerChunk> getChildren() {
        return chunks;
    }

    public boolean isBottomLevel() {
        return chunks.isEmpty() || (chunks.size() == 1 && chunks.peekFirst() instanceof ContainerFlowChunk);
    }
}
