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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;

/**
 * This enumeration contains the different minimal java versions required
 * to run properly OpenDS.  The versions specify a vendor and a java version.
 *
 */
enum CompatibleJava
{
  JDK_SUN("Sun Microsystems Inc.", "1.5.0_08");
  private String vendor;
  private String version;

  /**
   * Private constructor.
   * @param vendor the JVM vendor.
   * @param version the JVM version.
   */
  private CompatibleJava(String vendor, String version)
  {
    this.vendor = vendor;
    this.version = version;
  }

  /**
   * Returns the version of this compatible java version.
   * @return the version of this compatible java version.
   */
  String getVersion()
  {
    return version;
  }

  /**
   * Returns the vendor of this compatible java version.
   * @return the vendor of this compatible java version.
   */
  String getVendor()
  {
    return vendor;
  }
}
