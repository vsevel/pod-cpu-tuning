package org.vince.stress.model;

import java.util.List;
import java.util.stream.Collectors;

public class Pod {
    public int id;
    public List<Value> values;
    private List<Integer> orderedValues;

    public int request;
    public Integer limit;

    public Pod(int id) {
        this.id = id;
    }

    public void init() {
        orderedValues = values.stream().map(v -> v.idealMillicores).sorted().collect(Collectors.toList());
    }

    public int getMax() {
        return orderedValues.get(orderedValues.size() - 1);
    }

    public int getPercentile(int p) {
        return orderedValues.get((int) (p / 100.0 * orderedValues.size()));
    }

    @Override
    public String toString() {
        return "Pod{" +
                "id=" + id +
                '}';
    }
}
