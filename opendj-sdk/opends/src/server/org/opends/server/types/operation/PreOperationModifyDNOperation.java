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
package org.opends.server.types.operation;



import java.util.List;

import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;



/**
 * This class defines a set of methods that are available for use by
 * pre-operation plugins for modify DN operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreOperationModifyDNOperation
       extends PreOperationOperation
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
   * <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to rename, or <CODE>null</CODE> if
   *          the raw entry DN has not yet been processed.
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
   * <CODE>getRawNewRDN</CODE> method.
   *
   * @return  The new RDN to use for the entry, or <CODE>null</CODE>
   *          if the raw newRDN has not yet been processed.
   */
  public RDN getNewRDN();



  /**
   * Indicates whether the current RDN value should be removed from
   * the entry.
   *
   * @return  <CODE>true</CODE> if the current RDN value should be
   *          removed from the entry, or <CODE>false</CODE> if not.
   */
  public boolean deleteOldRDN();



  /**
   * Retrieves the raw, unprocessed newSuperior from the client
   * request.  This may or may not contain a valid DN, as no
   * validation will have been performed on it.
   *
   * @return  The raw, unprocessed newSuperior from the client
   *          request, or <CODE>null</CODE> if there is none.
   */
  public ByteString getRawNewSuperior();



  /**
   * Retrieves the newSuperior DN for the entry.  This should not be
   * called by pre-parse plugins, because the processed DN will not
   * yet be available at that time.  Instead, they should use the
   * <CODE>getRawNewSuperior</CODE> method.
   *
   * @return  The newSuperior DN for the entry, or <CODE>null</CODE>
   *          if there is no newSuperior DN for this request or if the
   *          raw newSuperior has not yet been processed.
   */
  public DN getNewSuperior();



  /**
   * Retrieves the set of modifications applied to attributes of the
   * target entry in the course of processing this modify DN
   * operation.  This will include attribute-level changes from the
   * modify DN itself (e.g., removing old RDN values if deleteOldRDN
   * is set, or adding new RDN values that don't already exist), but
   * it may also be used by pre-operation plugins to cause additional
   * changes in the entry.  In this case, those plugins may add
   * modifications to this list through the
   * <CODE>addModification</CODE> method (the list returned from this
   * method should not be modified directly) if any changes should be
   * processed in addition to the core modify DN processing.  Backends
   * may read this list to identify which attribute-level changes were
   * applied in order to more easily apply updates to attribute
   * indexes.
   *
   * @return  The set of modifications applied to attributes during
   *          the course of the modify DN processing, or
   *          <CODE>null</CODE> if that information is not yet
   *          available (e.g., during pre-parse plugins).
   */
  public List<Modification> getModifications();



  /**
   * Adds the provided modification to the set of modifications to be
   * applied as part of the update.  This should only be called by
   * pre-operation plugins.
   *
   * @param  modification  The modification to add to the set of
   *                       modifications to apply to the entry.
   */
  public void addModification(Modification modification);



  /**
   * Retrieves the current entry, before it is renamed.  This will not
   * be available to pre-parse plugins or during the conflict
   * resolution portion of the synchronization processing.
   *
   * @return  The current entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public Entry getOriginalEntry();



  /**
   * Retrieves the new entry, as it will appear after it is renamed.
   * This will not be  available to pre-parse plugins or during the
   * conflict resolution portion of the synchronization processing.
   *
   * @return  The updated entry, or <CODE>null</CODE> if it is not yet
   *           available.
   */
  public Entry getUpdatedEntry();
}

