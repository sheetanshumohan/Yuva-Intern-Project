#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
javac -d "$TMP_DIR" scripts/JavaSyntaxCheck.java
java -cp "$TMP_DIR" JavaSyntaxCheck .
javac -d "$TMP_DIR" src/main/java/com/foodplant/util/WeightNormalizer.java scripts/WeightNormalizerSelfTest.java
java -cp "$TMP_DIR" WeightNormalizerSelfTest
python3 scripts/self_check.py
