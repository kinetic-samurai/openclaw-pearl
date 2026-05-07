#!/usr/bin/env bash
set -euo pipefail

printf 'java: '
java -version 2>&1 | head -n 1 || true

printf 'javac: '
javac -version 2>&1 || echo 'not found'

printf 'JAVA_HOME: '
echo "${JAVA_HOME:-not set}"

printf 'gradle: '
gradle -v 2>/dev/null | awk '/Gradle / { print $2; found=1 } END { if (!found) print "not found" }'

printf 'node: '
node --version 2>/dev/null || echo 'not found'

printf 'pnpm: '
pnpm --version 2>/dev/null || echo 'not found'

cat <<'NOTE'

Expected:
- Java 21+ JDK, not only a JRE. `javac -version` must work.
- Gradle 8.8+ recommended for Kotlin 2.x and Kotlin DSL.
- Node 20+.
- pnpm 9+.

If Gradle says the Java installation does not provide JAVA_COMPILER,
install the full JDK, for example:

  sudo apt update
  sudo apt install openjdk-21-jdk

Then retry:

  gradle -p apps/litert-bridge run
NOTE
