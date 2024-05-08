///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//COMPILE_OPTIONS -source 21 --enable-preview
//RUNTIME_OPTIONS --enable-preview

import sun.misc.Unsafe;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class EvenMoreParallelWithSWAR {
    private static final Unsafe UNSAFE = unsafe();

    private static Unsafe unsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(Unsafe.class);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static final int CHUNK_COUNT = Runtime.getRuntime().availableProcessors();
    private static final String FILE = "./measurements.txt";
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
                        results[tid] = readAndParse(memSegment.asSlice(finalStartPoint, finalEndPoint - finalStartPoint));
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

    private static ByteArrayKeyedMap readAndParse(final MemorySegment memSegment) {
        final ByteArrayKeyedMap results = new ByteArrayKeyedMap();

        var inputBase = memSegment.address();
        var inputSize = memSegment.byteSize();

        long cursor = 0;
        while (cursor < inputSize) {
            long nameStartOffset = cursor;
            int hash = 0;
            int nameLen = 0;
            while (true) {
                long nameWord = UNSAFE.getLong(inputBase + nameStartOffset + nameLen);
                long matchBits = semicolonMatchBits(nameWord);
                if (matchBits != 0) {
                    nameLen += nameLen(matchBits);
                    nameWord = maskWord(nameWord, matchBits);
                    hash = hash(hash, nameWord);
                    cursor += nameLen;
                    long tempWord = UNSAFE.getLong(inputBase + cursor);
                    int dotPos = dotPos(tempWord);
                    int temperature = parseTemperature(tempWord, dotPos);
                    cursor += (dotPos >> 3) + 3;

                    var stationName = memSegment.asSlice(nameStartOffset, nameLen - 1).toArray(JAVA_BYTE);
                    var stats = results.computeIfAbsent(stationName, hash);
                    stats.sum += temperature;
                    stats.count++;
                    stats.min = Math.min(stats.min, temperature);
                    stats.max = Math.max(stats.max, temperature);
                    break;
                }
                hash = hash(hash, nameWord);
                nameLen += Long.BYTES;
            }
        }

        return results;
    }

    // Copied below SWAR code from the QuestDB 1brc blog post (Marko Topolnik 2024)
    // https://questdb.io/blog/billion-row-challenge-step-by-step/#optimization-4-sunmiscunsafe-swar

    private static final long BROADCAST_SEMICOLON = 0x3B3B3B3B3B3B3B3BL; // 0x3B == ';' in ASCII
    private static final long BROADCAST_0x01 = 0x0101010101010101L;
    private static final long BROADCAST_0x80 = 0x8080808080808080L;

    private static long semicolonMatchBits(long word) {
        long diff = word ^ BROADCAST_SEMICOLON;
        return (diff - BROADCAST_0x01) & (~diff & BROADCAST_0x80);
    }

    // credit: artsiomkorzun
    private static long maskWord(long word, long matchBits) {
        long mask = matchBits ^ (matchBits - 1);
        return word & mask;
    }

    private static final long DOT_BITS = 0x10101000;
    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);


    // credit: merykitty
    // The 4th binary digit of the ascii of a digit is 1 while
    // that of the '.' is 0. This finds the decimal separator.
    // The value can be 12, 20, 28
    private static int dotPos(long word) {
        return Long.numberOfTrailingZeros(~word & DOT_BITS);
    }

    // credit: merykitty
    // word contains the number: X.X, -X.X, XX.X or -XX.X
    private static int parseTemperatureOG(long word, int dotPos) {
        // signed is -1 if negative, 0 otherwise
        final long signed = (~word << 59) >> 63;
        final long removeSignMask = ~(signed & 0xFF);
        // Zeroes out the sign character in the word
        long wordWithoutSign = word & removeSignMask;
        // Shifts so that the digits come to fixed positions:
        // 0xUU00TTHH00 (UU: units digit, TT: tens digit, HH: hundreds digit)
        long digitsAligned = wordWithoutSign << (28 - dotPos);
        // Turns ASCII chars into corresponding number values. The ASCII code
        // of a digit is 0x3N, where N is the digit. Therefore, the mask 0x0F
        // passes through just the numeric value of the digit.
        final long digits = digitsAligned & 0x0F000F0F00L;
        // Multiplies each digit with the appropriate power of ten.
        // Representing 0 as . for readability,
        // 0x.......U...T.H.. * (100 * 0x1000000 + 10 * 0x10000 + 1) =
        // 0x.U...T.H........ * 100 +
        // 0x...U...T.H...... * 10 +
        // 0x.......U...T.H..
        //          ^--- H, T, and U are lined up here.
        // This results in our temperature lying in bits 32 to 41 of this product.
        final long absValue = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        // Apply the sign. It's either all 1's or all 0's. If it's all 1's,
        // absValue ^ signed flips all bits. In essence, this does the two's
        // complement operation -a = ~a + 1. (All 1's represents the number -1).
        return (int) ((absValue ^ signed) - signed);
    }

    // credit: merykitty and royvanrijn
    private static int parseTemperature(long numberBytes, int dotPos) {
        // numberBytes contains the number: X.X, -X.X, XX.X or -XX.X
        final long invNumberBytes = ~numberBytes;

        // Calculates the sign
        final long signed = (invNumberBytes << 59) >> 63;
        final int _28MinusDotPos = (dotPos ^ 0b11100);
        final long minusFilter = ~(signed & 0xFF);
        // Use the pre-calculated decimal position to adjust the values
        final long digits = ((numberBytes & minusFilter) << _28MinusDotPos) & 0x0F000F0F00L;

        // Multiply by a magic (100 * 0x1000000 + 10 * 0x10000 + 1), to get the result
        final long absValue = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        // And apply the sign
        return (int) ((absValue + signed) ^ signed);
    }

    private static int nameLen(long separator) {
        return (Long.numberOfTrailingZeros(separator) >>> 3) + 1;
    }

    private static int hash(int prevHash, long word) {
        return (int)Long.rotateLeft((prevHash ^ word) * 0x51_7c_c1_b7_27_22_0a_95L, 13);
    }

    // Copied above SWAR code from the QuestDB 1brc blog post (Marko Topolnik 2024)
    // https://questdb.io/blog/billion-row-challenge-step-by-step/#optimization-4-sunmiscunsafe-swar


    private static class MeasurementAggregator {
        final byte[] stationNameBytes;
        final int stationNameHashCode;
        private int min = Integer.MAX_VALUE;
        private int max = Integer.MIN_VALUE;
        private int sum;
        private long count;

        public MeasurementAggregator(final byte[] stationNameBytes, final int stationNameHashCode) {
            this.stationNameBytes = stationNameBytes;
            this.stationNameHashCode = stationNameHashCode;
        }

        public String toString() {
            return STR."\{round(min * 0.1)}/\{round((sum * 0.1) / count)}/\{round(max * 0.1)}";
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
