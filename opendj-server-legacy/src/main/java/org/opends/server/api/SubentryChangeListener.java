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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
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
 * efficient mechanism for determining whether any action is
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
  void checkSubentryAddAcceptable(Entry entry)
          throws DirectoryException;

  /**
   * Performs any checking that may be required before
   * subentry delete operation.
   * @param entry subentry being deleted in the server.
   * @throws DirectoryException if operation is not
   *         acceptable for this subentry.
   */
  void checkSubentryDeleteAcceptable(Entry entry)
          throws DirectoryException;

  /**
   * Performs any checking that may be required before
   * subentry modify operation.
   * @param oldEntry subentry being modified in the server.
   * @param newEntry subentry with modifications applied.
   * @throws DirectoryException if operation is not
   *         acceptable for this subentry.
   */
  void checkSubentryModifyAcceptable(Entry oldEntry,
          Entry newEntry) throws DirectoryException;

  /**
   * Performs any checking that may be required before
   * subentry modify DN operation.
   * @param oldEntry subentry being modified in the server.
   * @param newEntry subentry with modifications applied.
   * @throws DirectoryException if operation is not
   *         acceptable for this subentry.
   */
  void checkSubentryModifyDNAcceptable(Entry oldEntry,
          Entry newEntry) throws DirectoryException;

  /**
   * Performs any processing that may be required after a
   * subentry add operation.
   *
   * @param entry The subentry that was added to the
   *              server.
   */
  void handleSubentryAdd(Entry entry);

  /**
   * Performs any processing that may be required after a
   * subentry delete operation.
   *
   * @param entry The subentry that was removed from the
   *              server.
   */
  void handleSubentryDelete(Entry entry);

  /**
   * Performs any processing that may be required after a
   * subentry modify operation.
   *
   * @param oldEntry The subentry before it was updated.
   * @param newEntry The subentry after it was updated.
   */
  void handleSubentryModify(Entry oldEntry, Entry newEntry);

  /**
   * Performs any processing that may be required after a
   * subentry modify DN operation.
   *
   * @param oldEntry The subentry before it was updated.
   * @param newEntry The subentry after it was updated.
   */
  void handleSubentryModifyDN(Entry oldEntry, Entry newEntry);
}
