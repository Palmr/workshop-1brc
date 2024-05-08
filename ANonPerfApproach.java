///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//COMPILE_OPTIONS
//RUNTIME_OPTIONS

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class ANonPerfApproach {
    private static final String FILE = "./measurements.txt";
    public static final String SEPARATOR_CHAR = ";";

    public static void main(String[] args) throws IOException {
        Collector<Measurement, MeasurementAggregator, ResultRow> collector = Collector.of(
                MeasurementAggregator::new,
                (aggregator, measurement) -> {
                    aggregator.min = Math.min(aggregator.min, measurement.value);
                    aggregator.max = Math.max(aggregator.max, measurement.value);
                    aggregator.sum += measurement.value;
                    aggregator.count++;
                },
                (agg1, agg2) -> {
                    var res = new MeasurementAggregator();
                    res.min = Math.min(agg1.min, agg2.min);
                    res.max = Math.max(agg1.max, agg2.max);
                    res.sum = agg1.sum + agg2.sum;
                    res.count = agg1.count + agg2.count;

                    return res;
                },
                agg -> new ResultRow(agg.min, (Math.round(agg.sum * 10.0) / 10.0) / agg.count, agg.max));

        try (final Stream<String> lines = Files.lines(Paths.get(FILE))) {
            Map<String, ResultRow> measurements = new TreeMap<>(lines
                            .map(l -> new Measurement(l.split(SEPARATOR_CHAR)))
                            .collect(groupingBy(Measurement::station, collector)));

            System.out.println(measurements);
        }
    }

    private record Measurement(String station, double value) {
        private Measurement(String[] parts) {
            this(parts[0], Double.parseDouble(parts[1]));
        }
    }

    private record ResultRow(double min, double mean, double max) {

        public String toString() {
            return round(min) + "/" + round(mean) + "/" + round(max);
        }

        private double round(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }

    private static class MeasurementAggregator {
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private double sum;
        private long count;
    }
}
