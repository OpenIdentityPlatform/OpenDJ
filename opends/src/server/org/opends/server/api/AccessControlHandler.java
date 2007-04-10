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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.core.*;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.Entry;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server access control handler.  All
 * methods in this class should take the entire request into account
 * when making the determination, including any request controls that
 * might have been provided.
 */
public abstract class AccessControlHandler
{

  /**
   * Indicates whether the provided add operation is allowed based on
   * the access control configuration.  This method should not alter
   * the provided add operation in any way.
   *
   * @param  addOperation  The operation for which to make the
   *                       determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(AddOperation addOperation);



  /**
   * Indicates whether the provided bind operation is allowed based on
   * the access control configuration.  This method should not alter
   * the provided bind operation in any way.
   *
   * @param  bindOperation  The operation for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(BindOperation bindOperation);



  /**
   * Indicates whether the provided compare operation is allowed based
   * on the access control configuration.  This method should not
   * alter the provided compare operation in any way.
   *
   * @param  compareOperation  The operation for which to make the
   *                           determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(CompareOperation
                                         compareOperation);



  /**
   * Indicates whether the provided delete operation is allowed based
   * on the access control configuration.  This method should not
   * alter the provided delete operation in any way.
   *
   * @param  deleteOperation  The operation for which to make the
   *                          determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(DeleteOperation deleteOperation);



  /**
   * Indicates whether the provided extended operation is allowed
   * based on the access control configuration.  This method should
   * not alter the provided extended operation in any way.
   *
   * @param  extendedOperation  The operation for which to make the
   *                            determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(ExtendedOperation
                                         extendedOperation);



  /**
   * Indicates whether the provided modify operation is allowed based
   * on the access control configuration.  This method should not
   * alter the provided modify operation in any way.
   *
   * @param  modifyOperation  The operation for which to make the
   *                          determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(ModifyOperation modifyOperation);



  /**
   * Indicates whether the provided modify DN operation is allowed
   * based on the access control configuration.  This method should
   * not alter the provided modify DN operation in any way.
   *
   * @param  modifyDNOperation  The operation for which to make the
   *                            determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(ModifyDNOperation
                                         modifyDNOperation);



  /**
   * Indicates whether the provided search operation is allowed based
   * on the access control configuration.  This method may only alter
   * the provided search operation in order to add an opaque block of
   * data to it that will be made available for use in determining
   * whether matching search result entries or search result
   * references may be allowed.
   *
   * @param  searchOperation  The operation for which to make the
   *                          determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean isAllowed(SearchOperation searchOperation);



  /**
   * Indicates whether the provided search result entry may be sent to
   * the client. Implementations <b>must not under any
   * circumstances</b> modify the search entry in any way.
   *
   * @param  searchOperation  The search operation with which the
   *                          provided entry is associated.
   * @param  searchEntry      The search result entry for which to
   *                          make the determination.
   *
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *          the access control configuration, or <CODE>false</CODE>
   *          if not.
   */
  public abstract boolean maySend(SearchOperation searchOperation,
                                  SearchResultEntry searchEntry);



  /**
   * Filter the contents of the provided entry such that it no longer
   * contains any attributes or values that the client is not
   * permitted to access.
   *
   * @param searchOperation The search operation with which the
   *                        provided entry is associated.
   * @param searchEntry     The search result entry to be filtered.
   *
   * @return  Returns the entry with filtered attributes and values
   *          removed.
   */
  public abstract SearchResultEntry filterEntry(
      SearchOperation searchOperation, SearchResultEntry searchEntry);



  /**
   * Indicates whether the provided search result reference may be
   * sent to the client.
   *
   * @param searchOperation
   *          The search operation with which the provided reference
   *          is associated.
   * @param searchReference
   *          The search result reference for which to make the
   *          determination.
   * @return <CODE>true</CODE> if the operation should be allowed by
   *         the access control configuration, or <CODE>false</CODE>
   *         if not.
   */
  public abstract boolean maySend(SearchOperation searchOperation,
                               SearchResultReference searchReference);

  /**
   * Indicates whether a proxied authorization control is allowed
   * based on the current operation and the new authorization
   * entry.
   *
   * @param operation
   *        The operation with which the proxied authorization
   *        control is associated.
   * @param newAuthorizationEntry
   *        The new authorization entry related to the
   *        proxied authorization control authorization ID.
   * @return  <CODE>true</CODE> if the operation should be allowed by
   *         the access control configuration, or <CODE>false</CODE>
   *         if not.
   */
  public abstract boolean isProxiedAuthAllowed(Operation operation,
                                        Entry newAuthorizationEntry);
}

