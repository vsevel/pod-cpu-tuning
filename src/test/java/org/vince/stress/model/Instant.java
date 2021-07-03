package org.vince.stress.model;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;

public class Instant {

    private static final Logger log = Logger.getLogger(Instant.class);

    public int id;
    public List<Value> values;
    public double efficiency;

    public Instant(int id) {
        this.id = id;
    }

    public int getSum() {
        return values.stream().map(v -> v.idealMillicores).reduce(0, Integer::sum);
    }

    public void calculateCpu(int totalMillicoresHost) {

        int leftMillicores = totalMillicoresHost;

        List<Value> weightedValues = new ArrayList<>();
        values.forEach(value -> IntStream.range(0, value.pod.request).forEach(i -> weightedValues.add(value)));
        Collections.shuffle(weightedValues);

        while (!(isAllPodsSatisfied() || leftMillicores == 0)) {
            for (Value weightedValue : weightedValues) {
                if (!weightedValue.isSatisfied() && leftMillicores > 0) {
                    weightedValue.realMillicores++;
                    leftMillicores--;
                }
            }
        }

        efficiency = (totalMillicoresHost - leftMillicores) * 1.0 / totalMillicoresHost;
    }

    public int getEfficiencyAsPercent() {
        return (int) (efficiency * 100);
    }

    public Double getAvgPodsCompletion(boolean logLowPerf) {
        List<Double> list = values.stream().map(Value::getCompletion).collect(Collectors.toList());
        double result = list.stream().reduce(0.0, Double::sum) / list.size();
        List<Value> values = this.values.stream().filter(Value::isThrottled).collect(Collectors.toList());
        if (logLowPerf && result < 0.90 && !values.isEmpty()) {
            String s = values.stream().map(Value::toString).collect(joining(" "));
            log.info(this + " completion=" + (int) (result * 100) + "%: " + s);
        }
        return result;
    }

    public long getNumberOfThrottledPods() {
        return values.stream().filter(Value::isThrottled).count();
    }

    public boolean isAllPodsSatisfied() {
        for (Value value : values) {
            if (!value.isSatisfied()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Instant{" +
                "id=" + id +
                '}';
    }
}
