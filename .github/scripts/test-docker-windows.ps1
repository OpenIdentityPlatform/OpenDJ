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
    [string] $Image
)

$ErrorActionPreference = 'Stop'
$container = "opendj-smoke-$([guid]::NewGuid().ToString('N'))"
$volume = "$container-data"
$mount = "type=volume,source=$volume,target=C:\opendj\data"
$password = "OpEnDJ-$([guid]::NewGuid().ToString('N'))"

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($args[0]) failed with exit code $LASTEXITCODE"
    }
}

function Assert-LdapReady {
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $state = Invoke-Docker inspect --format '{{.State.Status}} {{.State.Health.Status}}' $container
        if ($state -eq 'running healthy') {
            $entry = Invoke-Docker exec $container cmd.exe /d /c C:\opendj\bat\ldapsearch.bat `
                --noPropertiesFile --hostname localhost --port 1636 --useSsl --trustAll `
                --bindDN 'cn=Directory Manager' --bindPassword $password `
                --baseDN 'dc=smoke,dc=example' --searchScope base '(objectClass=*)' 1.1
            if ($entry -notcontains 'dn: dc=smoke,dc=example') {
                throw 'LDAP search did not return the configured base entry'
            }
            return
        }
        if ($state -notlike 'running *' -or $state -eq 'running unhealthy') {
            throw "OpenDJ container is $state"
        }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    throw 'OpenDJ container did not become healthy'
}

try {
    Invoke-Docker volume create $volume | Out-Null
    Invoke-Docker run --detach --isolation=process --name $container --mount $mount `
        --env "ROOT_PASSWORD=$password" --env 'BASE_DN=dc=smoke,dc=example' $Image | Out-Null
    Assert-LdapReady

    Invoke-Docker stop $container | Out-Null
    Invoke-Docker rm $container | Out-Null

    # A replacement container must serve the same base entry and accept the same password.
    Invoke-Docker run --detach --isolation=process --name $container --mount $mount $Image | Out-Null
    Assert-LdapReady
    $errors = Invoke-Docker exec $container cmd.exe /d /c type C:\opendj\data\logs\errors
    if (-not ($errors -match 'category=CORE severity=NOTICE msgID=203\s')) {
        throw 'The persisted log does not record a clean OpenDJ shutdown'
    }
} catch {
    docker logs $container
    docker inspect --format '{{json .State.Health}}' $container
    throw
} finally {
    docker rm --force $container
    docker volume rm $volume
}
