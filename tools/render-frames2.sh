#!/usr/bin/env bash
# Renders frames [START..END] of the 40 s timeline at native 1080x1620 with headless Edge.
# Each worker keeps its own browser profile so we do not create a profile per frame,
# and no --virtual-time-budget is used: the page pins its animations synchronously.
# Usage: render-frames2.sh [start] [end] [parallel]
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
URL="file:///C:/Users/wilhe/Documents/Folark-redesign/video/index.html"
ROOT="$PWD"
OUT="$ROOT/video/frames"
START=${1:-0}
END=${2:-1199}
PAR=${3:-3}
mkdir -p "$OUT"

render_one(){
  local i=$1 w=$2 n ms
  n=$(printf "%04d" "$i")
  [ -s "$OUT/f-$n.png" ] && return 0
  ms=$(( (i*1000 + 15) / 30 ))
  "$EDGE" --headless=new --disable-gpu --hide-scrollbars --no-sandbox \
    --no-first-run --no-default-browser-check --disable-sync --disable-extensions \
    --disable-background-networking --disable-component-update \
    --user-data-dir="$ROOT/video/.edgeprof-$w" --force-device-scale-factor=1 \
    --window-size=1080,1620 --screenshot="$OUT/f-$n.png" "$URL?shot=$ms" >/dev/null 2>&1
}

for w in $(seq 1 "$PAR"); do
  (
    i=$((START + w - 1))
    while [ "$i" -le "$END" ]; do
      render_one "$i" "$w"
      i=$((i + PAR))
    done
  ) &
done
wait

echo "frames=$(ls "$OUT" | grep -c '\.png$')"
