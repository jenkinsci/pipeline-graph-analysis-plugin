package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

/** Container object for timing info about piece of a flow */
public class TimingInfo {
    private long totalDurationMillis;
    private long pauseDurationMillis;

    public TimingInfo(long totalDurationMillis, long pauseDurationMillis) {
        this.totalDurationMillis = totalDurationMillis;
        this.pauseDurationMillis = pauseDurationMillis;
    }

    public long getTotalDurationMillis() {
        return totalDurationMillis;
    }

    public void setTotalDurationMillis(long totalDurationMillis) {
        this.totalDurationMillis = totalDurationMillis;
    }

    public long getPauseDurationMillis() {
        return pauseDurationMillis;
    }

    public void setPauseDurationMillis(long pauseDurationMillis) {
        this.pauseDurationMillis = pauseDurationMillis;
    }
}
