#!/usr/bin/env sh

set -eu

GRADLE_VERSION="8.9"
WRAPPER_DIR=".gradle-wrapper"
ZIP_PATH="$WRAPPER_DIR/gradle-$GRADLE_VERSION-bin.zip"
DIST_DIR="$WRAPPER_DIR/gradle-$GRADLE_VERSION"

# Prefer LTS JDKs supported by Android/Gradle toolchain when multiple versions exist.
if [ -d "/usr/local/sdkman/candidates/java/17.0.14-ms" ]; then
  export JAVA_HOME="/usr/local/sdkman/candidates/java/17.0.14-ms"
elif [ -d "/usr/local/sdkman/candidates/java/17.0.13-ms" ]; then
  export JAVA_HOME="/usr/local/sdkman/candidates/java/17.0.13-ms"
elif [ -d "/usr/local/sdkman/candidates/java/21.0.9-ms" ]; then
  export JAVA_HOME="/usr/local/sdkman/candidates/java/21.0.9-ms"
fi

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

mkdir -p "$WRAPPER_DIR"

if [ ! -d "$DIST_DIR" ]; then
  if [ ! -f "$ZIP_PATH" ]; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_PATH"
  fi
  unzip -q -o "$ZIP_PATH" -d "$WRAPPER_DIR"
fi

exec "$DIST_DIR/bin/gradle" "$@"