#!/usr/bin/env bash
# Needs: JDK 17+ and kotlinc (https://github.com/JetBrains/kotlin/releases). Run from the project root.
set -e
CORE=app/src/main/java/com/liam/whackbot/core/*.kt
mkdir -p /tmp/wb && kotlinc -nowarn $CORE tools/Eval.kt tools/PlannerTest.kt tools/ScaleTest.kt -include-runtime -d /tmp/wb/t.jar
echo "--- detector on the 14 sample screenshots ---"; java -cp /tmp/wb/t.jar EvalKt
echo "--- planner / fail-safe logic ---";           java -cp /tmp/wb/t.jar PlannerTestKt
echo "--- native-resolution scaling ---";           java -cp /tmp/wb/t.jar ScaleTestKt
# To rebuild templates after choosing different source frames in tools/Eval.kt:  java -cp /tmp/wb/t.jar EvalKt build
