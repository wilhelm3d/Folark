#!/usr/bin/env bash
# Renders frames [START..END] of the 40 s timeline at native 1080x1620 with headless Edge.
# The page's ?shot=<ms> freeze pins CSS animations, so every frame is deterministic.
# Usage: render-frames.sh [start] [end] [parallel] [budget]
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
URL="file:///C:/Users/wilhe/Documents/Folark-redesign/video/index.html"
ROOT="$PWD"
OUT="$ROOT/video/frames"
START=${1:-0}
END=${2:-1199}
PAR=${3:-6}
BUDGET=${4:-2200}
mkdir -p "$OUT"

run_one(){
  local i=$1 n ms ud log
  n=$(printf "%04d" "$i")
  [ -s "$OUT/f-$n.png" ] && return 0
  ms=$(( (i*1000 + 15) / 30 ))
  ud=$(mktemp -d)
  log=$(mktemp)
  # Watchdog: some frames hang Edge (virtual-time + compositing); kill after 30 s
  # so one bad frame can never accumulate zombies and starve the machine.
  "$EDGE" --headless=new --disable-gpu --hide-scrollbars --no-sandbox \
    --force-device-scale-factor=1 --user-data-dir="$ud" \
    --virtual-time-budget="$BUDGET" --window-size=1080,1620 \
    --screenshot="$OUT/f-$n.png" "$URL?shot=$ms" >"$log" 2>&1 &
  local pid=$!
  ( sleep 30; kill -9 $pid 2>/dev/null ) & local wd=$!
  wait $pid 2>/dev/null
  kill $wd 2>/dev/null
  wait $wd 2>/dev/null
  [ -s "$OUT/f-$n.png" ] || echo "MISS f-$n (ms=$ms)"
  rm -rf "$ud" "$log"
}

i=$START
while [ $i -le $END ]; do
  n=$(( i + PAR - 1 )); [ $n -gt $END ] && n=$END
  j=$i
  while [ $j -le $n ]; do run_one $j & j=$(( j + 1 )); done
  wait
  i=$(( n + 1 ))
done
echo "RENDER-PASS-DONE"
