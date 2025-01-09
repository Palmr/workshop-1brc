#!/bin/sh

set -euo pipefail

if [[ $# -gt 0 ]] ; then
  echo 'Usage: ./run_benchmark_all.sh'
  exit 0
fi

MEASUREMENTS_FILE="measurements.txt"

if [ ! -L ${MEASUREMENTS_FILE} ]; then
  echo "Measurements file should be a symbolic link:"
  stat ${MEASUREMENTS_FILE}
  exit 1
else
  echo "Measurements file: "
  readlink ${MEASUREMENTS_FILE}
  echo
fi

java -version
echo

ALL_TESTS=$(ls *.java | grep -v 'MakeMeasurementsFile.java' | sort | paste -sd, -)
hyperfine --warmup 1 --runs 5 -L impl "${ALL_TESTS}" 'jbang {impl}'
