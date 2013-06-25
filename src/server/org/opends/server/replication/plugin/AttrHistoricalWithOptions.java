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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.HashMap;
import java.util.Set;


/**
 * Used to store historical information.
 * Contain a map of AttrInfo for each options of a given attribute type.
 */
public class AttrHistoricalWithOptions
{
  private HashMap<Set<String> ,AttrHistorical> attributesInfo;

  /**
   * creates a new AttrInfoWithOptions.
   */
  public AttrHistoricalWithOptions()
  {
    attributesInfo = new HashMap<Set<String> ,AttrHistorical>();
  }

  /**
   * Get the info for a given option.
   *
   * @param options the options
   * @return the information
   */
  public AttrHistorical get(Set<String> options)
  {
    return attributesInfo.get(options);
  }

  /**
   * Associate some info to a given set of options.
   *
   * @param options the options
   * @param attrInfo the info to associate
   * @return the info to associate
   */
  public AttrHistorical put(Set<String> options, AttrHistorical attrInfo )
  {
    return attributesInfo.put(options, attrInfo);
  }

  /**
   * get the Attributes information associated to this object.
   * @return the set of informations
   */
  public HashMap<Set<String>, AttrHistorical> getAttributesInfo()
  {
    return attributesInfo;
  }
}

