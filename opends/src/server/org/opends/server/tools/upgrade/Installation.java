/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

/**
 * This class represents the physical state of an OpenDJ installation. All the
 * operations are dependent upon the root directory that is specified in the
 * constructor.
 */
public final class Installation
{

  /** Relative path to bootstrap OpenDJ jar file. */
  public static final String OPENDJ_BOOTSTRAP_JAR_RELATIVE_PATH =
      "lib/bootstrap.jar";

  /**
   * The relative path where all the Windows binaries (batch files) are.
   */
  public static final String WINDOWS_BINARIES_PATH_RELATIVE = "bat";

  /**
   * The relative path where all the UNIX binaries (scripts) are.
   */
  public static final String UNIX_BINARIES_PATH_RELATIVE = "bin";

  /**
   * The relative path where the database files are.
   */
  public static final String DATABASES_PATH_RELATIVE = "db";

  /**
   * The relative path where the log files are.
   */
  public static final String LOGS_PATH_RELATIVE = "logs";

  /**
   * The relative path where the config files are.
   */
  public static final String CONFIG_PATH_RELATIVE = "config";

  /**
   * The relative path where the config files are.
   */
  public static final String HISTORY_PATH_RELATIVE = "history";

  /**
   * Path to the config/upgrade directory where upgrade base files are stored.
   */
  public static final String UPGRADE_PATH = "upgrade";

  /**
   * Relative path to the change log database directory.
   */
  public static final String CHANGELOG_PATH_RELATIVE = "changelogDb";

  /**
   * Relative path to the locks directory.
   */
  public static final String LOCKS_PATH_RELATIVE = "locks";

  /**
   * Relative path to the locks directory.
   */
  public static final String TMP_PATH_RELATIVE = "tmp";

  /**
   * Relative path to the snmp directory.
   */
  public static final String SNMP_PATH_RELATIVE = "snmp";

  /**
   * Relative path to the security directory.
   */
  public static final String SECURITY_PATH_RELATIVE = "security";

  /**
   * The relative path to the current Configuration LDIF file.
   */
  public static final String CURRENT_CONFIG_FILE_NAME = "config.ldif";

  /**
   * The path to the default instance.
   */
  public static final String DEFAULT_INSTANCE_PATH = "/var/opendj";

  /**
   * The relative path to the instance.loc file.
   */
  public static final String INSTANCE_LOCATION_PATH_RELATIVE = "instance.loc";

  /**
   * The path to the instance.loc file.
   */
  public static final String INSTANCE_LOCATION_PATH = "/etc/opendj/"
      + INSTANCE_LOCATION_PATH_RELATIVE;

  /**
   * The relative path to tmpl_instance.
   */
  public static final String TEMPLATE_RELATIVE_PATH = "template";

  /**
   * Relative path to the schema directory.
   */
  public static final String SCHEMA_PATH_RELATIVE = "schema";

  /**
   * The relative path to buildinfo file.
   */
  public static final String BUILDINFO_RELATIVE_PATH = "buildinfo";

  /**
   * The UNIX setup script file name.
   */
  public static final String UNIX_SETUP_FILE_NAME = "setup";

  /**
   * The UNIX upgrade script file name.
   */
  public static final String UNIX_UPGRADE_FILE_NAME = "upgrade";

  /**
   * The UNIX uninstall script file name.
   */
  public static final String UNIX_UNINSTALL_FILE_NAME = "uninstall";

  /**
   * The Windows upgrade batch file name.
   */
  public static final String WINDOWS_UPGRADE_FILE_NAME = "upgrade.bat";

  /**
   * The UNIX configure script file name.
   */
  public static final String UNIX_CONFIGURE_FILE_NAME = "configure";

  /**
   * The MacOS X Java application stub name.
   */
  public static final String MAC_JAVA_APP_STUB_NAME = "JavaApplicationStub";

  /**
   * Generic name for the backup tool.
   */
  public static final String BACKUP = "backup";

}
