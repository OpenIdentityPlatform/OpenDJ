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
# Render an OpenDJ-vs-OpenLDAP LDAP benchmark report (versions + comparison table +
# comparative Mermaid charts) to stdout — intended to be appended to $GITHUB_STEP_SUMMARY.
#
# Usage:
#   summary.sh <openldap_statistics.json> <opendj_statistics.json> \
#              <openldap_version> <opendj_version> [openldap_image] [opendj_image]
#
# The admin connection bind is labelled ADMIN_CONNECT in the plan and is intentionally
# skipped here, so it never pollutes the per-operation comparison.
set -euo pipefail

OL_JSON="${1:?openldap statistics.json required}"
DJ_JSON="${2:?opendj statistics.json required}"
OL_VER="${3:-unknown}"
DJ_VER="${4:-unknown}"
OL_IMG="${5:-}"
DJ_IMG="${6:-}"

# Operations to compare, in workflow order. ADMIN_CONNECT and Total are excluded.
OPS=("ADD" "SEARCH" "COMPARE" "MODIFY" "BIND" "DELETE" "READD")

# m <file> <label> <field>  -> numeric value (0 if absent), rounded to 1 decimal.
m() { jq -r --arg l "$2" --arg f "$3" '((.[$l][$f]) // 0) | (.*10 | round / 10)' "$1"; }
# mi <file> <label> <field> -> integer value (0 if absent).
mi() { jq -r --arg l "$2" --arg f "$3" '((.[$l][$f]) // 0) | round' "$1"; }

echo "## 🔬 Benchmark: OpenDJ vs OpenLDAP"
echo ""

# ---------------------------------------------------------------- Versions
echo "### Versions"
echo ""
echo "| Server | Version | Image |"
echo "|---|---|---|"
echo "| **OpenLDAP** | \`${OL_VER}\` | \`${OL_IMG:-n/a}\` |"
echo "| **OpenDJ** | \`${DJ_VER}\` | \`${DJ_IMG:-n/a}\` |"
echo ""

# ---------------------------------------------------------------- Totals
ol_tot_tp=$(m  "$OL_JSON" Total throughput)
dj_tot_tp=$(m  "$DJ_JSON" Total throughput)
ol_tot_n=$(mi  "$OL_JSON" Total sampleCount)
dj_tot_n=$(mi  "$DJ_JSON" Total sampleCount)
ol_tot_e=$(mi  "$OL_JSON" Total errorCount)
dj_tot_e=$(mi  "$DJ_JSON" Total errorCount)
ol_tot_mean=$(m "$OL_JSON" Total meanResTime)
dj_tot_mean=$(m "$DJ_JSON" Total meanResTime)

echo "### Totals (all operations, ADMIN_CONNECT excluded by the plan label)"
echo ""
echo "| Server | Throughput (ops/s) | Mean (ms) | Samples | Errors |"
echo "|---|--:|--:|--:|--:|"
echo "| **OpenLDAP** | ${ol_tot_tp} | ${ol_tot_mean} | ${ol_tot_n} | ${ol_tot_e} |"
echo "| **OpenDJ** | ${dj_tot_tp} | ${dj_tot_mean} | ${dj_tot_n} | ${dj_tot_e} |"
echo ""

# ---------------------------------------------------------------- Per-op table
echo "### Per-operation latency"
echo ""
echo "| Operation | mean ms OpenLDAP | mean ms OpenDJ | p99 ms OpenLDAP | p99 ms OpenDJ | err OL | err DJ |"
echo "|---|--:|--:|--:|--:|--:|--:|"
for op in "${OPS[@]}"; do
  printf '| %s | %s | %s | %s | %s | %s | %s |\n' \
    "$op" \
    "$(m  "$OL_JSON" "$op" meanResTime)"  "$(m  "$DJ_JSON" "$op" meanResTime)" \
    "$(mi "$OL_JSON" "$op" pct3ResTime)"  "$(mi "$DJ_JSON" "$op" pct3ResTime)" \
    "$(mi "$OL_JSON" "$op" errorCount)"   "$(mi "$DJ_JSON" "$op" errorCount)"
done
echo ""

# ---------------------------------------------------------------- Chart helpers (QuickChart)
# Mermaid xychart-beta can't do grouped bars / a legend and crowds 14 x-labels, so render proper
# grouped bar charts via QuickChart (Chart.js) as images: https://quickchart.io/chart?c=<config>.
OL_COLOR="#4e79a7"   # blue   = OpenLDAP
DJ_COLOR="#f28e2b"   # orange = OpenDJ

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
echo "### Total throughput (ops/s, higher is better)"
echo ""
echo "_Per-operation throughput is not charted: every op runs once per loop iteration, so each"
echo "op's throughput just equals the loop rate. The meaningful throughput is the aggregate._"
echo ""
TP_CFG="{\"type\":\"bar\",\"data\":{\"labels\":[\"OpenLDAP\",\"OpenDJ\"],\"datasets\":[{\"label\":\"ops/s\",\"backgroundColor\":[\"$OL_COLOR\",\"$DJ_COLOR\"],\"data\":[${ol_tot_tp},${dj_tot_tp}]}]},\"options\":{\"legend\":{\"display\":false},\"title\":{\"display\":true,\"text\":\"Total throughput (ops/s)\"}}}"
echo "![Total throughput (ops/s)]($(qc 500 320 "$TP_CFG"))"
echo ""

# ---------------------------------------------------------------- Latency chart (grouped bars)
echo "### p99 latency per operation (ms, lower is better)"
echo ""
echo "_🟦 OpenLDAP · 🟧 OpenDJ — grouped bars per operation._"
echo ""
LAT_CFG="{\"type\":\"bar\",\"data\":{\"labels\":$(labels_json),\"datasets\":[{\"label\":\"OpenLDAP\",\"backgroundColor\":\"$OL_COLOR\",\"data\":[$(vals mi "$OL_JSON" pct3ResTime)]},{\"label\":\"OpenDJ\",\"backgroundColor\":\"$DJ_COLOR\",\"data\":[$(vals mi "$DJ_JSON" pct3ResTime)]}]},\"options\":{\"title\":{\"display\":true,\"text\":\"p99 latency per operation (ms)\"}}}"
echo "![p99 latency per operation (ms)]($(qc 900 400 "$LAT_CFG"))"
echo ""

# ---------------------------------------------------------------- Caveats
echo "### Notes"
echo ""
echo "- \`BIND\` is the measured **user authentication** (\`test=sbind\`, single bind/unbind on its"
echo "  own connection) as \`cn=user_<n>,ou=People\` with the password set by \`MODIFY\`. The admin"
echo "  connection bind (\`ADMIN_CONNECT\`) is cached once per thread and excluded from these results."
echo "- MODIFY sends the password in cleartext; each server hashes it on write with the **same"
echo "  scheme (SSHA, Salted SHA-1)** — OpenLDAP via the ppolicy hash-cleartext overlay, OpenDJ via"
echo "  its Salted SHA-1 default scheme — so BIND authentication is compared on equal footing."
echo "- Full interactive JMeter HTML dashboards are attached as the \`jmeter-reports\` artifact."
