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

# Best-effort diagnostics; the caller retains the original test exit code.
set -o pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/replication/collect.sh"
node=${1:?container name required}
out=${2:?output directory required}
mkdir -p "$out" || exit 1
docker inspect --format '{{json .State}}' "$node" >"$out/state.json" 2>&1 || true
docker logs --timestamps "$node" >"$out/container.log" 2>&1 || true
docker cp "$node:/opt/opendj/data/logs" "$out/" >/dev/null 2>&1 || true
collect_tool_logs "$node" "$out/tool-logs" || true
docker cp "$node:/opt/opendj/.bootstrap-failed" "$out/bootstrap-failed" >/dev/null 2>&1 || true
docker exec "$node" sh -c '
  for file in /sys/fs/cgroup/memory.events /sys/fs/cgroup/memory.events.local \
      /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.max \
      /sys/fs/cgroup/memory.swap.current /sys/fs/cgroup/memory.swap.peak /sys/fs/cgroup/memory.swap.max \
      /sys/fs/cgroup/memory/memory.oom_control /sys/fs/cgroup/memory/memory.failcnt \
      /sys/fs/cgroup/memory/memory.memsw.usage_in_bytes /sys/fs/cgroup/memory/memory.memsw.limit_in_bytes \
      /proc/1/status; do
    if [ -r "$file" ]; then printf "%s\n" "$file"; cat "$file"; fi
  done
  ps -eo pid,ppid,rss,comm 2>/dev/null || true
' >"$out/processes-and-memory.txt" 2>&1 || true
printf '::group::Docker diagnostics (%s)\n' "$node"
cat "$out/state.json" "$out/processes-and-memory.txt"
[ ! -f "$out/logs/start-ds.log" ] || cat "$out/logs/start-ds.log"
[ ! -f "$out/logs/errors" ] || tail -n 1000 "$out/logs/errors"
for file in "$out"/tool-logs/*.log; do
  [ ! -f "$file" ] || { printf '%s\n' "$file"; cat "$file"; }
done
tail -n 100 "$out/container.log"
printf '::endgroup::\n'
