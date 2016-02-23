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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.ArrayList;
import java.util.List;




/**
 * This class defines a data structure for storing information about
 * an entry that matches a given set of search criteria and should be
 * returned to the client.
 * When the search result entry contains attribute types only, the
 * objectclass type (if requested) will be present in the user
 * attributes.  When the search result entry contains both attribute
 * types and values, the objectclass attribute will not be present in
 * the user attributes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class SearchResultEntry
       extends Entry
{
  /** The set of controls associated with this search result entry. */
  private final List<Control> controls;



  /**
   * Creates a new search result entry based on the provided entry.
   * The provided entry should have been a duplicate of a real entry
   * so that any changes that may be made to this entry (e.g., by
   * access control or plugins) will not impact the original entry.
   *
   * @param  entry  The entry to use to create this search result
   *                entry.
   */
  public SearchResultEntry(Entry entry)
  {
    super(entry.getName(), entry.getObjectClasses(),
          entry.getUserAttributes(),
          entry.getOperationalAttributes());


    this.controls = new ArrayList<>(0);
  }



  /**
   * Creates a new search result entry based on the provided entry.
   * The provided entry should have been a duplicate of a real entry
   * so that any changes that may be made to this entry (e.g., by
   * access control or plugins) will not impact the original entry.
   *
   * @param  entry     The entry to use to create this search result
   *                   entry.
   * @param  controls  The set of controls to return to the client
   *                   with this entry.
   */
  public SearchResultEntry(Entry entry, List<Control> controls)
  {
    super(entry.getName(), entry.getObjectClasses(),
          entry.getUserAttributes(),
          entry.getOperationalAttributes());


    if (controls == null)
    {
      this.controls = new ArrayList<>(0);
    }
    else
    {
      this.controls = controls;
    }
  }



  /**
   * Retrieves the set of controls to include with this search result
   * entry when it is sent to the client.  This set of controls may be
   * modified by the caller.
   *
   * @return  The set of controls to include with this search result
   *          entry when it is sent to the client.
   */
  public List<Control> getControls()
  {
    return controls;
  }
}

