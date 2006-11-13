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
package org.opends.server.synchronization.changelog;

import java.io.UnsupportedEncodingException;

import com.sleepycat.je.DatabaseEntry;

import org.opends.server.synchronization.common.ChangeNumber;

/**
 * Superclass of DatabaseEntry.
 * Useful to create Changelog keys from ChangeNumbers.
 */
public class ChangelogKey extends DatabaseEntry
{
  /**
   * Creates a new ChangelogKey from the given ChangeNumber.
   * @param changeNumber The changeNumber to use.
   */
  public ChangelogKey(ChangeNumber changeNumber)
  {
    try
    {
      this.setData(changeNumber.toString().getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e)
    {
      // Should never happens, UTF-8 is always supported
      // TODO : add better logging
    }
  }
}
