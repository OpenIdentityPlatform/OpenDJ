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

param(
    [Parameter(Mandatory)]
    [string] $ServerZipDirectory,
    [Parameter(Mandatory)]
    [string] $Image
)

$ErrorActionPreference = 'Stop'
$serverZips = @(Get-ChildItem -LiteralPath $ServerZipDirectory -File -Filter 'opendj-*.zip' |
    Where-Object Name -NotLike '*-slim.zip')
if ($serverZips.Count -ne 1) {
    throw "Expected one full OpenDJ server ZIP in $ServerZipDirectory. Found $($serverZips.Count)."
}

$context = Join-Path $env:RUNNER_TEMP "opendj-windows-$([guid]::NewGuid().ToString('N'))"
$windowsFiles = Join-Path $PSScriptRoot '../../opendj-packages/opendj-docker/windows/*'
try {
    Expand-Archive -LiteralPath $serverZips[0].FullName -DestinationPath $context
    Copy-Item -Path $windowsFiles -Destination $context
    # Windows Docker Engine uses the classic builder for native Windows containers.
    $env:DOCKER_BUILDKIT = '0'
    docker build --isolation=process --pull --tag $Image $context
    if ($LASTEXITCODE -ne 0) {
        throw "Windows image build failed with exit code $LASTEXITCODE"
    }
} finally {
    if (Test-Path -LiteralPath $context) {
        Remove-Item -LiteralPath $context -Recurse -Force
    }
}
