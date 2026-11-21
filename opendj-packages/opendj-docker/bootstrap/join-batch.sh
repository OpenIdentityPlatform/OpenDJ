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

# dsconfig's batch parser accepts shell-style quotes but does not expand shell syntax.
join_batch_quote() {
  local rest=$1 prefix
  printf "'"
  while [[ $rest == *"'"* ]]; do
    prefix=${rest%%\'*}
    printf "%s'\\\\''" "$prefix"
    rest=${rest#*\'}
  done
  printf "%s'" "$rest"
}

# A batch stops at its first error. If it fails, retry each change separately, including
# already-applied ones: the previous code ignored each change's error and continued.
join_batch_individual() {
  while [ "$#" -ge 4 ]; do
    change_replication_servers "$1" "$2" "$3" "$4" || :
    shift 4
  done
}

join_batch_changes() { # repeated <kind> <domain cn> <--add|--remove> <host:port>
  local batch= command name value unsafe=
  local -a changes=("$@")
  [ "$#" -gt 0 ] || return 0
  [ $(( $# % 4 )) -eq 0 ] || return 1
  [ "$#" -gt 4 ] || { join_batch_individual "$@"; return; }
  while [ "$#" -ge 4 ]; do
    case "$1$2$3$4" in *$'\n'*|*$'\r'*) unsafe=1 ;; esac
    name=$(join_batch_quote "$2")
    value=$(join_batch_quote "replication-server:$4")
    if [ "$1" = server ]; then
      command="set-replication-server-prop --provider-name 'Multimaster Synchronization' $3 $value"
    else
      command="set-replication-domain-prop --provider-name 'Multimaster Synchronization' --domain-name $name $3 $value"
    fi
    batch="$batch$command"$'\n'
    shift 4
  done
  if [ -z "$unsafe" ] && dsconfig_local --batch <<<"$batch"; then return 0; fi
  join_batch_individual "${changes[@]}"
}
