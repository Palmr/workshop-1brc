#!/bin/sh

set -euo pipefail

if [[ $# -eq 0 ]] ; then
  echo 'Usage: ./run_benchmark_once_with_perf.sh file.java'
  exit 0
fi

if ! command -v perf 2>&1 >/dev/null
then
  echo "No perf command found, not installed or not supported on this system (Linux/BSD only)"
  uname -a
  exit 1
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

perf stat -e branches,\
branch-misses,\
cache-references,\
cache-misses,\
cycles,\
instructions,\
idle-cycles-backend,\
idle-cycles-frontend,\
task-clock,\
page-faults,\
major-faults,\
minor-faults\
 -- jbang $@ >/dev/null
