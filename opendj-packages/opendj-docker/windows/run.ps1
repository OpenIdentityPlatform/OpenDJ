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
Set-Location -LiteralPath $PSScriptRoot

function Get-Port([string] $Name) {
    $port = (Get-Item "Env:$Name").Value -as [int]
    if ($port -lt 1 -or $port -gt 65535) {
        throw "$Name must be a port number between 1 and 65535"
    }
    return $port
}

function ConvertTo-PropertyValue([string] $Value) {
    # Preserve DN escapes and Unicode when Java reads the properties file.
    return -join ($Value.ToCharArray() | ForEach-Object { '\u{0:x4}' -f [int]$_ })
}

$data = Join-Path $PSScriptRoot 'data'
New-Item -ItemType Directory -Path $data -Force | Out-Null
# The batch launchers read one line with cmd.exe's SET /P.
Set-Content -Encoding ASCII -LiteralPath instance.loc -Value $data

$pending = Join-Path $data '.bootstrap-pending'
$setupProperties = Join-Path $env:TEMP 'opendj-setup.properties'
$passwordFile = Join-Path $env:TEMP 'opendj-setup-password'
Remove-Item -LiteralPath $setupProperties, $passwordFile -Force -ErrorAction SilentlyContinue

if (Test-Path -LiteralPath $pending) {
    throw 'OpenDJ setup did not finish. Restore the data volume or use a new volume.'
}
if (-not (Test-Path -LiteralPath (Join-Path $data 'config/config.ldif') -PathType Leaf)) {
    if ([string]::IsNullOrEmpty($env:ROOT_PASSWORD) -or $env:ROOT_PASSWORD -match '[\r\n]') {
        throw 'Set ROOT_PASSWORD to a nonempty single-line password for the first start.'
    }
    if ([string]::IsNullOrWhiteSpace($env:BASE_DN) -or [string]::IsNullOrWhiteSpace($env:ROOT_USER_DN)) {
        throw 'BASE_DN and ROOT_USER_DN must be nonempty.'
    }

    # Pass configurable values as properties to keep DN characters out of the batch command line.
    $properties = [string[]] @(
        "ldapPort=$(Get-Port PORT)"
        "ldapsPort=$(Get-Port LDAPS_PORT)"
        "adminConnectorPort=$(Get-Port ADMIN_PORT)"
        "baseDN=$(ConvertTo-PropertyValue $env:BASE_DN)"
        "rootUserDN=$(ConvertTo-PropertyValue $env:ROOT_USER_DN)"
    )
    try {
        [IO.File]::WriteAllLines($setupProperties, $properties)
        [IO.File]::WriteAllText($passwordFile, $env:ROOT_PASSWORD)
        New-Item -ItemType File -Path $pending | Out-Null
        & .\setup.bat --cli --no-prompt --acceptLicense --doNotStart --addBaseEntry `
            --hostname localhost --generateSelfSignedCertificate --enableStartTLS --propertiesFilePath $setupProperties `
            --rootUserPasswordFile $passwordFile
        if ($LASTEXITCODE -ne 0) {
            throw "OpenDJ setup failed with exit code $LASTEXITCODE"
        }
        Remove-Item -LiteralPath $pending -Force
    } finally {
        Remove-Item -LiteralPath $setupProperties, $passwordFile -Force -ErrorAction SilentlyContinue
    }
}

Remove-Item Env:ROOT_PASSWORD -ErrorAction SilentlyContinue
& .\bat\start-ds.bat --nodetach
exit $LASTEXITCODE
