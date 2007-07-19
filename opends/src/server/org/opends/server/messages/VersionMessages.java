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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.messages;

import static org.opends.server.messages.MessageHandler.registerMessage;
import static org.opends.server.messages.MessageHandler.
        CATEGORY_MASK_VERSION_COMPATIBITY_ISSUES;
import static org.opends.server.messages.MessageHandler.
        SEVERITY_MASK_INFORMATIONAL;

/**
 * Messages relating to incompatible version events (also known as 'flag-days')
 * that might cause potential issues or problems with upgrade or reversions
 * of a particular installation from one version to another.  These messages
 * are usually shown to the user during an upgrade or reversion process to
 * alert them to any postential issues.
 */
public class VersionMessages {

  /**
   * Message detailing possible upgrade issues caused by the upgrade of the
   * Berkley DB libraries in SVN rev 890.
   */
  public static final int MSGID_890_UPGRADE =
       CATEGORY_MASK_VERSION_COMPATIBITY_ISSUES |
               SEVERITY_MASK_INFORMATIONAL | 1;

  /**
   * Message detailing possible reversion issues caused by the upgrade of the
   * Berkley DB libraries in SVN rev 890.
   */
  public static final int MSGID_890_REVERSION =
       CATEGORY_MASK_VERSION_COMPATIBITY_ISSUES |
               SEVERITY_MASK_INFORMATIONAL | 2;

  /**
   * Message detailing possible upgrade issues cause by the database
   * record format change committed with SVN rev 1582.
   */
  public static final int MSGID_1582_UPGRADE =
       CATEGORY_MASK_VERSION_COMPATIBITY_ISSUES |
               SEVERITY_MASK_INFORMATIONAL | 3;

  /**
   * Message detailing possible reversion issues cause by the database
   * record format change committed with SVN rev 1582.
   */
  public static final int MSGID_1582_REVERSION =
       CATEGORY_MASK_VERSION_COMPATIBITY_ISSUES |
               SEVERITY_MASK_INFORMATIONAL | 4;

  /**
   * Message detailing possible reversion issues cause by the database
   * record format change committed with SVN rev 2049.
   */
  public static final int MSGID_2049_UPGRADE =
       CATEGORY_MASK_VERSION_COMPATIBITY_ISSUES |
               SEVERITY_MASK_INFORMATIONAL | 5;

  /**
   * Message detailing possible reversion issues cause by the database
   * record format change committed with SVN rev 2049.
   */
  public static final int MSGID_2049_REVERSION =
       CATEGORY_MASK_VERSION_COMPATIBITY_ISSUES |
               SEVERITY_MASK_INFORMATIONAL | 6;

  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages() {
    registerMessage(MSGID_890_UPGRADE,
            "With this upgrade, the Berkley DB Java Edition JAR " +
                    "will be upgraded to version 3.2.13 which introduces " +
                    "incompatibilities to the data format.  Consequently " +
                    "if at a later time you wish to revert this installation " +
                    "to its prior version you will have to export the data " +
                    "from this server and reimport it once the reversion " +
                    "has finished");

    registerMessage(MSGID_890_REVERSION,
            "With this reversion, the Berkley DB Java Editiong JAR " +
                    "will be downgraded to an older version which uses a " +
                    "different data format than the current version." +
                    "In order to revert this server you will have to export " +
                    "the data from this server and reimport it after the " +
                    "reversion has finished");

    registerMessage(MSGID_1582_UPGRADE,
            "This upgrade introduces improvements to the data format " +
                    "which are not backward compatible with the current " +
                    "version.  Consequently " +
                    "if at a later time you wish to revert this installation " +
                    "to its prior version you will have to export the data " +
                    "from this server and reimport it once the reversion " +
                    "has finished");

    registerMessage(MSGID_1582_REVERSION,
            "With this reversion the data format used to store data by the " +
                    "server will be reverted to a prior version.  " +
                    "In order to revert this server you will have to export " +
                    "the data from this server and reimport it after the " +
                    "reversion has finished");


    registerMessage(MSGID_2049_UPGRADE,
            "This upgrade introduces improvements to the data format " +
                    "which are not backward compatible with the current " +
                    "version.  Consequently " +
                    "if at a later time you wish to revert this installation " +
                    "to its prior version you will have to export the data " +
                    "from this server and reimport it once the reversion " +
                    "has finished");

    registerMessage(MSGID_2049_REVERSION,
            "With this reversion the data format used to store data by the " +
                    "server will be reverted to a prior version.  " +
                    "In order to revert this server you will have to export " +
                    "the data from this server and reimport it after the " +
                    "reversion has finished");


  }

}
