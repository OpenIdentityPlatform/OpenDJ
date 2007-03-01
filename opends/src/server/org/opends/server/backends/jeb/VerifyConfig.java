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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.types.DN;

import java.util.ArrayList;

/**
 * This class represents the configuration of a JE backend verification process.
 */
public class VerifyConfig
{
  /**
   * The base DN to be verified.
   */
  private DN baseDN;

  /**
   * The names of indexes to be verified for completeness.
   */
  private ArrayList<String> completeList;

  /**
   * The names of indexes to be verified for cleanliness.
   */
  private ArrayList<String> cleanList;

  /**
   * Create a new verify configuration.
   */
  public VerifyConfig()
  {
    baseDN = null;
    completeList = new ArrayList<String>();
    cleanList = new ArrayList<String>();
  }

  /**
   * Get the base DN to be verified.
   * @return The base DN to be verified.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Set the base DN to be verified.
   * @param baseDN The base DN to be verified.
   */
  public void setBaseDN(DN baseDN)
  {
    this.baseDN = baseDN;
  }

  /**
   * Get the names of indexes to be verified for completeness.
   * @return The names of indexes to be verified for completeness.
   */
  public ArrayList<String> getCompleteList()
  {
    return completeList;
  }

  /**
   * Add the name of an index to those indexes to be verified for completeness.
   * @param index The name of an index to be verified for completeness.
   */
  public void addCompleteIndex(String index)
  {
    completeList.add(index);
  }

  /**
   * Get the names of indexes to be verified for cleanliness.
   * @return The names of indexes to be verified for cleanliness.
   */
  public ArrayList<String> getCleanList()
  {
    return cleanList;
  }

  /**
   * Add the name of an index to those indexes to be verified for cleanliness.
   * @param index The name of an index to be verified for cleanliness.
   */
  public void addCleanIndex(String index)
  {
    cleanList.add(index);
  }
}
