package org.jenkinsci.plugins.workflow.pipelinegraphanalysis.metrics;

import java.util.HashMap;

/**
 * Holds metrics information for a run
 */
public class MetricsStore <MetricType> {
    private HashMap<String, MetricType> store = new HashMap<String, MetricType>();

    public void setValue(String key, MetricType metric) {
        store.put(key, metric);
    }

    public MetricType getValue(String key) {
        return store.get(key);
    }

    public void clear() {
        store.clear();
    }

    public MetricsStore<MetricType> copy() {
        MetricsStore<MetricType> output = new MetricsStore<MetricType>();
        output.store = (HashMap<String, MetricType>)store.clone();
        return output;
    }
}
