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
 *      Portions Copyright 2013 ForgeRock AS.
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
  ADMIN_CONNECTOR_PORT,
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
   * The value associated with this is a data options.Type.
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
   * The value associated with this is a JavaArguments object.
   */
  SERVER_JAVA_ARGUMENTS,
  /**
   * The value associated with this is a JavaArguments object.
   */
  IMPORT_JAVA_ARGUMENTS,
  /**
   * The value associated with this is a Boolean.
   */
  SERVER_START_INSTALLER,
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
  EXTERNAL_LOG_FILES
}
