#!/usr/bin/env bash
# Renders the companion app's design previews from the real firmware UI (the host simulator) and writes them, cropped to a circle,
# as drawables for the Satellites app. Needs: xvfb, imagemagick, libsdl2-dev; run `esphome compile sim_previews.yaml` first.
#   tools/sim_previews.sh [outdir]      (default: ../companion/android/satellites/src/main/res/drawable-nodpi)
set -euo pipefail
OUT=${1:-../companion/android/satellites/src/main/res/drawable-nodpi}
TMP=$(mktemp -d)
mkdir -p "$OUT"
PROG=$(ls .esphome/build/clive-sim-previews/.pioenvs/clive-sim-previews/program)
NAMES=(glance clock weather ring)
# each design block is 9.5 s after the 2 s start: rest at +1.5, talk at +4.5, card at +7.5 (the first block starts at 2 s)
SHOTS=""
for i in 0 1 2 3; do
  base=$(echo "2 + $i * 9.5" | bc)
  SHOTS="$SHOTS ${NAMES[$i]}_rest:$(echo "$base + 1.8" | bc) ${NAMES[$i]}_talk:$(echo "$base + 4.8" | bc) ${NAMES[$i]}_card:$(echo "$base + 7.8" | bc)"
done
xvfb-run -a -s "-screen 0 800x800x24" bash -c "
  $PROG > $TMP/run.log 2>&1 &
  PID=\$!
  START=\$(date +%s.%N)
  for s in $SHOTS; do
    n=\${s%%:*}; t=\${s##*:}
    now=\$(date +%s.%N); wait_s=\$(echo \"\$t - (\$now - \$START)\" | bc)
    if (( \$(echo \"\$wait_s > 0\" | bc) )); then sleep \$wait_s; fi
    import -window root $TMP/\$n.png
  done
  kill \$PID
"
for f in $TMP/*_*.png; do
  n=$(basename "$f" .png)
  convert "$f" -resize 352x352 \( -size 352x352 xc:black -fill white -draw "circle 176,176 176,1" \) -alpha off -compose CopyOpacity -composite "$OUT/prev_$n.png"
done
ls "$OUT"
