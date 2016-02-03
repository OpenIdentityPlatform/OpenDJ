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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.util.Reject;
import org.forgerock.opendj.ldap.DN;

/**
 * This class represents the configuration of a JE backend verification process.
 */
public class VerifyConfig
{
  /** The base DN to be verified. */
  private DN baseDN;
  /** The names of indexes to be verified for completeness. */
  private ArrayList<String> completeList = new ArrayList<>();
  /** The names of indexes to be verified for cleanliness. */
  private ArrayList<String> cleanList = new ArrayList<>();

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
  public List<String> getCompleteList()
  {
    return completeList;
  }

  /**
   * Add the name of an index to those indexes to be verified for completeness.
   * @param index The name of an index to be verified for completeness.
   */
  public void addCompleteIndex(String index)
  {
    Reject.ifNull(index);
    completeList.add(index);
  }

  /**
   * Get the names of indexes to be verified for cleanliness.
   * @return The names of indexes to be verified for cleanliness.
   */
  public List<String> getCleanList()
  {
    return cleanList;
  }

  /**
   * Add the name of an index to those indexes to be verified for cleanliness.
   * @param index The name of an index to be verified for cleanliness.
   */
  public void addCleanIndex(String index)
  {
    Reject.ifNull(index);
    cleanList.add(index);
  }
}
