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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.webstart;

/**
 * The following properties are set in the QuickSetup.jnlp file to provide
 * informations.
 *
 */
public interface JnlpProperties
{
  /**
   * Java property used to known if we are using web start or not.
   */
  public static final String IS_WEBSTART = "org.opends.quicksetup.iswebstart";

  /**
   * Java property used to know which are the jar files that must be downloaded
   * lazily.  The current code in WebStartDownloader that uses this property
   * assumes that the URL are separated with an space.
   */
  public static final String LAZY_JAR_URLS =
      "org.opends.quicksetup.lazyjarurls";

  /**
   * Java property used to know which is the name of the zip file that must
   * be unzipped and whose contents must be extracted during the Web Start
   * based setup.
   */
  public static final String ZIP_FILE_NAME =
      "org.opends.quicksetup.zipfilename";
}
