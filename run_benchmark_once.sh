#!/bin/sh

set -euo pipefail

if [[ $# -eq 0 ]] ; then
  echo 'Usage: ./run_benchmark_once.sh file.java'
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
OUTPUT_FILE="./output.${FILE_NO_SUFFIX}.txt"

hyperfine --warmup 1 --runs 1 --output ${OUTPUT_FILE} "jbang $@"

if command -v md5sum 2>&1 >/dev/null
then
  md5sum ${OUTPUT_FILE}
elif command -v shasum 2>&1 >/dev/null
then
  shasum ${OUTPUT_FILE}
else
  echo "No command found to checksum output? Tried md5sum and shasum"
  exit 1
fi
