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

# The descriptor survives the redirections and command substitutions of join.sh. Only
# the step name and result are logged: command arguments can contain credentials.
join_timing_init() {
  [ "${JOIN_TIMING:-0}" != 1 ] || exec 9>&2
}

join_timed() { # <step> <command> [<argument>...]
  local __join_step=$1 __join_rc
  shift
  if [ "${JOIN_TIMING:-0}" != 1 ]; then "$@"; return $?; fi
  printf 'join-timing event=start pid=%s step=%s seconds=%s\n' "${BASHPID:-$$}" "$__join_step" "$SECONDS" >&9 || :
  if "$@"; then __join_rc=0; else __join_rc=$?; fi
  printf 'join-timing event=end pid=%s step=%s seconds=%s status=%s\n' "${BASHPID:-$$}" "$__join_step" "$SECONDS" "$__join_rc" >&9 || :
  return "$__join_rc"
}
