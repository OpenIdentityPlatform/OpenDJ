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
package org.opends.server.tools.dsconfig;



import java.util.MissingResourceException;
import java.util.ResourceBundle;



/**
 * Message resource bundle for dsconfig.
 */
public class Messages {

  // The name of the bundle.
  private static final String BUNDLE_NAME =
    "org.opends.server.tools.dsconfig.messages"; //$NON-NLS-1$

  // The single resource bundle.
  private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle
      .getBundle(BUNDLE_NAME);



  // Prevent instantiation.
  private Messages() {
    // No implementation required.
  }



  /**
   * Get the named property.
   *
   * @param key
   *          The name of the property.
   * @return Returns the named property.
   */
  public static String getString(String key) {
    try {
      return RESOURCE_BUNDLE.getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }
}
