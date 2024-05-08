#!/bin/sh

set -euo pipefail

FILE_NO_SUFFIX="$(basename ${@: -1} .java)"
OUTPUT_FILE="./profile.${FILE_NO_SUFFIX}.html"

EVENT="cpu"

echo "Measurements file: "
readlink measurements.txt
echo

java -version
echo

jbang --javaagent=ap-loader@maxandersen=start,event=${EVENT},file=${OUTPUT_FILE} $@

xdg-open ${OUTPUT_FILE}
