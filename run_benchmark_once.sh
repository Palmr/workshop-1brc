#!/bin/sh

set -euo pipefail

FILE_NO_SUFFIX="$(basename ${@: -1} .java)"
OUTPUT_FILE="./output.${FILE_NO_SUFFIX}.txt"

echo "Measurements file: "
readlink measurements.txt
echo

java -version
echo

hyperfine --warmup 1 --runs 1 --output ${OUTPUT_FILE} "jbang $@"

md5sum ${OUTPUT_FILE}
