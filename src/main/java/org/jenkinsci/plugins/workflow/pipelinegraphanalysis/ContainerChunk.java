package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import com.google.common.collect.Lists;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowChunkWithContext;
import org.jenkinsci.plugins.workflow.graphanalysis.MemoryFlowChunk;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;

/**
 * Stores FlowNodes or chunks of them inside it
 * @author Sam Van Oort
 */
public interface ContainerChunk <ChunkType extends ContainerChunk> extends FlowChunkWithContext {

    public void addChildNodeToStart(@Nonnull FlowNode f);

    public void addChildChunkToStart(@Nonnull ChunkType chunk);

    /** Return iterator over all nodes within this, recursively */
    public Iterator<FlowNode> childNodeIterator();

    public Iterator<ChunkType> childChunkIterator();

    /** Return true if this does not contain nested chunks */
    public boolean isBottomLevel();

    public void setNodeBefore(FlowNode before);
    public void setNodeAfter(FlowNode after);
    public void setFirstNode(FlowNode first);
    public void setLastNode(FlowNode last);
}
