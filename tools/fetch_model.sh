#!/usr/bin/env bash
# Descarga el modelo Vosk pequeño en español y lo deja en app/src/main/assets/model-es
# Uso: ./tools/fetch_model.sh
set -euo pipefail
cd "$(dirname "$0")/.."
NAME=vosk-model-small-es-0.42
URL="https://alphacephei.com/vosk/models/$NAME.zip"
DEST=app/src/main/assets/model-es
if [ -d "$DEST/am" ]; then echo "El modelo ya está en $DEST"; exit 0; fi
TMP=$(mktemp -d)
echo "Descargando $URL (~39 MB)…"
curl -fL --retry 3 -o "$TMP/model.zip" "$URL"
unzip -q "$TMP/model.zip" -d "$TMP"
mkdir -p "$(dirname "$DEST")"
rm -rf "$DEST"
mv "$TMP/$NAME" "$DEST"
rm -rf "$TMP"
echo "Listo: modelo en $DEST"
du -sh "$DEST"
