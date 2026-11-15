#!/bin/sh
mkdir -p out
javac -d out -cp lib/sqlite-jdbc.jar src/workspace/*.java && java -cp "out:lib/sqlite-jdbc.jar" workspace.Main
