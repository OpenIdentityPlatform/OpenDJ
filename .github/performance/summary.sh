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
OPS=("ADD" "SEARCH" "COMPARE" "MODIFY" "BIND" "DELETE" "ADD WITHOUT DELETE")

# m <file> <label> <field>  -> numeric value (0 if absent), rounded to 1 decimal.
m() { jq -r --arg l "$2" --arg f "$3" '((.[$l][$f]) // 0) | (.*10 | round / 10)' "$1"; }
# mi <file> <label> <field> -> integer value (0 if absent).
mi() { jq -r --arg l "$2" --arg f "$3" '((.[$l][$f]) // 0) | round' "$1"; }

echo "## 🔬 LDAP Benchmark — OpenDJ vs OpenLDAP"
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
echo "### Per-operation comparison"
echo ""
echo "| Operation | ops/s OpenLDAP | ops/s OpenDJ | mean ms OpenLDAP | mean ms OpenDJ | p99 ms OpenLDAP | p99 ms OpenDJ | err OL | err DJ |"
echo "|---|--:|--:|--:|--:|--:|--:|--:|--:|"
for op in "${OPS[@]}"; do
  printf '| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n' \
    "$op" \
    "$(m  "$OL_JSON" "$op" throughput)"   "$(m  "$DJ_JSON" "$op" throughput)" \
    "$(m  "$OL_JSON" "$op" meanResTime)"  "$(m  "$DJ_JSON" "$op" meanResTime)" \
    "$(mi "$OL_JSON" "$op" pct3ResTime)"  "$(mi "$DJ_JSON" "$op" pct3ResTime)" \
    "$(mi "$OL_JSON" "$op" errorCount)"   "$(mi "$DJ_JSON" "$op" errorCount)"
done
echo ""

# ---------------------------------------------------------------- Chart helpers
# Build a Mermaid list "x, y, z" of values for all OPS from <file> <field>, via <m|mi>.
series() { # <fn> <file> <field>
  local fn="$1" file="$2" field="$3" out="" v
  for op in "${OPS[@]}"; do
    v=$("$fn" "$file" "$op" "$field")
    out+="${out:+, }${v}"
  done
  printf '%s' "$out"
}
# x-axis with every label quoted (labels contain spaces).
xaxis() {
  local out="" op
  for op in "${OPS[@]}"; do out+="${out:+, }\"${op}\""; done
  printf '%s' "$out"
}

XAXIS="$(xaxis)"

# ---------------------------------------------------------------- Throughput chart
echo "### Comparative chart — throughput per operation (ops/s, higher is better)"
echo ""
echo "_First bar series = OpenLDAP, second bar series = OpenDJ._"
echo ""
echo '```mermaid'
echo "xychart-beta"
echo "    title \"Throughput per operation (ops/s) — OpenLDAP vs OpenDJ\""
echo "    x-axis [${XAXIS}]"
echo "    y-axis \"ops/s\""
echo "    bar [$(series m "$OL_JSON" throughput)]"
echo "    bar [$(series m "$DJ_JSON" throughput)]"
echo '```'
echo ""

# ---------------------------------------------------------------- Latency chart
echo "### Comparative chart — mean latency per operation (ms, lower is better)"
echo ""
echo "_First bar series = OpenLDAP, second bar series = OpenDJ._"
echo ""
echo '```mermaid'
echo "xychart-beta"
echo "    title \"Mean latency per operation (ms) — OpenLDAP vs OpenDJ\""
echo "    x-axis [${XAXIS}]"
echo "    y-axis \"ms\""
echo "    bar [$(series m "$OL_JSON" meanResTime)]"
echo "    bar [$(series m "$DJ_JSON" meanResTime)]"
echo '```'
echo ""

# ---------------------------------------------------------------- Caveats
echo "### Notes"
echo ""
echo "- \`BIND\` is the measured **user authentication** (\`test=sbind\`, single bind/unbind on its"
echo "  own connection) as \`cn=user_<n>,ou=People\` with the password set by \`MODIFY\`. The admin"
echo "  connection bind (\`ADMIN_CONNECT\`) is cached once per thread and excluded from these results."
echo "- Default password storage scheme differs by server (OpenDJ PBKDF2/Salted-SHA vs OpenLDAP"
echo "  SSHA); this is a legitimate part of authentication cost."
echo "- Full interactive JMeter HTML dashboards are attached as the \`jmeter-reports\` artifact."
