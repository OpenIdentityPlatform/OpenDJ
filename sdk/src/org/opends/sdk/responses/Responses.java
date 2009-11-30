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



import org.opends.sdk.DN;
import org.opends.sdk.Entry;
import org.opends.sdk.ResultCode;
import org.opends.sdk.SortedEntry;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.Validator;



/**
 * This class contains various methods for creating and manipulating
 * responses.
 * <p>
 * TODO: search reference from LDAP URL.
 * <p>
 * TODO: referral from LDAP URL.
 * <p>
 * TODO: unmodifiable requests?
 * <p>
 * TODO: synchronized requests?
 * <p>
 * TODO: copy constructors.
 */
public final class Responses
{

  /**
   * Creates a new bind result using the provided result code.
   *
   * @param resultCode
   *          The result code.
   * @return The new bind result.
   * @throws NullPointerException
   *           If {@code resultCode} was {@code null}.
   */
  public static BindResult newBindResult(ResultCode resultCode)
      throws NullPointerException
  {
    Validator.ensureNotNull(resultCode);
    return new BindResultImpl(resultCode);
  }



  /**
   * Creates a new compare result using the provided result code.
   *
   * @param resultCode
   *          The result code.
   * @return The new compare result.
   * @throws NullPointerException
   *           If {@code resultCode} was {@code null}.
   */
  public static CompareResult newCompareResult(ResultCode resultCode)
      throws NullPointerException
  {
    Validator.ensureNotNull(resultCode);
    return new CompareResultImpl(resultCode);
  }



  /**
   * Creates a new generic extended result using the provided result
   * code.
   *
   * @param resultCode
   *          The result code.
   * @return The new generic extended result.
   * @throws NullPointerException
   *           If {@code resultCode} was {@code null}.
   */
  public static GenericExtendedResult newGenericExtendedResult(
      ResultCode resultCode) throws NullPointerException
  {
    Validator.ensureNotNull(resultCode);
    return new GenericExtendedResultImpl(resultCode);
  }



  /**
   * Creates a new generic intermediate response with no name or value.
   *
   * @return The new generic intermediate response.
   */
  public static GenericIntermediateResponse newGenericIntermediateResponse()
  {
    return new GenericIntermediateResponseImpl(null, null);
  }



  /**
   * Creates a new generic intermediate response using the provided
   * response name and value.
   *
   * @param responseName
   *          The dotted-decimal representation of the unique OID
   *          corresponding to this intermediate response, which may be
   *          {@code null} indicating that none was provided.
   * @param responseValue
   *          The response value associated with this generic
   *          intermediate response, which may be {@code null}
   *          indicating that none was provided.
   * @return The new generic intermediate response.
   */
  public static GenericIntermediateResponse newGenericIntermediateResponse(
      String responseName, ByteString responseValue)
  {
    return new GenericIntermediateResponseImpl(responseName,
        responseValue);
  }



  /**
   * Creates a new result using the provided result code.
   *
   * @param resultCode
   *          The result code.
   * @return The new result.
   * @throws NullPointerException
   *           If {@code resultCode} was {@code null}.
   */
  public static Result newResult(ResultCode resultCode)
      throws NullPointerException
  {
    Validator.ensureNotNull(resultCode);
    return new ResultImpl(resultCode);
  }



  /**
   * Creates a new search result entry using the provided distinguished
   * name.
   *
   * @param name
   *          The distinguished name of the entry.
   * @return The new search result entry.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static SearchResultEntry newSearchResultEntry(DN name)
      throws NullPointerException
  {
    final Entry entry = new SortedEntry().setName(name);
    return new SearchResultEntryImpl(entry);
  }



  /**
   * Creates a new search result entry backed by the provided entry.
   * Modifications made to {@code entry} will be reflected in the
   * returned search result entry. The returned search result entry
   * supports updates to its list of controls, as well as updates to the
   * name and attributes if the underlying entry allows.
   *
   * @param entry
   *          The entry.
   * @return The new search result entry.
   * @throws NullPointerException
   *           If {@code entry} was {@code null} .
   */
  public static SearchResultEntry newSearchResultEntry(Entry entry)
      throws NullPointerException
  {
    Validator.ensureNotNull(entry);
    return new SearchResultEntryImpl(entry);
  }



  /**
   * Creates a new search result entry using the provided distinguished
   * name decoded using the default schema.
   *
   * @param name
   *          The distinguished name of the entry.
   * @return The new search result entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code name} could not be decoded using the default
   *           schema.
   * @throws NullPointerException
   *           If {@code name} was {@code null}.
   */
  public static SearchResultEntry newSearchResultEntry(String name)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    final Entry entry = new SortedEntry().setName(name);
    return new SearchResultEntryImpl(entry);
  }



  /**
   * Creates a new search result entry using the provided lines of LDIF
   * decoded using the default schema.
   *
   * @param ldifLines
   *          Lines of LDIF containing an LDIF add change record or an
   *          LDIF entry record.
   * @return The new search result entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ldifLines} was empty, or contained invalid
   *           LDIF, or could not be decoded using the default schema.
   * @throws NullPointerException
   *           If {@code ldifLines} was {@code null} .
   */
  public static SearchResultEntry newSearchResultEntry(
      String... ldifLines) throws LocalizedIllegalArgumentException,
      NullPointerException
  {
    return newSearchResultEntry(new SortedEntry(ldifLines));
  }



  /**
   * Creates a new search result reference using the provided
   * continuation reference URI.
   *
   * @param uri
   *          The first continuation reference URI to be added to this
   *          search result reference.
   * @return The new search result reference.
   * @throws NullPointerException
   *           If {@code uri} was {@code null}.
   */
  public static SearchResultReference newSearchResultReference(
      String uri) throws NullPointerException
  {
    Validator.ensureNotNull(uri);
    return new SearchResultReferenceImpl(uri);
  }



  // Private constructor.
  private Responses()
  {
    // Prevent instantiation.
  }
}
