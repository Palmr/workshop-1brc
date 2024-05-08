#!/bin/sh

set -euo pipefail

echo "Measurements file linked: "
readlink measurements.txt
echo

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
