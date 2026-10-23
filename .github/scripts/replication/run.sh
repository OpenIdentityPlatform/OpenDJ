#!/usr/bin/env bash
#
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
# information: "Portions copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC.

# Writes to every directory server of a replication topology at once, then checks that the
# replicas hold the same data (#1090). The Docker image jobs of build.yml hand their image to
# the job that runs it; it runs as well on a laptop with Docker alone.
#
# Usage: run.sh <image>; the settings and their defaults are in lib.sh

set -eE -o pipefail

IMAGE=${1:?usage: $0 <image>}
HERE=$(cd "$(dirname "$0")" && pwd)
# changelog-gaps.py reads the changelogs of the replication servers
command -v python3 >/dev/null || { echo "run.sh needs python3" >&2; exit 2; }
. "$HERE/lib.sh"
. "$HERE/topology-up.sh"
. "$HERE/monitor.sh"
. "$HERE/load.sh"
. "$HERE/assert-converged.sh"
. "$HERE/collect.sh"
if [ "$DISTURBANCE" != none ]; then
  [ -f "$HERE/disturb-$DISTURBANCE.sh" ] \
    || { echo "unknown DISTURBANCE $DISTURBANCE: none, or the <name> of a disturb-<name>.sh in $HERE" >&2; exit 2; }
  [ "$DISTURB_AT" -gt 0 ] && [ "$DISTURB_AT" -lt "$DURATION" ] \
    || { echo "DISTURB_AT=$DISTURB_AT is not within the load of $DURATION s" >&2; exit 2; }
  . "$HERE/disturb-$DISTURBANCE.sh"
fi

# the files of an earlier run would mix into this one's: the samples are appended to, and the
# summary of a run that failed early would show what a run that got further left
if [ -n "$(ls -A "$OUT" 2>/dev/null)" ]; then
  echo "$OUT holds the files of an earlier run: remove it, or set OUT to another directory" >&2
  exit 2
fi
mkdir -p "$OUT"
# set -E hands the trap to every $(...) as well: there a failure only ends the subshell, and
# the main shell decides. A run that fails before the online checks - a load check, a wait
# that timed out, which a reconnection under load would explain - counts the reconnections
# while the servers are still up
trap 'code=$?; [ "$BASH_SUBSHELL" -eq 0 ] || exit $code; sampler_stop; [ -f "$OUT/reconnections.txt" ] || [ ! -f "$OUT/logs/${DS_NODES%% *}.mark" ] || record_reconnections || true; timings; collect; summarize failed; [ "$KEEP" = 1 ] || cleanup; exit $code' ERR
cleanup

phase "topology up ($TOPOLOGY)"
topology_up
for n in $RS_NODES; do
  runs_replication_server "$n" || fail "$n runs no replication server"
done
for n in $DS_NODES; do
  case " $RS_NODES " in
    *" $n "*) ;;
    *) ! runs_replication_server "$n" || fail "$n runs a replication server although its role is directory" ;;
  esac
done
for n in $DS_NODES; do
  users=$(ldaps_search "$n" "ou=People,$BASE_DN" one "(objectClass=inetOrgPerson)" 1.1 | grep -c '^dn:' || true)
  [ "$users" -eq "$SAMPLE_DATA" ] || fail "$n holds $users of the $SAMPLE_DATA seeded users"
done

mark_logs
record_server_ids

phase "load ($DURATION s${DISTURBED:+, $DISTURBANCE of $DISTURBED at $DISTURB_AT s})"
# the changes of the load: assert_changelogs looks for gaps among the changes made in between
LOAD_START_MS=$(($(date +%s) * 1000))
sampler_start
run_load
sampler_stop

phase "converge"
load_end=$SECONDS
# a disturbed server is left as topology_up leaves every server, and the wait for convergence
# still counts from the end of the load
[ "$DISTURBANCE" = none ] || settle
wait_converged $load_end
echo "converged in $CONVERGED_IN s"
LOAD_END_MS=$(($(date +%s) * 1000))

if [ "$INJECT_DIVERGENCE" = 1 ]; then
  phase "inject a divergence"
  inject_divergence
fi

# the offline checks run after online checks that failed as well: they tell which changes are
# missing where
phase "online checks"
checks=
assert_online || checks="the online checks"

phase "offline checks"
stop_all
assert_offline || checks="${checks:+$checks and }the offline checks"
[ -z "$checks" ] || fail "$checks failed"

timings
summarize passed
cleanup
