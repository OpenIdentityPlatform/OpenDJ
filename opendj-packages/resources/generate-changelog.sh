#!/usr/bin/env bash
#
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
# information: "Portions Copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC
#
# Regenerates the Debian and RPM package changelogs from the GitHub Releases of
# OpenIdentityPlatform/OpenDJ. Run this at release time (it needs network + an
# authenticated `gh`); the produced files are committed so the Maven build stays
# offline and reproducible.
#
# Usage (from the repository root):
#   opendj-packages/resources/generate-changelog.sh
#
# Requires: gh (authenticated), python3.

set -euo pipefail

REPO="${OPENDJ_REPO:-OpenIdentityPlatform/OpenDJ}"
HERE="$(cd "$(dirname "$0")" && pwd)"
DEB_FILE="${HERE}/../opendj-deb/resources/changelog"
RPM_FILE="${HERE}/../opendj-rpm/resources/changelog"

echo "Fetching releases from ${REPO} ..." >&2
RELEASES_TMP="$(mktemp)"
trap 'rm -f "$RELEASES_TMP"' EXIT
gh api --paginate "repos/${REPO}/releases" > "$RELEASES_TMP" 2>/dev/null

DEB_FILE="${DEB_FILE}" RPM_FILE="${RPM_FILE}" RELEASES_TMP="${RELEASES_TMP}" REPO="${REPO}" python3 - <<'PY'
import json, os, re, sys, textwrap

with open(os.environ["RELEASES_TMP"]) as _f:
    releases = json.load(_f)

MAINTAINER = "Open Identity Platform Community <open-identity-platform-opendj@googlegroups.com>"
DOW = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
MON = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
       "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

def parse_iso(ts):
    # e.g. 2026-06-11T19:19:48Z -> (Y, M, D, h, m, s, weekday)
    import datetime
    dt = datetime.datetime.strptime(ts, "%Y-%m-%dT%H:%M:%SZ")
    return dt

def clean_bullets(body):
    bullets = []
    for raw in (body or "").splitlines():
        line = raw.strip()
        if not line.startswith(("* ", "- ")):
            continue
        line = line[2:].strip()
        line = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", line)  # md link -> text
        line = re.sub(r"\bin https?://\S+", "", line)        # drop PR url
        line = re.sub(r"https?://\S+", "", line)             # drop bare urls
        line = re.sub(r"\b(by|thanks)\s+@[\w-]+(\[bot\])?", "", line)  # drop "by/thanks @author"
        line = re.sub(r"@[\w-]+(\[bot\])?", "", line)        # drop any leftover @mention
        line = line.replace("**", "").replace("`", "")
        line = re.sub(r"[←-➿️❤☀-⛿]", "", line)  # emoji/hearts
        line = re.sub(r"\s+", " ", line).strip(" -")
        if line:
            bullets.append(line)
    return bullets

def version_of(rel):
    return (rel.get("tag_name") or rel.get("name") or "").lstrip("v").strip()

deb_chunks, rpm_chunks = [], []
for rel in releases:
    if rel.get("draft"):
        continue
    ver = version_of(rel)
    if not ver or not ver[0].isdigit():
        continue
    dt = parse_iso(rel["published_at"])
    bullets = clean_bullets(rel.get("body")) or [
        "See release notes: https://github.com/%s/releases/tag/%s"
        % (os.environ.get("REPO", "OpenIdentityPlatform/OpenDJ"), ver)
    ]

    # --- Debian stanza ---
    deb = ["opendj (%s) unstable; urgency=medium" % ver, ""]
    for b in bullets:
        wrapped = textwrap.fill(b, width=78, initial_indent="  * ",
                                subsequent_indent="    ")
        deb.append(wrapped)
    deb_date = "%s, %02d %s %d %02d:%02d:%02d +0000" % (
        DOW[dt.weekday()], dt.day, MON[dt.month - 1], dt.year,
        dt.hour, dt.minute, dt.second)
    deb.append("")
    deb.append(" -- %s  %s" % (MAINTAINER, deb_date))
    deb_chunks.append("\n".join(deb))

    # --- RPM stanza ---
    rpm_date = "%s %s %2d %d" % (DOW[dt.weekday()], MON[dt.month - 1], dt.day, dt.year)
    rpm = ["* %s %s - %s" % (rpm_date, MAINTAINER, ver)]
    for b in bullets:
        rpm.append(textwrap.fill(b, width=78, initial_indent="- ",
                                 subsequent_indent="  "))
    rpm_chunks.append("\n".join(rpm))

with open(os.environ["DEB_FILE"], "w") as f:
    f.write("\n\n".join(deb_chunks) + "\n")

RPM_PREAMBLE = """#
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
# information: "Portions Copyright [year] [name of copyright owner]".
#
# Copyright 2013-2026 ForgeRock AS and Open Identity Platform Community.

# =============================
# opendj rpm package changelog
# =============================

%changelog
"""
with open(os.environ["RPM_FILE"], "w") as f:
    f.write(RPM_PREAMBLE + "\n".join(rpm_chunks) + "\n")

print("Wrote %d releases to:\n  %s\n  %s"
      % (len(deb_chunks), os.environ["DEB_FILE"], os.environ["RPM_FILE"]),
      file=sys.stderr)
PY
