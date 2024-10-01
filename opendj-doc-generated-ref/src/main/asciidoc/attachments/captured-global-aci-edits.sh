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
# Portions Copyright 2024 3A Systems LLC.

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
#  The following command sequence utilizes single quote encapsulation
#  of the `global-aci` value. This is simply to avoid
#  the need for extensive character escapes.  If the quotes are removed,
#  the user will need to manually escape certain characters, such as pipe
#  (`|`) or exclamation points (`!`) to
#  avoid shell errors.

dsconfig set-access-control-handler-prop \
         --remove=global-aci:'(targetattr!="userPassword||authPassword||changes||
         changeNumber||changeType||changeTime||targetDN||newRDN||
         newSuperior||deleteOldRDN||targetEntryUUID||changeInitiatorsName||
         changeLogCookie||includedAttributes")(version 3.0; acl "Anonymous
          read access"; allow (read,search,compare) userdn="ldap:///anyone";)' \
         --hostname=opendj.example.com \
         --port=4444 \
         --bindDN=cn=Directory\ Manager \
         --bindPassword=password \
         --trustAll \
         --no-prompt

