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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

/**
 * This is an enumeration used to identify the different fields that we have
 * in the Installation wizard.
 *
 * Note that each field is not necessarily associated
 * with a single Swing component (for instance we have two text fields for
 * the server location).  This enumeration is used to retrieve information from
 * the panels without having any knowledge of the actual graphical layout.
 *
 */
public enum FieldName
{
  /**
   * The value associated with this is a Boolean.
   * It is used by the merged installer and upgrader to know whether an upgrade
   * or an install must be performed.
   */
  IS_UPGRADE,
  /**
   * The value associated with this is a String.
   * The upgrader uses this field to indicate the
   * location of the server to upgrade.
   */
  SERVER_TO_UPGRADE_LOCATION,
  /**
   * The value associated with this is a String.
   * The web start installer uses this field to indicate the
   * location to install the server.
   */
  SERVER_LOCATION,
  /**
   * The value associated with this is a String.
   */
  HOST_NAME,
  /**
   * The value associated with this is a String.
   */
  SERVER_PORT,
  /**
   * The value associated with this is a String.
   */
  DIRECTORY_MANAGER_DN,
  /**
   * The value associated with this is a String.
   */
  DIRECTORY_MANAGER_PWD,
  /**
   * The value associated with this is a String.
   */
  DIRECTORY_MANAGER_PWD_CONFIRM,
  /**
   * The value associated with this is a String.
   */
  DIRECTORY_BASE_DN, // the value associated with this is a String
  /**
  * The value associated with this is a SecurityOptions object.
  */
  SECURITY_OPTIONS,
  /**
   * The value associated with this is a DataOptions.Type.
   */
  DATA_OPTIONS,
  /**
   * The value associated with this is a String.
   */
  LDIF_PATH,
  /**
   * The value associated with this is a String.
   */
  NUMBER_ENTRIES,
  /**
   * The value associated with this is a DataReplicationOptions.Type.
   */
  REPLICATION_OPTIONS,
  /**
   * The value associated with this is a SuffixesToReplicateOptions.Type.
   */
  SUFFIXES_TO_REPLICATE_OPTIONS,
  /**
   * The value associated with this is a Set of SuffixDescriptor.
   */
  SUFFIXES_TO_REPLICATE,
  /**
   * The value associated with this is a Boolean.
   */
  REPLICATION_SECURE,
  /**
   * The value associated with this is a String.
   */
  REPLICATION_PORT,
  /**
   * The value associated with this is a String.
   */
  REMOTE_SERVER_DN,
  /**
   * The value associated with this is a String.
   */
  REMOTE_SERVER_PWD,
  /**
   * The value associated with this is a String.
   */
  REMOTE_SERVER_HOST,
  /**
   * The value associated with this is a String.
   */
  REMOTE_SERVER_PORT,
  /**
   * Whether the Remote Server Port is a secure port or not.  The value
   * associated with this is a Boolean.
   */
  REMOTE_SERVER_IS_SECURE_PORT,
  /**
   * The value associated with this is a String.
   */
  GLOBAL_ADMINISTRATOR_UID,
  /**
   * The value associated with this is a String.
   */
  GLOBAL_ADMINISTRATOR_PWD,
  /**
   * The value associated with this is a String.
   */
  GLOBAL_ADMINISTRATOR_PWD_CONFIRM,
  /**
   * The value associated with this is a Map where the key is a String and the
   * value a String.
   */
  REMOTE_REPLICATION_PORT,
  /**
   * The value associated with this is a Map where the key is a String and the
   * value a Boolean.
   */
  REMOTE_REPLICATION_SECURE,
  /**
   * The value associated with this is a Boolean.
   */
  SERVER_START_INSTALLER,
  /**
   * The value associated with this is a Boolean.
   */
  SERVER_START_UPGRADER,
  /**
   * The value associated with this is a Boolean.
   */
  ENABLE_WINDOWS_SERVICE,
  /**
   * The value associated with this is a Boolean.
   */
  REMOVE_LIBRARIES_AND_TOOLS,
  /**
   * The value associated with this is a Boolean.
   */
  REMOVE_DATABASES,
  /**
   * The value associated with this is a Boolean.
   */
  REMOVE_LOGS,
  /**
   * The value associated with this is a Boolean.
   */
  REMOVE_CONFIGURATION_AND_SCHEMA,
  /**
   * The value associated with this is a Boolean.
   */
  REMOVE_BACKUPS,
  /**
   * The value associated with this is a Boolean.
   */
  REMOVE_LDIFS,
  /**
   * The value associated with this is a Set of String.
   */
  EXTERNAL_DB_DIRECTORIES,
  /**
   * The value associated with this is a Set of String.
   */
  EXTERNAL_LOG_FILES,

  /**
   * Indicates whether the upgrade will need to first download
   * an OpenDS install package (.zip) to download or the
   * upgrader will use a file that has already been
   * downloaded.  The value of this field is boolean and if
   * true must be accompanied by a value for UPGRADE_BUILD_TO_DOWNLOAD.
   * If false UPGRADE_FILE must be specified.
   */
  UPGRADE_DOWNLOAD,

  /**
   * Display name of the build to which the upgrader
   * will upgrade the build indicated by SERVER_LOCATION.
   */
  UPGRADE_BUILD_TO_DOWNLOAD,

  /**
   * Local OpenDS install package (.zip) file containing
   * a build to which the build indicated by SERVER_LOCATION
   * will be upgraded.
   */
  UPGRADE_FILE

}
