/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import org.opends.admin.ads.ADSContext;

/**
 * Defines common constants.
 */
public class Constants {

  /** Platform appropriate line separator. */
  public static final String LINE_SEPARATOR = System.getProperty("line.separator");

  /** HTML line break tag. */
  public static final String HTML_LINE_BREAK = "<br>";

  /** HTML bold open tag. */
  public static final String HTML_BOLD_OPEN = "<b>";

  /** HTML bold close tag. */
  public static final String HTML_BOLD_CLOSE = "</b>";

  /** HTML list item open tag. */
  public static final String HTML_LIST_ITEM_OPEN = "<li>";

  /** HTML list item close tag. */
  public static final String HTML_LIST_ITEM_CLOSE = "</li>";

  /** Default dynamic name of directory manager. */
  public static final String DIRECTORY_MANAGER_DN = "cn=Directory Manager";

  /** Default global admin UID. */
  public static final String GLOBAL_ADMIN_UID = ADSContext.GLOBAL_ADMIN_UID;
  /** These HTML tags cause a line break in formatted text. */
  public static final String[] BREAKING_TAGS = {
          HTML_LINE_BREAK,
          HTML_LIST_ITEM_CLOSE
  };

  /** DN of the schema object. */
  public static final String SCHEMA_DN = "cn=schema";

  /** DN of legacy replication changes base DN for backwards compatibility with OpenDJ <= 2.6.x. */
  public static final String REPLICATION_CHANGES_DN = "dc=replicationChanges";

  /** The cli java system property. */
  public static final String CLI_JAVA_PROPERTY = "org.opends.quicksetup.cli";

  /** The default replication port. */
  public static final int DEFAULT_REPLICATION_PORT = 8989;

  /** The maximum chars we show in a line of a dialog. */
  public static final int MAX_CHARS_PER_LINE_IN_DIALOG = 100;
}
