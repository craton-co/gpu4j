#!/usr/bin/env bash
# Local CI runner in Docker mirroring .github/workflows/ci.yml across JDK 17, 21, and 25.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_VOLUME="gpu4j-m2-cache"
JAVA_VERSIONS=("${@:-17 21 25}")
if [ "$#" -eq 0 ]; then
    JAVA_VERSIONS=(17 21 25)
else
    JAVA_VERSIONS=("$@")
fi

docker volume create "${CACHE_VOLUME}" >/dev/null

RESULTS=()
for jdk in "${JAVA_VERSIONS[@]}"; do
    image="maven:3.9-eclipse-temurin-${jdk}"
    echo ""
    echo "================================================================"
    echo "  Running CI job in Docker: ${image}"
    echo "================================================================"

    docker run --rm \
        -v "${REPO_ROOT}:/workspace" \
        -v "${CACHE_VOLUME}:/root/.m2" \
        -w /workspace \
        "${image}" \
        bash -c '
            set -euo pipefail
            mvn -B -ntp -Prelease clean verify -Dgpg.skip=true
            VER="$(mvn -N -q help:evaluate -Dexpression=project.version -DforceStdout)"
            java -cp "gpu4j-core/target/gpu4j-core-${VER}.jar" examples/Demo.java
        '

    RESULTS+=("JDK ${jdk}: PASS")
done

echo ""
echo "================================================================"
echo "  All Local CI Jobs Passed:"
for res in "${RESULTS[@]}"; do
    echo "    - ${res}"
done
echo "================================================================"
