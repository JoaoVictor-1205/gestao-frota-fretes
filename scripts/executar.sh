#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/classes
find src -name '*.java' > build/fontes.txt
javac --release 17 -encoding UTF-8 -d build/classes @build/fontes.txt
exec java -Dfile.encoding=UTF-8 -cp 'build/classes:lib/*' app.Main "$@"
