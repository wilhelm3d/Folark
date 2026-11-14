#!/usr/bin/env bash
# Builds video/score.wav: a 40.000 s bed whose sections follow the video's scenes.
# Scene map: boot 0 | home 4.8 | drawer styles 6.8 | folders 10.4 | recents 12.2
#            splits 17.48 | picker 23.08 | pull 24.98 | divider 26.93
#            taskbar 28.88 | widgets 30.93 | end 37.22
set -euo pipefail
FF="tools/ffmpeg/ffmpeg-9.0.2-essentials_build/bin/ffmpeg.exe"
TMP="video/.score"
rm -rf "$TMP"; mkdir -p "$TMP"
NF=0
declare -a F=() D=()

ms(){ awk "BEGIN{printf \"%d\", ($1)*1000 + 0.5}"; }
sec(){ awk "BEGIN{printf \"%.3f\", $1}"; }

add(){ # expr dur at [filter]
  local expr="$1" dur="$2" at="$3" flt="${4:-}" p
  NF=$((NF+1)); p="$TMP/p$NF.wav"
  if [ -n "$flt" ]; then
    "$FF" -hide_banner -loglevel error -y -f lavfi -i "aevalsrc='${expr}':s=44100:d=${dur}" \
      -af "$flt" -ac 2 -c:a pcm_s16le "$p"
  else
    "$FF" -hide_banner -loglevel error -y -f lavfi -i "aevalsrc='${expr}':s=44100:d=${dur}" \
      -ac 2 -c:a pcm_s16le "$p"
  fi
  F+=("$p"); D+=("$at")
}

pad(){ # t0 dur "freqs" amp atk rel
  local t0="$1" dur="$2" notes="$3" amp="$4" atk="$5" rel="$6" sum="" f
  for f in $notes; do sum+="sin(2*PI*${f}*t)+"; done
  sum="${sum%+}"
  add "(${sum})/9*${amp}*min(1,t/${atk})*min(1,max(0,(${dur}-t)/${rel}))" "$(sec "$dur")" "$(ms "$t0")"
}

note(){ # t0 freq amp dur
  add "${3}*(sin(2*PI*${2}*t)+0.3*sin(4*PI*${2}*t))*min(1,t/0.012)*exp(-t*3.2)" "$(sec "$4")" "$(ms "$1")"
}

bell(){ add "${3}*sin(2*PI*${2}*t)*exp(-t*4.2)+${3}*0.35*sin(4*PI*${2}*t)*exp(-t*6)" "0.9" "$(ms "$1")"; }

kicktrain(){ # t0 t1 period amp
  local dur; dur=$(awk "BEGIN{printf \"%.3f\", $2-$1}")
  add "${4}*sin(2*PI*(46+120*exp(-mod(t,${3})*26))*mod(t,${3}))*exp(-mod(t,${3})*13)" "$dur" "$(ms "$1")"
}

hattrain(){ # t0 t1 period amp
  local dur; dur=$(awk "BEGIN{printf \"%.3f\", $2-$1}")
  add "${4}*(sin(2*PI*4800*t)*sin(2*PI*1310*t))*exp(-mod(t,${3})*90)" "$dur" "$(ms "$1")"
}

subhit(){ local a="${2:-0.34}"; add "${a}*sin(2*PI*(56*exp(-t*3.0)+33)*t)*exp(-t*2.2)" "1.7" "$(ms "$1")"; }

revertail(){
  add "0.30*(sin(2*PI*110*t)+sin(2*PI*164.81*t))/2*min(1,t/0.10)*exp(-t*1.05)" "$(sec "$2")" "$(ms "$1")" \
    "aecho=0.8:0.86:70|140|210|280:0.42|0.28|0.17|0.09"
}

riser(){ add "0.10*sin(2*PI*(180*pow(3.6,t/${2}))*t)*min(1,t/0.12)" "$(sec "$2")" "$(ms "$1")"; }

swish(){ local a="${2:-0.13}"; add "${a}*(sin(2*PI*(900+600*sin(2*PI*0.8*t))*t)*sin(2*PI*2300*t))*exp(-t*9)" "0.45" "$(ms "$1")"; }
transitions(){ local t; for t in "$@"; do swish "$t" 0.12; done; }

# ---------------------------------------------------------------- opening
pad 0    4.9  "110 130.81 164.81" 0.80 1.10 1.20
bell 0.35 440 0.075
bell 2.15 659.25 0.06

# ---------------------------------------------------------------- home -> drawer styles
pad 4.8  2.4  "87.31 110 130.81"  0.62 0.50 0.90
pad 6.8  2.1  "130.81 164.81 196" 0.70 0.35 0.60
pad 8.85 1.6  "98 123.47 146.83"  0.60 0.30 0.60
i=0
for f in 261.63 329.63 392 329.63 261.63 392 329.63 392 523.25 392 329.63 392; do
  note "$(awk "BEGIN{printf \"%.3f\", 6.8 + $i*0.30}")" "$f" 0.052 0.28
  i=$((i+1))
done

# ---------------------------------------------------------------- folders
pad 10.4 1.85 "110 130.81 164.81" 0.58 0.30 0.55
bell 10.52 523.25 0.06
bell 11.42 659.25 0.052

# ---------------------------------------------------------------- recents
pad 12.2 5.35 "110 130.81 164.81" 0.50 0.40 0.80
i=0
for f in 440 523.25 587.33 659.25 783.99 659.25 587.33 523.25 440 523.25 659.25; do
  bell "$(awk "BEGIN{printf \"%.3f\", 12.22 + $i*0.48}")" "$f" 0.085
  i=$((i+1))
done

# ---------------------------------------------------------------- splits
pad 17.48 5.55 "146.83 174.61 220" 0.60 0.35 0.70
kicktrain 17.48 23.02 0.625 0.26
for t in 17.48 18.28 19.03 19.78 20.58 21.38 22.23; do
  hattrain "$t" "$(awk "BEGIN{printf \"%.3f\", $t+0.06}")" 0.05 0.10
done

# ---------------------------------------------------------------- picker
pad 23.08 1.88 "164.81 196 246.94" 0.60 0.50 0.70
bell 23.63 493.88 0.065
bell 24.28 659.25 0.055

# ---------------------------------------------------------------- pull + divider
riser 24.98 2.00
pad 25.06 1.85 "164.81 196 246.94" 0.30 1.40 0.60
hattrain 25.88 26.88 0.11 0.045
pad 26.93 1.93 "110 130.81 164.81" 0.62 0.05 0.50
kicktrain 26.93 28.85 0.625 0.24
hattrain 27.24 28.85 0.625 0.07

# ---------------------------------------------------------------- taskbar
pad 28.88 2.03 "130.81 164.81 196 246.94" 0.80 0.08 0.35
i=0
for f in 659.25 587.33 523.25 493.88 440 523.25 659.25 587.33; do
  note "$(awk "BEGIN{printf \"%.3f\", 28.88 + $i*0.28}")" "$f" 0.090 0.34
  i=$((i+1))
done
kicktrain 28.88 30.90 0.31 0.30
hattrain 29.03 30.90 0.155 0.085

# ---------------------------------------------------------------- widgets -> search
pad 30.93 1.62 "98 123.47 146.83" 0.56 0.25 0.40
pad 32.55 1.42 "130.81 164.81 196" 0.56 0.25 0.40
pad 34.12 1.42 "110 130.81 164.81" 0.56 0.25 0.40
pad 35.52 1.72 "87.31 110 130.81" 0.56 0.25 0.40
kicktrain 30.93 37.12 0.625 0.26
hattrain 31.24 37.12 0.625 0.075
i=0
for f in 392 440 523.25 659.25 587.33 523.25 440 392; do
  note "$(awk "BEGIN{printf \"%.3f\", 31.14 + $i*0.80}")" "$f" 0.048 0.55
  i=$((i+1))
done

# ---------------------------------------------------------------- end card
pad 37.22 2.78 "110 130.81 164.81 246.94" 0.88 0.25 1.40
bell 37.27 880 0.075
bell 37.77 659.25 0.055
note 38.52 440 0.05 1.45
subhit 38.90 0.34
revertail 38.72 3.20

transitions 4.8 6.8 12.2 17.48 23.08 24.98 26.93 28.88 30.93 32.72 34.12 35.52 37.22

# ---------------------------------------------------------------- mix
args=(); parts=""; mix=""
for i in "${!F[@]}"; do
  args+=(-i "${F[$i]}")
  parts+="[$i:a]adelay=${D[$i]}|${D[$i]}[a$i];"
  mix+="[a$i]"
done
"$FF" -hide_banner -loglevel error -y "${args[@]}" \
  -filter_complex "${parts}${mix}amix=inputs=${NF}:normalize=0,volume=3.5dB,alimiter=limit=0.94,afade=t=out:st=39.45:d=0.55[a]" \
  -map "[a]" -t 40 -ar 44100 -ac 2 -c:a pcm_s16le video/score.wav
ls -la video/score.wav
