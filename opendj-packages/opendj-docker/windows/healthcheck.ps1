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

$ErrorActionPreference = 'Stop'
$port = $env:LDAPS_PORT -as [int]
if ($port -lt 1 -or $port -gt 65535) {
    exit 1
}

# Setup completes before the foreground server starts. Read the root DSE without credentials.
& "$PSScriptRoot\bat\ldapsearch.bat" --noPropertiesFile --hostname localhost --port $port `
    --useSsl --trustAll --baseDN= --searchScope base '(objectClass=*)' 1.1
if ($LASTEXITCODE -ne 0) {
    exit 1
}
