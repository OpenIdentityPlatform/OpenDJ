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
import java.util.Iterator;

import org.opends.server.util.CollectionUtils;

/**
 * This class defines a data structure for storing information about a
 * referral returned while processing a search request.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class SearchResultReference
{
  /** The set of controls associated with this search result reference. */
  private List<Control> controls;

  /** The set of referral URLs for this search result reference. */
  private List<String> referralURLs;



  /**
   * Creates a new search result reference with the provided referral
   * URL.
   *
   * @param  referralURL  The referral URL for this search result
   *                      reference.
   */
  public SearchResultReference(String referralURL)
  {
    referralURLs = CollectionUtils.newArrayList(referralURL);
    this.controls = new ArrayList<>(0);
  }



  /**
   * Creates a new search result reference with the provided set of
   * referral URLs and no controls.
   *
   * @param  referralURLs  The referral URLs for this search result
   *                       reference.
   */
  public SearchResultReference(List<String> referralURLs)
  {
    if (referralURLs == null)
    {
      this.referralURLs = new ArrayList<>();
    }
    else
    {
      this.referralURLs = referralURLs;
    }

    this.controls = new ArrayList<>(0);
  }



  /**
   * Creates a new search result reference with the provided set of
   * referral URLs and no controls.
   *
   * @param  referralURLs  The referral URLs for this search result
   *                       reference.
   * @param  controls      The set of controls for this search result
   *                       reference.
   */
  public SearchResultReference(List<String> referralURLs,
                               List<Control> controls)
  {
    if (referralURLs == null)
    {
      this.referralURLs = new ArrayList<>();
    }
    else
    {
      this.referralURLs = referralURLs;
    }

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
   * Retrieves the set of referral URLs for this search result
   * reference.  It may be modified by the caller.
   *
   * @return  The set of referral URLs for this search result
   *          reference.
   */
  public List<String> getReferralURLs()
  {
    return referralURLs;
  }



  /**
   * Retrieves a string representation of the referral URL(s) for this
   * search result reference.
   *
   * @return  A string representation of the referral URL(s) for this
   *          search result reference.
   */
  public String getReferralURLString()
  {
    if (referralURLs == null || referralURLs.isEmpty())
    {
      return "";
    }
    else if (referralURLs.size() == 1)
    {
      return referralURLs.get(0);
    }
    else
    {
      Iterator<String> iterator = referralURLs.iterator();
      StringBuilder    buffer   = new StringBuilder();
      buffer.append("{ ");
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      buffer.append(" }");
      return buffer.toString();
    }
  }



  /**
   * Retrieves the set of controls to include with this search result
   * reference when it is sent to the client.  This set may be
   * modified by the caller.
   *
   * @return  The set of controls to include with this search result
   *          reference when it is sent to the client.
   */
  public List<Control> getControls()
  {
    return controls;
  }
}

