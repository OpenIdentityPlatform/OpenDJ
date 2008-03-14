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

# ----------------------------------------------------------------------
# BEFORE running system-tests, you need to set up your environment
# 
# TMPDIR    : is the directory where the results will be stored
# OPENDSDIR : is the opends framework directory you have checked out
#             example : MY_PATH/opends
# ----------------------------------------------------------------------


STAF_LOCAL_HOSTNAME         = 'localhost'
STAF_REMOTE_HOSTNAME        = 'localhost'
TMPDIR                      = 'NEED_VALUE'
OPENDSDIR                   = 'NEED_VALUE'
TESTS_ROOT                  = '%s/tests' % OPENDSDIR
TESTS_DIR                   = '%s/system-tests' % TESTS_ROOT
DIRECTORY_INSTANCE_DN       = 'cn=directory manager'
DIRECTORY_INSTANCE_PSWD     = 'secret12'
JAVA_HOME                   = 'NEED_VALUE'
