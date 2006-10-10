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
package org.opends.server.types;



import java.util.ArrayList;
import java.util.List;

import static org.opends.server.loggers.Debug.*;



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
public class SearchResultEntry
       extends Entry
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.SearchResultEntry";



  // The set of controls associated with this search result entry.
  private List<Control> controls;



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
    super(entry.getDN(), entry.getObjectClasses(),
          entry.getUserAttributes(),
          entry.getOperationalAttributes());

    assert debugConstructor(CLASS_NAME, String.valueOf(entry));

    this.controls = new ArrayList<Control>(0);
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
    super(entry.getDN(), entry.getObjectClasses(),
          entry.getUserAttributes(),
          entry.getOperationalAttributes());

    assert debugConstructor(CLASS_NAME, String.valueOf(entry),
                            String.valueOf(controls));

    if (controls == null)
    {
      this.controls = new ArrayList<Control>(0);
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
    assert debugEnter(CLASS_NAME, "getControls");

    return controls;
  }
}

