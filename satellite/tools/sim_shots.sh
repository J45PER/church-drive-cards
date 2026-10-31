#!/usr/bin/env bash
# Runs the host simulator under a virtual display and saves a screenshot of each step of sim.yaml's tour.
# Needs: xvfb, imagemagick, libsdl2-dev (to build). Usage: tools/sim_shots.sh <outdir>   (after `esphome compile sim.yaml`)
set -euo pipefail
OUT=${1:-sim-shots}
mkdir -p "$OUT"
PROG=$(ls .esphome/build/clive-sim/.pioenvs/clive-sim/program)
# name:seconds after start (the tour changes something every 3 s; each shot is 1.8 s after a change)
SHOTS="glance:4.8 clock:7.8 weather:10.8 ring:13.8 listen:16.8 think:19.8 speak:22.8 card-weather:25.8 card-light:28.8 card-ask:31.8 idle:34.5 shade:37.5 menu:40.5 pair:43.5"
xvfb-run -a -s "-screen 0 800x800x24" bash -c "
  $PROG > $OUT/run.log 2>&1 &
  PID=\$!
  START=\$(date +%s.%N)
  for s in $SHOTS; do
    n=\${s%%:*}; t=\${s##*:}
    now=\$(date +%s.%N); wait_s=\$(echo \"\$t - (\$now - \$START)\" | bc)
    if (( \$(echo \"\$wait_s > 0\" | bc) )); then sleep \$wait_s; fi
    import -window root $OUT/\$n.png
  done
  kill \$PID
"
ls "$OUT"
