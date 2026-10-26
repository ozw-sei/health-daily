#!/usr/bin/env bash
# Map a production tag vX.Y.Z to versionName and versionCode.
# Ranges are checked before versionCode is computed:
#   X 0..2099, Y 0..99, Z 0..99.
# A final release uses suffix 99. This app does not publish vX.Y.Z-beta.N
# (that form would use N in 1..98). versionCode must be 1..2100000000.
#
# Usage:
#   scripts/release-version.sh v1.2.3
#   scripts/release-version.sh --self-test
set -euo pipefail

version_from_tag() {
  local tag="$1"
  local major minor patch version_code version_name

  if [[ ! "$tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    echo "::error::This app publishes a normal release from tag vX.Y.Z only (got '${tag}'). vX.Y.Z-beta.N is not used here." >&2
    return 1
  fi
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"

  if (( 10#$major > 2099 )); then
    echo "::error::Tag ${tag} major ${major} is outside 0..2099." >&2
    return 1
  fi
  if (( 10#$minor > 99 )); then
    echo "::error::Tag ${tag} minor ${minor} is outside 0..99." >&2
    return 1
  fi
  if (( 10#$patch > 99 )); then
    echo "::error::Tag ${tag} patch ${patch} is outside 0..99." >&2
    return 1
  fi

  version_code=$((10#$major * 1000000 + 10#$minor * 10000 + 10#$patch * 100 + 99))
  if (( version_code <= 0 || version_code > 2100000000 )); then
    echo "::error::versionCode ${version_code} for ${tag} must be from 1 through 2100000000." >&2
    return 1
  fi
  version_name="${tag#v}"
  printf '%s %s\n' "$version_name" "$version_code"
}

self_test() {
  local out err
  err="$(mktemp)"
  if out="$(version_from_tag "v1.0.100" 2>"$err")"; then
    echo "self-test: v1.0.100 should have been rejected" >&2
    rm -f "$err"
    return 1
  fi
  if ! grep -q "outside 0..99" "$err"; then
    echo "self-test: v1.0.100 rejection was not clear:" >&2
    cat "$err" >&2
    rm -f "$err"
    return 1
  fi
  rm -f "$err"

  out="$(version_from_tag "v0.0.0")"
  if [[ "$out" != "0.0.0 99" ]]; then
    echo "self-test: v0.0.0 produced '${out}', expected '0.0.0 99'" >&2
    return 1
  fi
  out="$(version_from_tag "v1.2.3")"
  if [[ "$out" != "1.2.3 1020399" ]]; then
    echo "self-test: v1.2.3 produced '${out}', expected '1.2.3 1020399'" >&2
    return 1
  fi
  echo "release version self-test ok"
}

if [[ "${1:-}" == "--self-test" ]]; then
  self_test
elif [[ $# -eq 1 ]]; then
  version_from_tag "$1"
else
  echo "usage: $0 vX.Y.Z | --self-test" >&2
  exit 2
fi
