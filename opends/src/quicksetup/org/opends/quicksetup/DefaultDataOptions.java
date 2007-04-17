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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

/**
 * This class is just used to specify which are the default values that will be
 * proposed to the user in the Data Options panel of the Installation wizard.
 *
 */
class DefaultDataOptions extends DataOptions
{
  /**
   * Default constructor.
   *
   */
  public DefaultDataOptions()
  {
    super(Type.CREATE_BASE_ENTRY, "dc=example,dc=com");
  }

  /**
   * Get the number of entries that will be automatically generated.
   *
   * @return the number of entries that will be automatically generated.
   */
  public int getNumberEntries()
  {
    return 2000;
  }

}
