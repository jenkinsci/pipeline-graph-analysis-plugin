/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

import hudson.model.Result;

/**
 * Statuses of a {@link org.jenkinsci.plugins.workflow.graphanalysis.FlowChunk} in increasing priority order
 */
public enum GenericStatus {
    /**
     * We resumed from checkpoint or {@link Result#NOT_BUILT} status - nothing ran in the chunk.
     */
    NOT_EXECUTED,

    /**
     * Completed &amp; successful, ex {@link Result#SUCCESS}
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
