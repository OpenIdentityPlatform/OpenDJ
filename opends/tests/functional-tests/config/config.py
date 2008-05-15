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
#      Copyright 2006-2008 Sun Microsystems, Inc.

TEST_OS_STRING              = ''
TEST_JVM_STRING             = ''
STAF_LOCAL_HOSTNAME         = 'localhost'
STAF_REMOTE_HOSTNAME        = 'localhost'
TMPDIR                      = '/tmp'
PSWDFILE                    = '%s/password' % TMPDIR
OPENDSDIR                   = '/path/to/opends'
OPENDSNAME                  = 'OpenDS-1.0.0'
ZIPNAME                     = '%s.zip' % OPENDSNAME
ZIPPATH                     = '%s/build/package' % OPENDSDIR
TESTS_ROOT                  = '%s/tests' % OPENDSDIR
TESTS_DIR                   = '%s/functional-tests' % TESTS_ROOT
TESTS_SHARED_DIR            = '%s/shared' % TESTS_DIR
TESTS_FUNCTIONS_DIR         = '%s/shared/functions' % TESTS_ROOT
TESTS_DATA_DIR              = '%s/data' % TESTS_SHARED_DIR
TESTS_JAVA_DIR              = '%s/shared/java' % TESTS_ROOT
DIRECTORY_INSTANCE_DN       = 'cn=Directory Manager'
DIRECTORY_INSTANCE_PSWD     = 'password'
DIRECTORY_INSTANCE_DIR      = '%s' % TMPDIR
DIRECTORY_INSTANCE_HOST     = 'localhost'
DIRECTORY_INSTANCE_PORT     = '1389'
DIRECTORY_INSTANCE_SSL_PORT = '1636'
DIRECTORY_INSTANCE_SFX      = 'dc=com'
DIRECTORY_INSTANCE_BE       = 'userRoot'
JAVA_HOME                   = '/path/to/jdk'
LOGS_ROOT                   = '%s' % TMPDIR
LOGS_URI                    = ''
SEND_MAIL_AFTER_TEST_RUN    = 'false'
SEND_MAIL_TO                = ''
WC_TYPE                     = 'apache-tomcat'
WC_VERSION                  = '6.0.14'
WC_ZIPPATH                  = '%s/tests/ext' % OPENDSDIR
WC_ZIPNAME                  = '%s-%s.zip' % (WC_TYPE, WC_VERSION)
WC_DIRECTORY                = '%s' % TMPDIR
WC_PORT                     = '9000'
WC_SSL_PORT                 = '9001'
WC_HOST                     = 'localhost'
DSML_WARNAME                = 'OpenDS-1.0.0-DSML.war'
DSML_WARPATH                = '%s/build/package/%s' % (OPENDSDIR, DSML_WARNAME)
SNMP_OPENDS_JARFILE         = '%s/lib/extensions/snmp-mib2605.jar' % OPENDSNAME
SNMP_OPENDMK_LIBDIR         = '/path/to/opendmk/lib'
SNMP_OPENDMK_JARFILE        = '%s/jdmkrt.jar' % SNMP_OPENDMK_LIBDIR
SNMP_PORT                   = '8085'
SNMP_TRAP_PORT              = '8086'
VERBOSE_MODE                = 'False'
