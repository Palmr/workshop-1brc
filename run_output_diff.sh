#!/bin/sh

set -euo pipefail

FORMATTED_DIR=$(mktemp -d)
for f in $(ls output.*.txt); do
  tr , '\n' < $f > $FORMATTED_DIR/$f
done

cd $FORMATTED_DIR
KNOWN_FILE=$(ls ./output.*.txt | head -n 1)
for i in ./output.*.txt; do
  if ! diff -q "$i" $KNOWN_FILE &>/dev/null; then
    printf '%.30s%65s\n' "$i" $KNOWN_FILE "================" "===================";
    diff -y --suppress-common-lines "$i" $KNOWN_FILE | head -n 25
  else
    echo "$KNOWN_FILE matches $i"
  fi
done

trap "exit 1"           HUP INT PIPE QUIT TERM
trap 'rm -rf "FORMATTED_DIR"'  EXIT
