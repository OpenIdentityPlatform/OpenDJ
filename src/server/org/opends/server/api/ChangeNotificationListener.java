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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.server.types.Entry;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.
            PostResponseModifyDNOperation;



/**
 * This interface defines a mechanism that Directory Server components
 * may use if they need to be notified of changes that are made in the
 * Directory Server.  Similar functionality can be achieved using
 * post-response plugins, but this interface is better suited to core
 * functionality that should not be considered optional, since plugins
 * may be disabled.  Further, change notification listeners will only
 * be invoked for successful operations.
 * <BR><BR>
 * Each change notification listener will be notified whenever an
 * update is made in the server (just before the response is sent to
 * the client), so the listener should use a very efficient mechanism
 * for determining whether or not any action is required for the
 * associated operation and quickly return for cases in which the
 * update is not applicable.  Also note that even though the listener
 * will be invoked before the response is sent to the client, it may
 * not alter that response in any way and therefore the listener will
 * be given what is essentially a read-only view of the associated
 * operation.
 * <BR><BR>
 * Note that while this interface can be used by clients to be
 * notified of changes to the configuration data just as easily as it
 * can be used for any other entry anywhere in the server, components
 * that are only interested in being notified of changes to the server
 * configuration should use the {@code ConfigAddListener},
 * {@code ConfigDeleteListener}, and/or the
 * {@code ConfigChangeListener} interfaces instead.  They will be more
 * efficient overall because they will only be invoked for operations
 * in the server configuration, and then only for the specific entries
 * with which the component has registered.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface ChangeNotificationListener
{
  /**
   * Performs any processing that may be required after an add
   * operation.
   *
   * @param  addOperation  The add operation that was performed in the
   *                       server.
   * @param  entry         The entry that was added to the server.
   */
  public void handleAddOperation(
                   PostResponseAddOperation addOperation,
                   Entry entry);



  /**
   * Performs any processing that may be required after a delete
   * operation.
   *
   * @param  deleteOperation  The delete operation that was performed
   *                          in the server.
   * @param  entry            The entry that was removed from the
   *                          server.
   */
  public void handleDeleteOperation(
                   PostResponseDeleteOperation deleteOperation,
                   Entry entry);



  /**
   * Performs any processing that may be required after a modify
   * operation.
   *
   * @param  modifyOperation  The modify operation that was performed
   *                          in the server.
   * @param  oldEntry         The entry before it was updated.
   * @param  newEntry         The entry after it was updated.
   */
  public void handleModifyOperation(
                   PostResponseModifyOperation modifyOperation,
                   Entry oldEntry, Entry newEntry);



  /**
   * Performs any processing that may be required after a modify DN
   * operation.
   *
   * @param  modifyDNOperation  The modify DN operation that was
   *                            performed in the server.
   * @param  oldEntry           The entry before it was updated.
   * @param  newEntry           The entry after it was updated.
   */
  public void handleModifyDNOperation(
                   PostResponseModifyDNOperation modifyDNOperation,
                   Entry oldEntry, Entry newEntry);
}

