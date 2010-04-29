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
package org.opends.server.api;



import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;



/**
 * This interface defines a mechanism that Directory Server components
 * may use if they need to be notified of changes that are being made
 * to subentries in the Directory Server.
 * <BR><BR>
 * Each change listener will be notified whenever an update is being
 * made to subentry in the server, so the listener should use a very
 * efficient mechanism for determining whether or not any action is
 * required for the associated operation and quickly return for cases
 * in which the update is not applicable.
 * <BR><BR>
 * The interface consists of two separate classes of methods. Check
 * methods are invoked to verify that a specific operation performed
 * on subentry is acceptable and if so the server may proceed with
 * that operation further. Handle methods are invoked to notify that
 * specific operation has occured on subentry thus serving purely as
 * notification mechanism. While Check methods can affect the outcome
 * of given operation Handle methods cannot affect the operation out-
 * come in any way. Also note that Handle methods are invoked before
 * any actual operation response to the client is sent.
 * <BR><BR>
 * This interface is intended for the server components that either
 * require to track changes to subentries within the server or need
 * to evaluate and take actions on specific changes being made to
 * subentries within the server. Eg server components implementing
 * their configuration objects as administrative subentries.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface SubentryChangeListener
{
  /**
   * Performs any checking that may be required before
   * subentry add operation.
   * @param entry subentry being added to the server.
   * @throws DirectoryException if operation is not
   *         acceptable for this subentry.
   */
  public void checkSubentryAddAcceptable(Entry entry)
          throws DirectoryException;

  /**
   * Performs any checking that may be required before
   * subentry delete operation.
   * @param entry subentry being deleted in the server.
   * @throws DirectoryException if operation is not
   *         acceptable for this subentry.
   */
  public void checkSubentryDeleteAcceptable(Entry entry)
          throws DirectoryException;

  /**
   * Performs any checking that may be required before
   * subentry modify operation.
   * @param oldEntry subentry being modified in the server.
   * @param newEntry subentry with modifications applied.
   * @throws DirectoryException if operation is not
   *         acceptable for this subentry.
   */
  public void checkSubentryModifyAcceptable(Entry oldEntry,
          Entry newEntry) throws DirectoryException;

  /**
   * Performs any checking that may be required before
   * subentry modify DN operation.
   * @param oldEntry subentry being modified in the server.
   * @param newEntry subentry with modifications applied.
   * @throws DirectoryException if operation is not
   *         acceptable for this subentry.
   */
  public void checkSubentryModifyDNAcceptable(Entry oldEntry,
          Entry newEntry) throws DirectoryException;

  /**
   * Performs any processing that may be required after a
   * subentry add operation.
   *
   * @param entry The subentry that was added to the
   *              server.
   */
  public void handleSubentryAdd(Entry entry);

  /**
   * Performs any processing that may be required after a
   * subentry delete operation.
   *
   * @param entry The subentry that was removed from the
   *              server.
   */
  public void handleSubentryDelete(Entry entry);

  /**
   * Performs any processing that may be required after a
   * subentry modify operation.
   *
   * @param oldEntry The subentry before it was updated.
   * @param newEntry The subentry after it was updated.
   */
  public void handleSubentryModify(Entry oldEntry, Entry newEntry);

  /**
   * Performs any processing that may be required after a
   * subentry modify DN operation.
   *
   * @param oldEntry The subentry before it was updated.
   * @param newEntry The subentry after it was updated.
   */
  public void handleSubentryModifyDN(Entry oldEntry, Entry newEntry);
}
