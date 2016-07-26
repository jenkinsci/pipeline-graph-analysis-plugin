package org.jenkinsci.plugins.workflow.pipelinegraphanalysis;

public class TimingInfo {
    private long totalDurationMillis;
    private long pauseDurationMillis;

    public TimingInfo() {
        this.totalDurationMillis = 0;
        this.pauseDurationMillis = 0;
    }

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

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof TimingInfo)) {
            return false;
        } else if (this == o) {
            return true;
        }
        TimingInfo ti = (TimingInfo) o;
        return this.pauseDurationMillis == ti.pauseDurationMillis && this.totalDurationMillis == ti.totalDurationMillis;
    }

    @Override
    public int hashCode() {
        long mixed = (~pauseDurationMillis) ^ totalDurationMillis;  // Bitwise invert because both are often equal
        return (int) (mixed ^ (mixed >>> 32));
    }
}
