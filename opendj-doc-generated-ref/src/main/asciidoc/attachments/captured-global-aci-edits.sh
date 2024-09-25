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
# information: "Portions Copyright [year] [name of copyright owner]".
#
# Portions Copyright 2015 ForgeRock AS.

# Captured dsconfig command to replace anonymous read access with authenticated access.
# Edit this script to match your deployment.
#
# This command does not affect global-aci properties allowing anonymous access
# to read the root DSE and to read directory schema definitions,
# nor to use selected LDAP controls and extended operations.
#
# This command works against a server built following the changes introduced
# in http://sources.forgerock.org/changelog/opendj?cs=9325.
# If the global-aci settings are different on your OpenDJ server,
# generate this script for that server as described in the documentation.
#
dsconfig set-access-control-handler-prop \
          --remove global-aci:\(targetattr!=\"userPassword\|\|authPassword\|\|debugsearchindex\|\|changes\|\|changeNumber\|\|changeType\|\|changeTime\|\|targetDN\|\|newRDN\|\|newSuperior\|\|deleteOldRDN\"\)\(version\ 3.0\;\ acl\ \"Anonymous\ read\ access\"\;\ allow\ \(read,search,compare\)\ userdn=\"ldap:///anyone\"\;\) \
          --remove global-aci:\(targetattr=\"createTimestamp\|\|creatorsName\|\|modifiersName\|\|modifyTimestamp\|\|entryDN\|\|entryUUID\|\|subschemaSubentry\|\|etag\|\|governingStructureRule\|\|structuralObjectClass\|\|hasSubordinates\|\|numSubordinates\"\)\(version\ 3.0\;\ acl\ \"User-Visible\ Operational\ Attributes\"\;\ allow\ \(read,search,compare\)\ userdn=\"ldap:///anyone\"\;\) \
          --add global-aci:\(targetattr!=\"userPassword\|\|authPassword\|\|debugsearchindex\|\|changes\|\|changeNumber\|\|changeType\|\|changeTime\|\|targetDN\|\|newRDN\|\|newSuperior\|\|deleteOldRDN\"\)\(version\ 3.0\;\ acl\ \"Authenticated\ read\ access\"\;\ allow\(read,search,compare\)\ userdn=\"ldap:///all\"\;\) \
          --add global-aci:\(targetattr=\"createTimestamp\|\|creatorsName\|\|modifiersName\|\|modifyTimestamp\|\|entryDN\|\|entryUUID\|\|subschemaSubentry\|\|etag\|\|governingStructureRule\|\|structuralObjectClass\|\|hasSubordinates\|\|numSubordinates\"\)\(version\ 3.0\;\ acl\ \"User-Visible\ Operational\ Attributes\"\;\ allow\(read,search,compare\)\ userdn=\"ldap:///all\"\;\) \
          --hostname opendj.example.com \
          --port 4444 \
          --trustStorePath /path/to/opendj/config/admin-truststore \
          --bindDN cn=Directory\ Manager \
          --bindPassword ****** \
          --no-prompt

