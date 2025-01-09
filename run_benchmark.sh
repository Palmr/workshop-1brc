#!/bin/sh

set -euo pipefail

if [[ $# -eq 0 ]] ; then
  echo 'Usage: ./run_benchmark.sh file.java'
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

hyperfine --warmup 1 --runs 5 "jbang $@"
