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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.RDN;



/**
 * This class defines a set of methods that are available for use by
 * subordinate modify DN operation plugins.  Note that this interface
 * is intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
public interface SubordinateModifyDNOperation
       extends InProgressOperation
{
  /**
   * Retrieves the raw, unprocessed entry DN as included in the client
   * request.  The DN that is returned may or may not be a valid DN,
   * since no validation will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client
   *          request.
   */
  public ByteString getRawEntryDN();



  /**
   * Retrieves the DN of the entry to rename.  This should not be
   * called by pre-parse plugins because the processed DN will not be
   * available yet.  Instead, they should call the
   * {@code getRawEntryDN} method.
   *
   * @return  The DN of the entry to rename, or {@code null} if the
   *          raw entry DN has not yet been processed.
   */
  public DN getEntryDN();



  /**
   * Retrieves the raw, unprocessed newRDN as included in the request
   * from the client.  This may or may not contain a valid RDN, as no
   * validation will have been performed on it.
   *
   * @return  The raw, unprocessed newRDN as included in the request
   *          from the client.
   */
  public ByteString getRawNewRDN();



  /**
   * Retrieves the new RDN to use for the entry.  This should not be
   * called by pre-parse plugins, because the processed newRDN will
   * not yet be available.  Pre-parse plugins should instead use the
   * {@code getRawNewRDN} method.
   *
   * @return  The new RDN to use for the entry, or {@code null} if the
   *          raw newRDN has not yet been processed.
   */
  public RDN getNewRDN();



  /**
   * Indicates whether the current RDN value should be removed from
   * the entry.
   *
   * @return  {@code true} if the current RDN value should be removed
   *          from the entry, or {@code false} if not.
   */
  public boolean deleteOldRDN();



  /**
   * Retrieves the raw, unprocessed newSuperior from the client
   * request.  This may or may not contain a valid DN, as no
   * validation will have been performed on it.
   *
   * @return  The raw, unprocessed newSuperior from the client
   *          request, or {@code null} if there is none.
   */
  public ByteString getRawNewSuperior();



  /**
   * Retrieves the newSuperior DN for the entry.  This should not be
   * called by pre-parse plugins, because the processed DN will not
   * yet be available at that time.  Instead, they should use the
   * {@code getRawNewSuperior} method.
   *
   * @return  The newSuperior DN for the entry, or {@code null} if
   *          there is no newSuperior DN for this request or if the
   *          raw newSuperior has not yet been processed.
   */
  public DN getNewSuperior();



  /**
   * Retrieves the current entry, before it is renamed.  This will not
   * be available to pre-parse plugins or during the conflict
   * resolution portion of the synchronization processing.
   *
   * @return  The current entry, or {@code null} if it is not yet
   *           available.
   */
  public Entry getOriginalEntry();



  /**
   * Retrieves the new entry, as it will appear after it is renamed.
   * This will not be  available to pre-parse plugins or during the
   * conflict resolution portion of the synchronization processing.
   *
   * @return  The updated entry, or {@code null} if it is not yet
   *           available.
   */
  public Entry getUpdatedEntry();
}

