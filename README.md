# Workshop #1brc

## Setup

 - Linux
 - perf (see distro package install instructions)
 - [sdkman](https://sdkman.io/)
 - [jbang](https://www.jbang.dev/)
 - [hyperfine](https://github.com/sharkdp/hyperfine)
 - [async profiler](https://github.com/async-profiler/async-profiler)

## Creating the dataset

```bash
jbang MakeMeasurementsFile.java -c 100 measurements.100.txt 
jbang MakeMeasurementsFile.java -c 10000000 measurements.10m.txt 
jbang MakeMeasurementsFile.java -c 1000000000 measurements.1b.txt 

# Start with a small file that will enable fast feedback
ln --force --symbolic measurements.100.txt measurements.txt
```

## Run order

- [ANonPerfApproach.java](ANonPerfApproach.java)
- [BetterWithMemMap.java](BetterWithMemMap.java)
- [CustomHashTable.java](CustomHashTable.java)
- [DoublesWereTrouble.java](DoublesWereTrouble.java)
- [EvenMoreParallelWithSWAR.java](EvenMoreParallelWithSWAR.java)

### First run

```bash
sdk install java 21.0.2-tem
sdk use java 21.0.2-tem
java -version

./run_benchmark.sh ANonPerfApproach.java
```

### Graal VM

```bash
sdk install java 21.0.2-graalce
sdk use java 21.0.2-graalce
java -version

./run_benchmark.sh ANonPerfApproach.java
```

## Benchmarking

```bash
./run_benchmark.sh ANonPerfApproach.java # Runs 1 warmup, 5 timed runs, outputs benchmark results only
./run_benchmark_all.sh # Runs 1 warmup, 5 timed runs for all implementations, outputs benchmark results only
./run_benchmark_once.sh ANonPerfApproach.java # Runs 1 warmup, 1 timed runs, outputs benchmark results and md5sum of output
./run_benchmark_with_async_profiler.sh ANonPerfApproach.java # Runs jbang directly but with async profiler attached to create a CPU flamegraph
./run_benchmark_with_perf.sh ANonPerfApproach.java # Runs jbang directly but with linux perf monitoring some notable events
```

### Validation

When making modifications to implementations it is very easy to cause a bug due to total lack of testing.

To help with this, `./run_benchmark_once.sh` will save the output of the command to an `output.*.txt` file and you
can use `./run_output_diff.sh` to confirm all output files have the same content.

If there is a diff, check they all ran against the same input file and re-run to confirm.