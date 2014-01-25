#
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

