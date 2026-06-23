#!/usr/bin/env bash
# ATDD red-phase wiring assertion — Story 9.5 (Android calls over Type3).
#
# The 9.5 red surface lives in source that CANNOT be exercised by an on-device
# unit test (the VoIPService call path needs two real devices + a live proxy —
# see the AC#5 manual smoke runbook). So, exactly like teleproto3's
# lib/tests/abi/shim_release_symbols_test.sh (story 9.2), the red marker for the
# wiring ACs is a SOURCE ASSERTION: grep the tree for the wired end-state, fail
# RED while it is absent, pass GREEN once implemented.
#
# Red-phase gate (mirrors story 9.4's ATDD_9_X_ACTIVATE env gate): with no env
# var this script REPORTS status and exits 0 (committed tree / CI stays green).
# Set ATDD_9_5_ACTIVATE=1 to ENFORCE — exits non-zero until every assertion is
# GREEN. Activation = implement AC#2/#3/#4/#6, flip the flag, drive it to 0.
#
# Source-assertable here: AC#2, AC#3, AC#4, AC#6, + an AC#1 no-WS green guard.
# NOT here: AC#5 real call (manual runbook), AC#7 regression (existing suite).
#
# Portable to BSD grep (macOS dev) and GNU grep (Linux CI): line-based ERE only,
# no -P / -z / multiline.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP="$ROOT/TMessagesProj/src/main/java/org/telegram/messenger"
VOIP="$APP/voip"
JNI="$ROOT/TMessagesProj/jni/voip"
RES="$ROOT/TMessagesProj/src/main/res"

VOIPSERVICE="$VOIP/VoIPService.java"
INSTANCE="$VOIP/Instance.java"
JNI_MAKE="$JNI/org_telegram_messenger_voip_Instance.cpp"
STRINGS_EN="$RES/values/strings.xml"
STRINGS_RU="$RES/values-ru/strings.xml"
SHIM="$APP/Type3ShimController.java"

fail=0
red() { printf '  \033[31mRED  \033[0m %s\n' "$1"; fail=$((fail + 1)); }
grn() { printf '  \033[32mGREEN\033[0m %s\n' "$1"; }

# assert_grep <human-desc> <file> <extended-regex>
assert_grep() {
  local desc="$1" file="$2" re="$3"
  if [ ! -f "$file" ]; then red "$desc — FILE MISSING: $file"; return; fi
  if grep -Eq "$re" "$file"; then grn "$desc"; else red "$desc"; fi
}

echo "ATDD 9.5 wiring assertions (root: $ROOT)"
echo
echo "AC#2 — shim lifecycle wired into VoIPService"
assert_grep "AC2a VoIPService starts the shim (Type3ShimController.start)"            "$VOIPSERVICE" 'Type3ShimController\.start\('
assert_grep "AC2b VoIPService gates the shim on isType3Secret(...)"                   "$VOIPSERVICE" 'isType3Secret\('
assert_grep "AC2c VoIPService stops the shim on teardown (Type3ShimController.stop)"  "$VOIPSERVICE" 'Type3ShimController\.stop\('

echo
echo "AC#3 — tgcalls pointed at the shim (overriding the secret-skip)"
assert_grep "AC3a VoIPService builds a 127.0.0.1 Instance.Proxy for the Type3 case"   "$VOIPSERVICE" 'new Instance\.Proxy\([^)]*127\.0\.0\.1'
assert_grep "AC3b VoIPService feeds the shim port (Type3ShimController.getPort)"       "$VOIPSERVICE" 'Type3ShimController\.getPort\('

echo
echo "AC#4 — allowTCP plumbed Java Config -> JNI -> native descriptor"
assert_grep "AC4a Instance.Config declares allowTCP"                                   "$INSTANCE"  'allowTCP'
assert_grep "AC4b JNI make() marshals allowTCP from the Java Config"                   "$JNI_MAKE"  'getBooleanField\("allowTCP"\)|\.allowTCP[[:space:]]*='

echo
echo "AC#6 — group-call clean-fail toast (RU + EN), short-circuit before UDP"
assert_grep "AC6a EN strings.xml carries a group-call-unsupported string"             "$STRINGS_EN" 'Type3GroupCall|[Gg]roup call.*not.*upport'
assert_grep "AC6b RU strings.xml carries the paired Russian string"                   "$STRINGS_RU" 'Type3GroupCall|Групповые звонки'
assert_grep "AC6c VoIPService shows the group-call-unsupported toast (string ref)"    "$VOIPSERVICE" 'Type3GroupCall|GroupCallUnsupported'

echo
echo "AC#1 — no WS upgrade on the wire (HTTP-stream only) [green guard]"
# AC#1 transport is delivered by 9-2 (lib) / 9-4 (server). At the Android layer the
# only regression we can cheaply guard is that the Type3 call path never emits a
# wss:// / WebSocket upgrade. Green today; must stay green.
if grep -Eq 'wss://|Sec-WebSocket-Key|Upgrade:[[:space:]]*websocket' "$VOIPSERVICE" "$SHIM" 2>/dev/null; then
  red "AC1 a WS-upgrade token leaked into the Type3 call path"
else
  grn "AC1 no wss:// / WS-upgrade token in VoIPService or Type3ShimController"
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "ALL GREEN — 9.5 wiring assertions satisfied."
  exit 0
fi
echo "$fail assertion(s) RED (expected before 9.5 is implemented)."
if [ "${ATDD_9_5_ACTIVATE:-0}" = "1" ]; then
  echo "ATDD_9_5_ACTIVATE=1 => ENFORCING => exit 1."
  exit 1
fi
echo "Red-phase (no ATDD_9_5_ACTIVATE) => reporting only => exit 0."
exit 0
