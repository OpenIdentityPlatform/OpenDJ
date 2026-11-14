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

# One sequential worker per join. Every request still creates a fresh LDAP connection;
# no configuration or membership result survives a request. Private FIFOs and base64
# framing preserve empty arguments, whitespace and LDIF without evaluating their contents.
join_search_init() {
  [ "${JOIN_SEARCH_REUSE:-1}" = 1 ] || return 0
  JOIN_SEARCH_DIR=$(mktemp -d "${TMPDIR:-/tmp}/opendj-join-search.XXXXXX") || return 1
  mkfifo "$JOIN_SEARCH_DIR/request" "$JOIN_SEARCH_DIR/response" || return 1
  (
    trap - EXIT
    # Open both pipe ends before setup: a failed setup then produces EOF, not a
    # parent blocked forever opening a FIFO which no child will ever open.
    exec 7<"$JOIN_SEARCH_DIR/request" 8>"$JOIN_SEARCH_DIR/response"
    INSTALL_ROOT=${JOIN_INSTALL_ROOT:-/opt/opendj}
    SCRIPT_NAME=ldapsearch SCRIPT_UTIL_CMD=set-full-environment
    export INSTALL_ROOT SCRIPT_NAME SCRIPT_UTIL_CMD
    . "$INSTALL_ROOT/lib/_script-util.sh" || exit 1
    # Preserve the same JVM flags, class path, trust and bind behavior as bin/ldapsearch.
    exec "$OPENDJ_JAVA_BIN" ${OPENDJ_JAVA_ARGS:-} ${SCRIPT_ARGS:-} ${SCRIPT_NAME_ARG:-} \
      com.forgerock.opendj.ldap.tools.BootstrapSearch <&7 >&8 7<&- 8>&-
  ) &
  JOIN_SEARCH_PID=$!
  exec 7>"$JOIN_SEARCH_DIR/request" 8<"$JOIN_SEARCH_DIR/response"
}

join_search_close() {
  [ -n "${JOIN_SEARCH_DIR:-}" ] || return 0
  exec 7>&- 8<&-
  if [ -n "${JOIN_SEARCH_PID:-}" ]; then
    kill "$JOIN_SEARCH_PID" 2>/dev/null || :
    wait "$JOIN_SEARCH_PID" 2>/dev/null || :
  fi
  rm -rf "$JOIN_SEARCH_DIR"
  JOIN_SEARCH_DIR= JOIN_SEARCH_PID=
}

join_search() {
  local request status response
  if [ -n "${JOIN_SEARCH_DIR:-}" ] && [ ! -e "$JOIN_SEARCH_DIR/disabled" ]; then
    request=$(printf '%s\0' "$@" | PATH=/usr/bin:/bin base64 | tr -d '\n') || return 1
    # A timeout disables this worker for every later command substitution too: an
    # eventual response must never be mistaken for the response to another search.
    if (trap '' PIPE; printf '%s\n' "$request" >&7) \
        && IFS=' ' read -r -t "${REPLICATION_ATTEMPT_TIMEOUT:-120}" status response <&8; then
      case $status in
        ''|*[!0-9]*) ;;
        *)
          if [ "$status" -le 255 ] && printf '%s' "$response" | PATH=/usr/bin:/bin base64 -d >"$JOIN_SEARCH_DIR/result"; then
            cat "$JOIN_SEARCH_DIR/result" || return 1
            return "$status"
          fi
          ;;
      esac
    fi
    : >"$JOIN_SEARCH_DIR/disabled"
    kill "$JOIN_SEARCH_PID" 2>/dev/null || :
  fi
  # An older distribution may not contain the worker. Reads are safe to retry,
  # and genuine LDAP result codes above never take this fallback.
  ldapsearch "$@"
}
