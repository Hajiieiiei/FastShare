#!/bin/sh
#
# Gradle wrapper startup script
# This is a minimal replacement for the standard gradlew script.
# It locates the wrapper jar and downloads Gradle if needed.

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$DIR"

# Determine the JVM
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Read gradle-wrapper.properties
PROPS_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
if [ ! -f "$PROPS_FILE" ]; then
    echo "Error: Could not find gradle-wrapper.properties"
    exit 1
fi

DISTRIBUTION_URL=$(grep "^distributionUrl=" "$PROPS_FILE" | cut -d= -f2- | tr -d '\r\n')
DISTRIBUTION_URL=$(echo "$DISTRIBUTION_URL" | sed 's|\\:|:|g')

DISTRIBUTION_BASE=$(grep "^distributionBase=" "$PROPS_FILE" | cut -d= -f2- | tr -d '\r\n')
DISTRIBUTION_PATH=$(grep "^distributionPath=" "$PROPS_FILE" | cut -d= -f2- | tr -d '\r\n')
DISTRIBUTION_BASE="${DISTRIBUTION_BASE:-GRADLE_USER_HOME}"
DISTRIBUTION_PATH="${DISTRIBUTION_PATH:-wrapper/dists}"

if [ "$DISTRIBUTION_BASE" = "GRADLE_USER_HOME" ]; then
    GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
    DIST_DIR="$GRADLE_USER_HOME/$DISTRIBUTION_PATH"
else
    DIST_DIR="$DISTRIBUTION_BASE/$DISTRIBUTION_PATH"
fi

# Extract distribution filename
DIST_FILENAME=$(basename "$DISTRIBUTION_URL")
DIST_NAME="${DIST_FILENAME%-bin.zip}"

FULL_DIST_DIR="$DIST_DIR/$DIST_NAME"

if [ ! -d "$FULL_DIST_DIR" ]; then
    echo "Downloading Gradle..."
    mkdir -p "$FULL_DIST_DIR"
    if command -v wget > /dev/null 2>&1; then
        wget -q -O "$DIST_DIR/$DIST_FILENAME" "$DISTRIBUTION_URL"
    else
        curl -s -L -o "$DIST_DIR/$DIST_FILENAME" "$DISTRIBUTION_URL"
    fi
    echo "Extracting Gradle..."
    unzip -q -o "$DIST_DIR/$DIST_FILENAME" -d "$DIST_DIR"
    touch "$FULL_DIST_DIR/ok"
fi

GRADLE_HOME=$(find "$DIST_DIR" -maxdepth 1 -name "$DIST_NAME-*" -type d | head -1)

# Execute Gradle
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
