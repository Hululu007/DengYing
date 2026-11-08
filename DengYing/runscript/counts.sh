#!/bin/sh
cd /d/workspace/dengY/DengYing || exit 1
for d in shared/build/test-results/jvmTest \
         shared/build/test-results/testDebugUnitTest \
         app/build/test-results/testDebugUnitTest; do
  awk '
    match($0,/tests="[0-9]+"/){ t+=substr($0,RSTART+7,RLENGTH-8) }
    match($0,/failures="[0-9]+"/){ f+=substr($0,RSTART+10,RLENGTH-11) }
    match($0,/errors="[0-9]+"/){ e+=substr($0,RSTART+8,RLENGTH-9) }
    match($0,/skipped="[0-9]+"/){ s+=substr($0,RSTART+9,RLENGTH-10) }
    END{ printf "%-46s tests=%d failures=%d errors=%d skipped=%d\n", D, t, f, e, s }
  ' D="$d" "$d"/*.xml
done
echo
echo "=== APK ==="
ls -l app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk \
  | awk '{printf "%-60s %s bytes\n", $NF, $5}'
