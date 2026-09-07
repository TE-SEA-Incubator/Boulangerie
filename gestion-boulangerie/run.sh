#!/bin/bash
# ============================================================
#  Lanceur — Gestion Boulangerie  (JavaFX 21 + AtlantaFX)
#  Java 21 + module-path vers les JARs JavaFX 21 natifs Linux
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/target/gestion-boulangerie-1.0.0.jar"

M2="$HOME/.m2/repository/org/openjfx"
FX_VERSION="21.0.2"

# JARs JavaFX natifs Linux 21
FX_PATH="\
$M2/javafx-base/$FX_VERSION/javafx-base-$FX_VERSION-linux.jar:\
$M2/javafx-base/$FX_VERSION/javafx-base-$FX_VERSION.jar:\
$M2/javafx-graphics/$FX_VERSION/javafx-graphics-$FX_VERSION-linux.jar:\
$M2/javafx-graphics/$FX_VERSION/javafx-graphics-$FX_VERSION.jar:\
$M2/javafx-controls/$FX_VERSION/javafx-controls-$FX_VERSION-linux.jar:\
$M2/javafx-controls/$FX_VERSION/javafx-controls-$FX_VERSION.jar:\
$M2/javafx-fxml/$FX_VERSION/javafx-fxml-$FX_VERSION-linux.jar:\
$M2/javafx-fxml/$FX_VERSION/javafx-fxml-$FX_VERSION.jar:\
$M2/javafx-swing/$FX_VERSION/javafx-swing-$FX_VERSION-linux.jar:\
$M2/javafx-swing/$FX_VERSION/javafx-swing-$FX_VERSION.jar"

if [ ! -f "$JAR" ]; then
    echo "JAR introuvable. Construction en cours..."
    cd "$SCRIPT_DIR" && mvn package -q -DskipTests
fi

exec java \
  --module-path "$FX_PATH" \
  --add-modules javafx.controls,javafx.fxml,javafx.swing \
  --add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED \
  --add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED \
  --add-opens=javafx.base/com.sun.javafx.collections=ALL-UNNAMED \
  --add-opens=javafx.graphics/com.sun.javafx.css=ALL-UNNAMED \
  -cp "$JAR" \
  com.boulangerie.MainApp \
  "$@"
