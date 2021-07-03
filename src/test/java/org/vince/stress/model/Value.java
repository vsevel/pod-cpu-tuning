package org.vince.stress.model;

public class Value {

    public final int idealMillicores;
    public final Pod pod;
    public Instant instant;
    public int realMillicores;

    public Value(Pod pod, int idealMillicores) {
        this.pod = pod;
        this.idealMillicores = idealMillicores;
    }

    public double getCompletion() {
        return realMillicores * 1.0 / idealMillicores;
    }

    public boolean isThrottled() {
        return realMillicores < idealMillicores;
    }

    public double getThrottledPercent() {
        return realMillicores * 1.0 / idealMillicores;
    }

    public boolean isSatisfied() {
        return realMillicores == idealMillicores || pod.limit != null && realMillicores == pod.limit;
    }

    @Override
    public String toString() {
        return "P" + pod.id + "[R:" + pod.request + ",L:" + pod.limit + "]" + "@T" + instant.id + ":A=" + realMillicores + "/W=" + idealMillicores + "(" + (int) (getCompletion() * 100) + "%)";
    }
}
