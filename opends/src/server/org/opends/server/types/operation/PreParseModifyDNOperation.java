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



import org.opends.server.types.ByteString;



/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for modify DN operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
public interface PreParseModifyDNOperation
       extends PreParseOperation
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
   * Specifies the raw, unprocessed entry DN as included in the client
   * request.  This should only be called by pre-parse plugins.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in
   *                     the client request.
   */
  public void setRawEntryDN(ByteString rawEntryDN);



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
   * Specifies the raw, unprocessed newRDN as included in the request
   * from the client.  This should only be called by pre-parse plugins
   * and should not be used in later stages of processing.
   *
   * @param  rawNewRDN  The raw, unprocessed newRDN as included in the
   *                    request from the client.
   */
  public void setRawNewRDN(ByteString rawNewRDN);



  /**
   * Indicates whether the current RDN value should be removed from
   * the entry.
   *
   * @return  <CODE>true</CODE> if the current RDN value should be
   *          removed from the entry, or <CODE>false</CODE> if not.
   */
  public boolean deleteOldRDN();



  /**
   * Specifies whether the current RDN value should be removed from
   * the entry.
   *
   * @param  deleteOldRDN  Specifies whether the current RDN value
   *                       should be removed from the entry.
   */
  public void setDeleteOldRDN(boolean deleteOldRDN);



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
   * Specifies the raw, unprocessed newSuperior for this modify DN
   * operation, as provided in the request from the client.  This
   * method should only be called by pre-parse plugins.
   *
   * @param  rawNewSuperior  The raw, unprocessed newSuperior as
   *                         provided in the request from the client.
   */
  public void setRawNewSuperior(ByteString rawNewSuperior);
}

