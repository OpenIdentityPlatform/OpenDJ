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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.responses;



import java.util.LinkedList;
import java.util.List;

import com.sun.opends.sdk.util.Validator;



/**
 * Search result reference implementation.
 */
final class SearchResultReferenceImpl extends
    AbstractResponseImpl<SearchResultReference> implements
    SearchResultReference
{

  private final List<String> uris = new LinkedList<String>();



  /**
   * Creates a new search result reference using the provided
   * continuation reference URI.
   * 
   * @param uri
   *          The first continuation reference URI to be added to this
   *          search result reference.
   * @throws NullPointerException
   *           If {@code uri} was {@code null}.
   */
  SearchResultReferenceImpl(String uri) throws NullPointerException
  {
    addURI(uri);
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultReference addURI(String uri)
      throws UnsupportedOperationException, NullPointerException
  {
    Validator.ensureNotNull(uri);
    uris.add(uri);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public SearchResultReference clearURIs()
      throws UnsupportedOperationException
  {
    uris.clear();
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public int getURICount()
  {
    return uris.size();
  }



  /**
   * {@inheritDoc}
   */
  public Iterable<String> getURIs()
  {
    return uris;
  }



  /**
   * {@inheritDoc}
   */
  public boolean hasURIs()
  {
    return !uris.isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("SearchResultReference(uris=");
    builder.append(getURIs());
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }



  SearchResultReference getThis()
  {
    return this;
  }

}
