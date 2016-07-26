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
     * We resumed from checkpoint or {@link Result#NOT_BUILT} status
     */
    NOT_EXECUTED,

    /**
     * Success, ex {@link Result#SUCCESS}
     */
    SUCCESS,

    /**
     * Recoverable failures, such as noncritical tests, ex {@link Result#UNSTABLE}
     */
    UNSTABLE,

    /**
     * Still executing, waiting for a result
     */
    IN_PROGRESS,

    /**
     * Ran and explicitly failed, i.e. {@link Result#FAILURE}
     */
    FAILURE,

    /**
     * Aborted while running, no way to determine final outcome {@link Result#ABORTED}
     */
    ABORTED,

    /**
     * We are waiting for user input to continue (special case IN_PROGRESS
     */
    PAUSED_PENDING_INPUT
}
