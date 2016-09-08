package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.SimpleChunkVisitor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayDeque;

/** Used with analytics together with combiner methods for chunks? */
@SuppressFBWarnings
public abstract class ScopeTrackingVisitor<ChunkType extends ContainerChunk> implements SimpleChunkVisitor {
    /** Hierarchal levels of enclosing blocks */
    ArrayDeque<ChunkType> levels = new ArrayDeque<ChunkType>();

    // TODO extend me and store the chunk data in whatever our container structure is

    /** Extend me to do some post-analysis once a chunk is done */
    public void finishChunk(ChunkType t) {
        // Analysis
    }

    protected abstract ChunkType createBasicContainer();

    protected abstract ChunkType createParallelContainer();

    @Override
    public void chunkStart(FlowNode startNode, FlowNode beforeBlock, ForkScanner scanner) {
        ChunkType chunk;

        if (!levels.isEmpty()) {
            chunk = levels.pop();
            // Set startNode, before?
        } else {
            chunk = createBasicContainer();
        }
        // TODO Verify we don't need to handle incomplete chunks?
        chunk.setFirstNode(startNode);
        chunk.setNodeBefore(beforeBlock);
        finishChunk(chunk);
    }

    @Override
    public void chunkEnd(FlowNode endNode, FlowNode afterChunk, ForkScanner scanner) {
        ChunkType chunk = createBasicContainer();
        if (!levels.isEmpty()) {
            levels.peekFirst().addChildChunkToStart(chunk);
        }
        chunk.setLastNode(endNode);
        chunk.setNodeAfter(afterChunk);
        levels.push(chunk);
    }

    @Override
    public void parallelStart(FlowNode parallelStartNode, FlowNode branchNode, ForkScanner scanner) {
        ChunkType chunk;
        if (!levels.isEmpty()) {
            chunk = levels.pop();
            // Set startNode, before?
        } else {
            return;
        }
        // TODO Verify we don't need to handle incomplete chunks?
        chunk.setFirstNode(parallelStartNode);
        // TODO set the node before?
        finishChunk(chunk);
    }

    @Override
    public void parallelEnd(FlowNode parallelStartNode, FlowNode parallelEndNode, ForkScanner scanner) {
        ChunkType chunk = createParallelContainer();
        if (!levels.isEmpty()) {
            levels.peekFirst().addChildChunkToStart(chunk);
        }
        chunk.setLastNode(parallelEndNode);
//        chunk.setNodeAfter(afterChunk); // Maybe need this, maybe no?
        levels.push(chunk);
    }

    @Override
    public void parallelBranchStart(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchStartNode, @Nonnull ForkScanner scanner) {
        ChunkType chunk;
        if (!levels.isEmpty()) {
            chunk = levels.pop();
            // Set startNode, before?
        } else {
            return;
        }
        // TODO Verify we don't need to handle incomplete chunks?
        chunk.setFirstNode(parallelStartNode);
        // TODO set the node before?
        finishChunk(chunk);
    }

    @Override
    public void parallelBranchEnd(@Nonnull FlowNode parallelStartNode, @Nonnull FlowNode branchEndNode, @Nonnull ForkScanner scanner) {
        ChunkType chunk = createBasicContainer();
        if (!levels.isEmpty()) {
            levels.peekFirst().addChildChunkToStart(chunk);
        }
        chunk.setLastNode(branchEndNode);
        // Note: don't need to set the parallel end node because it is the parallelEnd
        levels.push(chunk);
    }

    @Override
    public void atomNode(@CheckForNull FlowNode before, @Nonnull FlowNode atomNode, @CheckForNull FlowNode after, @Nonnull ForkScanner scan) {
        if (!levels.isEmpty()) {
            levels.peekFirst().addChildNodeToStart(atomNode);
        }
    }
}
