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
. "$HERE/lib.sh"
. "$HERE/topology-up.sh"
. "$HERE/assert-converged.sh"

mkdir -p "$OUT"
# set -E hands the trap to every $(...) as well: there a failure only ends the subshell, and
# the main shell decides
trap 'code=$?; [ "$BASH_SUBSHELL" -eq 0 ] || exit $code; timings; [ "$KEEP" = 1 ] || cleanup; exit $code' ERR
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

if [ "$INJECT_DIVERGENCE" = 1 ]; then
  phase "inject a divergence"
  inject_divergence
fi

phase "online checks"
assert_online

phase "offline checks"
stop_all
assert_offline

timings
cleanup
