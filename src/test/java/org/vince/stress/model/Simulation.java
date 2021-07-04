package org.vince.stress.model;

import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class Simulation {

    private static final Logger log = Logger.getLogger(Simulation.class);


    public Host host;
    public List<Instant> instants;
    public List<Pod> pods;
    public String name;

    public int getSumOfMax() {
        return pods.stream().map(Pod::getMax).reduce(0, Integer::sum);
    }

    public int getBiggestInstant() {
        List<Integer> list = instants.stream().map(Instant::getSum).sorted().collect(Collectors.toList());
        return list.get(list.size() - 1);
    }

    public void useRequestAsPercentile(int p, float requestFactor, Float limitFactor) {
        pods.forEach(pod -> {
            pod.request = (int) (pod.getPercentile(p) * requestFactor);
            if(limitFactor != null) {
                pod.limit = (int) (pod.request * limitFactor);
            }
        });
    }

    public int getSumOfRequests() {
        return pods.stream().map(pod -> pod.request).reduce(0, Integer::sum);
    }

    public void calculateCpu(int totalMillicoresHost) {
        instants.forEach(instant -> instant.calculateCpu(totalMillicoresHost));
    }

    public List<Instant> getMaxedOutInstants() {
        return instants.stream().filter(instant -> instant.getEfficiencyAsPercent() == 100).collect(Collectors.toList());
    }

    public int getAvgEfficiencyPercent() {
        return (int) (instants.stream().map(instant -> instant.efficiency).reduce(0.0, Double::sum) / instants.size() * 100);
    }

    public Double getAvgCompletion(boolean logLowPerf) {
        return instants.stream().map(instant -> instant.getAvgPodsCompletion(logLowPerf)).reduce(0.0, Double::sum) / instants.size();
    }

    public long getNumberOfThrottledPods() {
        return instants.stream().map(Instant::getNumberOfThrottledPods).reduce(0L, Long::sum);
    }

    public long getTotalNumberOfValues() {
        return ((long) instants.size()) * pods.size();
    }

}
