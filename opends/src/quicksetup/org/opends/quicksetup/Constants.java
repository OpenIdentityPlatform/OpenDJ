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

package org.opends.quicksetup;

import org.opends.admin.ads.ADSContext;

/**
 * Defines common constants.
 */
public class Constants {

  /** Platform appropriate line separator. */
  static public final String LINE_SEPARATOR =
          System.getProperty("line.separator");

  /** HTML line break tag. */
  public static final String HTML_LINE_BREAK = "<br>";

  /** HTML bold open tag. */
  public static final String HTML_BOLD_OPEN = "<b>";

  /** HTML bold close tag. */
  public static final String HTML_BOLD_CLOSE = "</b>";

  /** HTML italics open tag. */
  public static final String HTML_ITALICS_OPEN = "<i>";

  /** HTML italics close tag. */
  public static final String HTML_ITALICS_CLOSE = "</i>";

  /** HTML unordered list open tag. */
  public static final Object HTML_UNORDERED_LIST_OPEN = "<ul>";

  /** HTML unordered list close tag. */
  public static final Object HTML_UNORDERED_LIST_CLOSE = "</ul>";

  /** HTML unordered list open tag. */
  public static final Object HTML_ORDERED_LIST_OPEN = "<ol>";

  /** HTML unordered list close tag. */
  public static final Object HTML_ORDERED_LIST_CLOSE = "</ol>";

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

}
