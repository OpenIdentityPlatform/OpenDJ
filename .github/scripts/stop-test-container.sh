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

step_status=$1
container_id=$2

if stop_output=$(docker stop "$container_id" 2>&1); then
  exit "$step_status"
else
  stop_status=$?
fi

# Docker may have already removed a container started with --rm.
if running=$(docker ps --quiet --filter "id=$container_id") && [[ -z "$running" ]]; then
  exit "$step_status"
fi

printf '%s\n' "$stop_output" >&2
if (( step_status == 0 )); then
  exit "$stop_status"
fi
exit "$step_status"
