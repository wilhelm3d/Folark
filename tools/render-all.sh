#!/usr/bin/env bash
# Full native render + rebuild. Renders all 1200 frames at 1080x1620 with headless Edge,
# encodes the master, re-exports the social crops, and copies everything to Downloads.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
BIN="tools/ffmpeg/ffmpeg-9.0.2-essentials_build/bin"
N=1200

echo "render start $(date +%H:%M:%S)"
bash tools/render-frames2.sh 0 $((N-1)) 6
have=$(ls video/frames 2>/dev/null | grep -c '\.png$')
echo "rendered $have/$N $(date +%H:%M:%S)"
if [ "$have" -lt "$N" ]; then
  echo "SECOND PASS for missing frames"
  bash tools/render-frames2.sh 0 $((N-1)) 4
  have=$(ls video/frames 2>/dev/null | grep -c '\.png$')
  echo "after retry $have/$N"
fi
[ "$have" -ge "$N" ] || { echo "INCOMPLETE - not encoding"; exit 1; }

echo "encode $(date +%H:%M:%S)"
"$BIN/ffmpeg.exe" -hide_banner -loglevel warning -y -framerate 30 -i video/frames/f-%04d.png \
  -frames:v "$N" -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p video/.vfull.mp4 || exit 1
"$BIN/ffmpeg.exe" -hide_banner -loglevel warning -y -i video/.vfull.mp4 -i video/score.wav \
  -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -ar 44100 -shortest -movflags +faststart video/.mux.mp4 || exit 1
mv video/.mux.mp4 video/Folark-Features-40s.mp4

echo "exports $(date +%H:%M:%S)"
"$BIN/ffmpeg.exe" -hide_banner -loglevel warning -y -i video/Folark-Features-40s.mp4 \
  -vf "crop=1080:1080:0:106" -c:v libx264 -preset medium -crf 17 -pix_fmt yuv420p -c:a copy -movflags +faststart video/.sq3.mp4 || exit 1
mv video/.sq3.mp4 video/Folark-Square-1080x1080.mp4
"$BIN/ffmpeg.exe" -hide_banner -loglevel warning -y -i video/Folark-Features-40s.mp4 -filter_complex \
  "[0:v]scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,gblur=sigma=40,eq=brightness=-0.10:saturation=1.1[bg];[0:v]scale=-2:1080[fg];[bg][fg]overlay=(W-w)/2:0:format=auto,format=yuv420p[v]" \
  -map "[v]" -map 0:a -c:v libx264 -preset medium -crf 17 -c:a copy -movflags +faststart video/.ls3.mp4 || exit 1
mv video/.ls3.mp4 video/Folark-Landscape-1920x1080.mp4

for f in Folark-Features-40s.mp4 Folark-Square-1080x1080.mp4 Folark-Landscape-1920x1080.mp4; do
  cp "video/$f" "/c/Users/wilhe/Downloads/$f"
  echo "--- $f ---"
  "$BIN/ffprobe.exe" -v error -show_entries format=duration,size -show_entries stream=codec_type,width,height,nb_frames -of default=nw=1 "video/$f"
done
rm -f video/.vfull.mp4
echo "DONE $(date +%H:%M:%S)"
