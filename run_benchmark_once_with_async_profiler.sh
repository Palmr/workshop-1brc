#!/bin/sh

set -euo pipefail

if [[ $# -eq 0 ]] ; then
  echo 'Usage: ./run_benchmark_once_with_async_profiler.sh file.java'
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

FILE_NO_SUFFIX="$(basename ${@: -1} .java)"
OUTPUT_FILE="./profile.${FILE_NO_SUFFIX}.html"
EVENT="cpu"

jbang --javaagent=ap-loader@maxandersen=start,event=${EVENT},file=${OUTPUT_FILE} $@

if command -v xdg-open 2>&1 >/dev/null
then
  xdg-open ${OUTPUT_FILE}
elif command -v open 2>&1 >/dev/null
then
  open ${OUTPUT_FILE}
else
  echo "Profiler output file can be opened in your browser:"
  readlink -f ${OUTPUT_FILE}
fi
