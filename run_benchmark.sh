#!/bin/sh

set -euo pipefail

echo "Measurements file: "
readlink measurements.txt
echo

java -version
echo

hyperfine --warmup 1 --runs 5 "jbang $@"
