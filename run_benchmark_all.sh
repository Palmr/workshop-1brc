#!/bin/sh

set -euo pipefail

echo "Measurements file: "
readlink measurements.txt
echo

java -version
echo

ALL_TESTS=$(ls *.java | grep -v 'MakeMeasurementsFile.java' | sort | paste -sd, -)
hyperfine --warmup 1 --runs 5 -L impl "${ALL_TESTS}" 'jbang {impl}'
