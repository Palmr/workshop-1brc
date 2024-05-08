///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//COMPILE_OPTIONS -source 21 --enable-preview
//RUNTIME_OPTIONS --enable-preview

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class CustomHashTable {
    public static final int CHUNK_COUNT = Runtime.getRuntime().availableProcessors();
    private static final String FILE = "./measurements.txt";
    public static final char SEPARATOR_CHAR = ';';
    public static final char END_OF_RECORD = '\n';

    public static void main(String[] args) throws IOException, InterruptedException {
        try (var file = new RandomAccessFile(FILE, "r")) {
            final long fileLength = file.length();

            final var chunkSize = fileLength / CHUNK_COUNT;
            final var threads = new Thread[CHUNK_COUNT];
            final ByteArrayKeyedMap[] results = new ByteArrayKeyedMap[CHUNK_COUNT];

            final var memSegment = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, fileLength, Arena.global());

            long startPoint = 0;
            for (int i = 0; i < CHUNK_COUNT; i++) {
                long endPoint = (i + 1) * chunkSize;
                while (memSegment.get(JAVA_BYTE, endPoint++) != END_OF_RECORD) {
                    Thread.onSpinWait();
                }
                final long finalEndPoint = endPoint;
                final long finalStartPoint = startPoint;

                final int tid = i;
                Thread thread = new Thread(() -> {
                    try {
                        results[tid] = readAndParse(memSegment, finalStartPoint, finalEndPoint);
                    } catch (Throwable t) {
                        System.err.println(STR."Thread \{tid} failed :(");
                        // noinspection CallToPrintStackTrace
                        t.printStackTrace();
                    }
                });
                threads[i] = thread;
                thread.start();

                startPoint = endPoint;
            }


            for (var thread : threads) {
                thread.join();
            }

            final Map<String, MeasurementAggregator> finalAggregator = new TreeMap<>();
            for (var result : results) {
                result.getAsUnorderedList().forEach(m -> {
                    final var stationName = new String(m.stationNameBytes, StandardCharsets.UTF_8);
                    finalAggregator.compute(stationName, (_, x) -> {
                        if (x == null) {
                            return m;
                        }
                        else {
                            x.count += m.count;
                            x.min = Math.min(x.min, m.min);
                            x.max = Math.max(x.max, m.max);
                            x.sum += m.sum;
                            return x;
                        }
                    });
                });
            }
            System.out.println(finalAggregator);
        }
    }

    private static ByteArrayKeyedMap readAndParse(final MemorySegment memSegment, final long startPoint, final long endPoint) {
        final ByteArrayKeyedMap results = new ByteArrayKeyedMap();

        for (var cursor = startPoint; cursor < endPoint; ) {

            var semicolonPos = findByte(memSegment, cursor, SEPARATOR_CHAR);
            var newlinePos = findByte(memSegment, semicolonPos + 1, END_OF_RECORD);

            var stationName = memSegment.asSlice(cursor, semicolonPos - cursor).toArray(JAVA_BYTE);
            int signedHashCode = 0;
            for (byte ch : stationName) {
                signedHashCode = 31 * signedHashCode + (ch & 0xff);
            }

            var temp = Double.parseDouble(stringAt(memSegment, semicolonPos + 1, newlinePos));

            int finalSignedHashCode = signedHashCode;
            var stats = results.computeIfAbsent(stationName, finalSignedHashCode);
            stats.sum += temp;
            stats.count++;
            stats.min = Math.min(stats.min, temp);
            stats.max = Math.max(stats.max, temp);
            cursor = newlinePos + 1;
        }
        return results;
    }


    private static long findByte(final MemorySegment memSegment, final long cursor, final int b) {
        for (var i = cursor; i < memSegment.byteSize(); i++) {
            if (memSegment.get(JAVA_BYTE, i) == b) {
                return i;
            }
        }
        throw new RuntimeException(STR."\{(char) b} not found");
    }

    private static String stringAt(final MemorySegment memSegment, final long start, final long limit) {
        return new String(
                memSegment.asSlice(start, limit - start).toArray(JAVA_BYTE),
                StandardCharsets.UTF_8
        );
    }


    private static class MeasurementAggregator {
        final byte[] stationNameBytes;
        final int stationNameHashCode;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private double sum;
        private long count;

        public MeasurementAggregator(final byte[] stationNameBytes, final int stationNameHashCode) {
            this.stationNameBytes = stationNameBytes;
            this.stationNameHashCode = stationNameHashCode;
        }

        public String toString() {
            return STR."\{round(min)}/\{round(sum / count)}/\{round(max)}";
        }

        private double round(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }


    private static class ByteArrayKeyedMap {
        private final int BUCKET_COUNT = 0xFFF; // 413 unique stations in the data set, & 0xFFF ~= 399 (only 14 collisions (given our hashcode implementation))
        private final MeasurementAggregator[] buckets = new MeasurementAggregator[BUCKET_COUNT + 1];
        private final List<MeasurementAggregator> compactUnorderedBuckets = new ArrayList<>(413);

        public MeasurementAggregator computeIfAbsent(final byte[] key, final int keyHashCode) {
            int index = keyHashCode & BUCKET_COUNT;

            while (true) {
                MeasurementAggregator maybe = buckets[index];
                if (maybe == null) {
                    final byte[] copiedKey = Arrays.copyOf(key, key.length);
                    MeasurementAggregator measurementAggregator = new MeasurementAggregator(copiedKey, keyHashCode);
                    buckets[index] = measurementAggregator;
                    compactUnorderedBuckets.add(measurementAggregator);
                    return measurementAggregator;
                }
                else {
                    if (Arrays.equals(key, 0, key.length, maybe.stationNameBytes, 0, maybe.stationNameBytes.length)) {
                        return maybe;
                    }
                    index++;
                    index &= BUCKET_COUNT;
                }
            }
        }

        public List<MeasurementAggregator> getAsUnorderedList() {
            return compactUnorderedBuckets;
        }
    }
}
