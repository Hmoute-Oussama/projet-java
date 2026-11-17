# Packaging script for Tawkit Desktop on Linux (change #13).
# Produces a .deb (or .rpm) installer with a bundled JRE via jpackage.
#
# Usage:
#   ./scripts/package-linux.sh            # deb
#   ./scripts/package-linux.sh rpm        # rpm

#!/usr/bin/env bash
set -euo pipefail

TYPE="${1:-deb}"
VERSION="1.0"

echo "==> Building the jar..."
mvn -q clean package -DskipTests

echo "==> Creating $TYPE installer with jpackage..."
jpackage \
    --type "$TYPE" \
    --name Tawkit \
    --app-version "$VERSION" \
    --input target \
    --main-jar "tawkit-desktop-$VERSION-SNAPSHOT.jar" \
    --icon src/main/resources/assets/icon.png \
    --dest installer \
    --vendor "Tawkit Desktop team" \
    --description "Offline prayer times display for mosques and homes" \
    --linux-shortcut

echo "==> Done! Installer is in ./installer/"
ls -la installer/
