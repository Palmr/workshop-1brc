# Workshop #1brc

Original challenge: https://github.com/gunnarmorling/1brc

This repo was used for a workshop given internally at LMAX as well as at Devoxx UK 2024:
https://docs.google.com/presentation/d/1XA78LGgj9ldSPr5mjDAZQHzXQPFB7sowgfLhH0kQg3Y/edit?usp=sharing

## Setup

 - Linux (Some things may work on a Mac or WSL, YMMV)
 - [sdkman](https://sdkman.io/)
 - JDKs
     - `sdk install java 21.0.5-tem`
     - `sdk install java 21.0.5-graalce`
 - [jbang](https://www.jbang.dev/) (you can use `sdk install jbang` to have sdkman install this)
 - [hyperfine](https://github.com/sharkdp/hyperfine)
 - [async profiler](https://github.com/async-profiler/async-profiler) (jbang can automatically get this, see `./run_benchmark_once_with_async_profiler.sh`)
 - [perf](https://perfwiki.github.io/main/) (see distro package install instructions)

I'm using Intellij with the JBang plugin. I have Intellij set up with JVM's installed by sdkman and language level for 
the project set to `21 Preview` since 1brc was about enjoying new Java features.

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
sdk install java 21.0.5-tem
sdk use java 21.0.5-tem
java -version

./run_benchmark.sh ANonPerfApproach.java
```

### Graal VM

```bash
sdk install java 21.0.5-graalce
sdk use java 21.0.5-graalce
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