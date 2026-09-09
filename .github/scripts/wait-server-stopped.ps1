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

# Verify a stop took effect before moving on: wait until the server releases the exclusive
# byte-range lock it holds on locks\server.lock. Checking the exit code of stop-ds is not a
# substitute - #768 was exactly the case where winlauncher.exe reported success without
# having stopped the server - and starting the service on a lock the old JVM still holds
# fails in ways that look like flakiness.
#
# The explicit Lock(0, 1) probe is required: a byte-range lock does not prevent opening the
# file, so a bare Open() would always succeed.
#
# Dot-source this file to use it: . .github\scripts\wait-server-stopped.ps1

function Wait-ServerStopped($lockFile) {
  # Callers pass either a workspace-relative path (the zip build) or an absolute one (an
  # installed tree), so only resolve the relative ones.
  if (-not [System.IO.Path]::IsPathRooted($lockFile)) { $lockFile = Join-Path $PWD $lockFile }
  for ($i = 0; $i -lt 30; $i++) {
    if (-not (Test-Path $lockFile)) { return }
    # IOException only - that is what both a held byte-range lock and a sharing
    # violation raise. A blanket catch would also swallow UnauthorizedAccessException,
    # spin out the full minute on a permissions problem under Program Files and then
    # report a lock that was never held; let anything else surface with its own message.
    try {
      $fs = [System.IO.File]::Open($lockFile, 'Open', 'ReadWrite', 'ReadWrite')
      try { $fs.Lock(0, 1); $fs.Unlock(0, 1); return } finally { $fs.Close() }
    } catch [System.IO.IOException] { Start-Sleep -Seconds 2 }
  }
  throw "The server still holds the lock on ${lockFile}: the stop did not take effect"
}
