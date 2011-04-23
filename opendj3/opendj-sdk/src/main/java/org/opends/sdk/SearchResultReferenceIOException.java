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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.io.IOException;

import org.opends.sdk.responses.SearchResultReference;

import com.sun.opends.sdk.util.Validator;



/**
 * Thrown when an iteration over a set of search results using a
 * {@code ConnectionEntryReader} encounters a {@code SearchResultReference}.
 */
@SuppressWarnings("serial")
public final class SearchResultReferenceIOException extends IOException
{
  private final SearchResultReference reference;



  /**
   * Creates a new referral result IO exception with the provided
   * {@code SearchResultReference}.
   *
   * @param reference
   *          The {@code SearchResultReference} which may be later retrieved by
   *          the {@link #getReference} method.
   * @throws NullPointerException
   *           If {@code reference} was {@code null}.
   */
  public SearchResultReferenceIOException(final SearchResultReference reference)
      throws NullPointerException
  {
    super(Validator.ensureNotNull(reference).toString());
    this.reference = reference;
  }



  /**
   * Returns the {@code SearchResultReference} which was encountered while
   * processing the search results.
   *
   * @return The {@code SearchResultReference} which was encountered while
   *         processing the search results.
   */
  public SearchResultReference getReference()
  {
    return reference;
  }
}
