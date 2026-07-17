#!/usr/bin/env bash
# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC.
#
# Render an LDAP benchmark comparison report (versions + per-operation table + QuickChart charts)
# for two servers A and B to stdout — intended to be appended to $GITHUB_STEP_SUMMARY. The report
# is generic: server names, versions and images are all parameters, and benchmark-specific notes
# are NOT included here (append them separately, e.g. `cat notes.md >> $GITHUB_STEP_SUMMARY`).
#
# Usage:
#   summary.sh <A_name> <A_statistics.json> <A_version> <A_image> \
#              <B_name> <B_statistics.json> <B_version> <B_image>
#
# The admin connection bind is labelled ADMIN_CONNECT in the plan and is intentionally
# skipped here, so it never pollutes the per-operation comparison.
set -euo pipefail

A_NAME="${1:?server A name required}"
A_JSON="${2:?server A statistics.json required}"
A_VER="${3:-unknown}"
A_IMG="${4:-}"
B_NAME="${5:?server B name required}"
B_JSON="${6:?server B statistics.json required}"
B_VER="${7:-unknown}"
B_IMG="${8:-}"

A_COLOR="#4e79a7"   # blue   = server A
B_COLOR="#f28e2b"   # orange = server B

# Operations to compare, in workflow order. ADMIN_CONNECT and Total are excluded.
OPS=("ADD" "SEARCH" "COMPARE" "MODIFY" "BIND" "DELETE" "READD")

# m <file> <label> <field>  -> numeric value (0 if absent), rounded to 1 decimal.
m() { jq -r --arg l "$2" --arg f "$3" '((.[$l][$f]) // 0) | (.*10 | round / 10)' "$1"; }
# mi <file> <label> <field> -> integer value (0 if absent).
mi() { jq -r --arg l "$2" --arg f "$3" '((.[$l][$f]) // 0) | round' "$1"; }

echo "## 🔬 Benchmark: ${A_NAME} vs ${B_NAME}"
echo ""

# ---------------------------------------------------------------- Versions
echo "### Versions"
echo ""
echo "| Server | Version | Image |"
echo "|---|---|---|"
echo "| **${A_NAME}** | \`${A_VER}\` | \`${A_IMG:-n/a}\` |"
echo "| **${B_NAME}** | \`${B_VER}\` | \`${B_IMG:-n/a}\` |"
echo ""

# ---------------------------------------------------------------- Totals
a_tot_tp=$(m  "$A_JSON" Total throughput)
b_tot_tp=$(m  "$B_JSON" Total throughput)
a_tot_n=$(mi  "$A_JSON" Total sampleCount)
b_tot_n=$(mi  "$B_JSON" Total sampleCount)
a_tot_e=$(mi  "$A_JSON" Total errorCount)
b_tot_e=$(mi  "$B_JSON" Total errorCount)
a_tot_mean=$(m "$A_JSON" Total meanResTime)
b_tot_mean=$(m "$B_JSON" Total meanResTime)

echo "### Totals (all operations, ADMIN_CONNECT excluded by the plan label)"
echo ""
echo "| Server | Throughput (tests/s) | Mean (ms) | Samples | Errors |"
echo "|---|--:|--:|--:|--:|"
echo "| **${A_NAME}** | ${a_tot_tp} | ${a_tot_mean} | ${a_tot_n} | ${a_tot_e} |"
echo "| **${B_NAME}** | ${b_tot_tp} | ${b_tot_mean} | ${b_tot_n} | ${b_tot_e} |"
echo ""

# ---------------------------------------------------------------- Per-op table
echo "### Per-operation latency"
echo ""
echo "| Operation | mean ms ${A_NAME} | mean ms ${B_NAME} | p99 ms ${A_NAME} | p99 ms ${B_NAME} | err ${A_NAME} | err ${B_NAME} |"
echo "|---|--:|--:|--:|--:|--:|--:|"
for op in "${OPS[@]}"; do
  printf '| %s | %s | %s | %s | %s | %s | %s |\n' \
    "$op" \
    "$(m  "$A_JSON" "$op" meanResTime)"  "$(m  "$B_JSON" "$op" meanResTime)" \
    "$(mi "$A_JSON" "$op" pct3ResTime)"  "$(mi "$B_JSON" "$op" pct3ResTime)" \
    "$(mi "$A_JSON" "$op" errorCount)"   "$(mi "$B_JSON" "$op" errorCount)"
done
echo ""

# ---------------------------------------------------------------- Chart helpers (QuickChart)
# Mermaid xychart-beta can't do grouped bars / a legend and crowds 14 x-labels, so render proper
# grouped bar charts via QuickChart (Chart.js) as images: https://quickchart.io/chart?c=<config>.

# JSON array of the OPS labels: ["ADD","SEARCH",...].
labels_json() {
  local out="" op
  for op in "${OPS[@]}"; do out+="${out:+,}\"${op}\""; done
  printf '[%s]' "$out"
}
# Comma-joined values for all OPS from <file> <field> via the <m> helper.
vals() { # <fn> <file> <field>
  local fn="$1" file="$2" field="$3" out="" v
  for op in "${OPS[@]}"; do v=$("$fn" "$file" "$op" "$field"); out+="${out:+,}${v}"; done
  printf '%s' "$out"
}
urienc() { jq -rn --arg s "$1" '$s|@uri'; }                       # URL-encode the chart config
qc() { printf 'https://quickchart.io/chart?w=%s&h=%s&c=%s' "$1" "$2" "$(urienc "$3")"; }

# ---------------------------------------------------------------- Total throughput chart
echo "### Total throughput (tests/s, higher is better)"
echo ""
echo "_Per-operation throughput is not charted: every op runs once per loop iteration, so each"
echo "op's throughput just equals the loop rate. The meaningful throughput is the aggregate._"
echo ""
TP_CFG="{\"type\":\"bar\",\"data\":{\"labels\":[\"${A_NAME}\",\"${B_NAME}\"],\"datasets\":[{\"label\":\"tests/s\",\"backgroundColor\":[\"$A_COLOR\",\"$B_COLOR\"],\"data\":[${a_tot_tp},${b_tot_tp}]}]},\"options\":{\"legend\":{\"display\":false},\"title\":{\"display\":true,\"text\":\"Total throughput (tests/s)\"}}}"
echo "![Total throughput (tests/s)]($(qc 500 320 "$TP_CFG"))"
echo ""

# ---------------------------------------------------------------- Latency chart (grouped bars)
echo "### p99 latency per operation (ms, lower is better)"
echo ""
echo "_🟦 ${A_NAME} · 🟧 ${B_NAME} — grouped bars per operation._"
echo ""
LAT_CFG="{\"type\":\"bar\",\"data\":{\"labels\":$(labels_json),\"datasets\":[{\"label\":\"${A_NAME}\",\"backgroundColor\":\"$A_COLOR\",\"data\":[$(vals mi "$A_JSON" pct3ResTime)]},{\"label\":\"${B_NAME}\",\"backgroundColor\":\"$B_COLOR\",\"data\":[$(vals mi "$B_JSON" pct3ResTime)]}]},\"options\":{\"title\":{\"display\":true,\"text\":\"p99 latency per operation (ms)\"}}}"
echo "![p99 latency per operation (ms)]($(qc 900 400 "$LAT_CFG"))"
echo ""
