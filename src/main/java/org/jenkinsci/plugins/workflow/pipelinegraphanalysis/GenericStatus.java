package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import hudson.model.Result;

/**
 * Statuses of a {@link org.jenkinsci.plugins.workflow.graphanalysis.FlowChunk} in increasing priority order
 */
public enum GenericStatus {
    /**
     * Can't be determined for whatever reason, possibly in an undefined or transitory state
     */
    UNKNOWN,

    /**
     * We resumed from checkpoint or {@link Result#NOT_BUILT} status - nothing ran in the chunk.
     */
    NOT_EXECUTED,

    /**
     * Completed & successful, ex {@link Result#SUCCESS}
     */
    SUCCESS,

    /**
     * Completed with recoverable failures, such as noncritical tests, ex {@link Result#UNSTABLE}
     */
    UNSTABLE,

    /**
     * Not complete: still executing, waiting for a result
     */
    IN_PROGRESS,

    /**
     * Completed and explicitly failed, i.e. {@link Result#FAILURE}
     */
    FAILURE,

    /**
     * Aborted while running, no way to determine final outcome {@link Result#ABORTED}
     */
    ABORTED,

    /**
     * Not complete: we are waiting for user input to continue (special case of IN_PROGRESS)
     */
    PAUSED_PENDING_INPUT
}
