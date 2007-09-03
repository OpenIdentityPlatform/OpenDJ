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
package org.opends.server.admin.server;



import java.util.Collection;

import org.opends.messages.Message;
import org.opends.server.config.ConfigException;



/**
 * An interface for performing server-side constraint validation.
 * <p>
 * Constraints are evaluated immediately before and after write
 * operations are performed. Server-side constraints are evaluated in
 * two phases: the first phase determines if the proposed add, delete,
 * or modification is acceptable according to the constraint. If one
 * or more constraints fails, the write write operation is refused,
 * and the client will receive an
 * <code>OperationRejectedException</code> exception. The second
 * phase is invoked once the add, delete, or modification request has
 * been allowed and any changes applied. The second phase gives the
 * constraint handler a chance to register listener call-backs if
 * required.
 * <p>
 * A server constraint handler must override at least one of the
 * provided methods.
 *
 * @see org.opends.server.admin.Constraint
 */
public abstract class ServerConstraintHandler {

  /**
   * Creates a new server constraint handler.
   */
  protected ServerConstraintHandler() {
    // No implementation required.
  }



  /**
   * Determines whether or not the newly created managed object which
   * is about to be added to the server configuration satisfies this
   * constraint.
   * <p>
   * If the constraint is not satisfied, the implementation must
   * return <code>false</code> and add a message describing why the
   * constraint was not satisfied.
   * <p>
   * The default implementation is to return <code>true</code>.
   *
   * @param managedObject
   *          The new managed object.
   * @param unacceptableReasons
   *          A list of messages to which error messages should be
   *          added.
   * @return Returns <code>true</code> if this constraint is
   *         satisfied, or <code>false</code> if it is not.
   * @throws ConfigException
   *           If an configuration exception prevented this constraint
   *           from being evaluated.
   */
  public boolean isAddAcceptable(ServerManagedObject<?> managedObject,
      Collection<Message> unacceptableReasons) throws ConfigException {
    return true;
  }



  /**
   * Determines whether or not the existing managed object which is
   * about to be deleted from the server configuration satisfies this
   * constraint.
   * <p>
   * If the constraint is not satisfied, the implementation must
   * return <code>false</code> and add a message describing why the
   * constraint was not satisfied.
   * <p>
   * The default implementation is to return <code>true</code>.
   *
   * @param managedObject
   *          The managed object which is about to be deleted.
   * @param unacceptableReasons
   *          A list of messages to which error messages should be
   *          added.
   * @return Returns <code>true</code> if this constraint is
   *         satisfied, or <code>false</code> if it is not.
   * @throws ConfigException
   *           If an configuration exception prevented this constraint
   *           from being evaluated.
   */
  public boolean isDeleteAcceptable(ServerManagedObject<?> managedObject,
      Collection<Message> unacceptableReasons) throws ConfigException {
    return true;
  }



  /**
   * Determines whether or not the changes to an existing managed
   * object which are about to be committed to the server
   * configuration satisfies this constraint.
   * <p>
   * If the constraint is not satisfied, the implementation must
   * return <code>false</code> and add a message describing why the
   * constraint was not satisfied.
   * <p>
   * The default implementation is to return <code>true</code>.
   *
   * @param managedObject
   *          The modified managed object.
   * @param unacceptableReasons
   *          A list of messages to which error messages should be
   *          added.
   * @return Returns <code>true</code> if this modify is satisfied,
   *         or <code>false</code> if it is not.
   * @throws ConfigException
   *           If an configuration exception prevented this constraint
   *           from being evaluated.
   */
  public boolean isModifyAcceptable(ServerManagedObject<?> managedObject,
      Collection<Message> unacceptableReasons) throws ConfigException {
    return true;
  }



  /**
   * Perform any add post-condition processing.
   * <p>
   * The default implementation is to do nothing.
   *
   * @param managedObject
   *          The managed object which was added.
   * @throws ConfigException
   *           If the post-condition processing fails due to a
   *           configuration exception.
   */
  public void performAddPostCondition(ServerManagedObject<?> managedObject)
      throws ConfigException {
    // Do nothing.
  }



  /**
   * Perform any delete post-condition processing.
   * <p>
   * The default implementation is to do nothing.
   *
   * @param managedObject
   *          The managed object which was deleted.
   * @throws ConfigException
   *           If the post-condition processing fails due to a
   *           configuration exception.
   */
  public void performDeletePostCondition(ServerManagedObject<?> managedObject)
      throws ConfigException {
    // Do nothing.
  }



  /**
   * Perform any modify post-condition processing.
   * <p>
   * The default implementation is to do nothing.
   *
   * @param managedObject
   *          The managed object which was modified.
   * @throws ConfigException
   *           If the post-condition processing fails due to a
   *           configuration exception.
   */
  public void performModifyPostCondition(ServerManagedObject<?> managedObject)
      throws ConfigException {
    // Do nothing.
  }
}
