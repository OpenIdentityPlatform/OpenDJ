#!/bin/ksh

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
#
# You can obtain a copy of the license at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE
# or https://OpenDS.dev.java.net/OpenDS.LICENSE.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2008 Sun Microsystems, Inc.

suffix="dc=com"
hostname=nott
maxDuration=50
nb_threads=1
NB_MAX_mod=100
keystorePath=/tmp/sylvie
LDAPSport=1235

bindDN="cn=directory manager"
bindPW=password

#java -client -Xmx1G -Xms1G -XX:NewRatio=1 -XX:SurvivorRatio=100 -cp ../LDAPjdk/ldapjdk.jar:search.jar Client $@

#java -client -Xmx1G -Xms1G -XX:NewRatio=1 -XX:SurvivorRatio=100 -cp ../LDAPjdk/ldapjdk.jar:search.jar -Djavax.net.ssl.keyStore=/tmp/shared/data/CERT_1111/keystore -Djavax.net.ssl.trustStorePassword=password -Djavax.net.ssl.keyStorePassword=password -Djavax.net.ssl.trustStore=/tmp/shared/data/CERT_1111/keystore -Djava.security.debug=ALL -Djava.security.auth.debug=ALL -Djavax.net.debug=ALL -Djavax.security.sasl.level=FINEST Client $@

## SSL
java -client -Xmx1G -Xms1G -XX:NewRatio=1 -XX:SurvivorRatio=100 -cp secureModifyEntries.jar -Djavax.net.ssl.keyStore=$keystorePath/keystore -Djavax.net.ssl.trustStorePassword=password -Djavax.net.ssl.keyStorePassword=password -Djavax.net.ssl.trustStore=$keystorePath/keystore  -Djavax.security.sasl.level=FINEST -Dport=$LDAPSport -DmaxDuration=$maxDuration -DNB_MAX_mod=$NB_MAX_mod -Dsuffix=$suffix -Dnb_threads=$nb_threads -Dhostname=$hostname Client $@
#


## CLEAR
#java -client -Xmx1G -Xms1G -XX:NewRatio=1 -XX:SurvivorRatio=100 -cp search.jar -Djavax.net.ssl.keyStore=/tmp/shared/data/CERT_1111/keystore -Djavax.net.ssl.trustStorePassword=password -Djavax.net.ssl.keyStorePassword=password -Djavax.net.ssl.trustStore=/tmp/shared/data/CERT_1111/keystore  -Djavax.security.sasl.level=FINEST -Dport=1111 -DmaxDuration=$maxDuration -DNB_MAX_mod=$NB_MAX_mod -Dsuffix=$suffix -Dnb_threads=$nb_threads -Dhostname=$hostname -DbindDN="$bindDN" -DbindPW=$bindPW Client $@

