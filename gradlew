#!/bin/sh

##############################################################################
# Gradle start up script for UNIX
##############################################################################

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

exec java -Xmx64m -Xms64m -Dorg.gradle.appname="$APP_BASE_NAME" -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
