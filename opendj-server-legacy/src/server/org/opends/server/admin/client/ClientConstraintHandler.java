/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.admin.client;



import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.ManagedObjectPath;



/**
 * An interface for performing client-side constraint validation.
 * <p>
 * Constraints are evaluated immediately before the client performs a
 * write operation. If one or more constraints fails, the write
 * operation is refused and fails with an
 * {@link OperationRejectedException}.
 * <p>
 * A client constraint handler must override at least one of the
 * provided methods.
 *
 * @see org.opends.server.admin.Constraint
 */
public abstract class ClientConstraintHandler {

  /**
   * Creates a new client constraint handler.
   */
  protected ClientConstraintHandler() {
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
   * @param context
   *          The management context.
   * @param managedObject
   *          The new managed object.
   * @param unacceptableReasons
   *          A list of messages to which error messages should be
   *          added.
   * @return Returns <code>true</code> if this constraint is
   *         satisfied, or <code>false</code> if it is not.
   * @throws AuthorizationException
   *           If an authorization failure prevented this constraint
   *           from being evaluated.
   * @throws CommunicationException
   *           If a communications problem prevented this constraint
   *           from being evaluated.
   */
  public boolean isAddAcceptable(ManagementContext context,
      ManagedObject<?> managedObject, Collection<LocalizableMessage> unacceptableReasons)
      throws AuthorizationException, CommunicationException {
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
   * @param context
   *          The management context.
   * @param managedObject
   *          The modified managed object.
   * @param unacceptableReasons
   *          A list of messages to which error messages should be
   *          added.
   * @return Returns <code>true</code> if this modify is satisfied,
   *         or <code>false</code> if it is not.
   * @throws AuthorizationException
   *           If an authorization failure prevented this constraint
   *           from being evaluated.
   * @throws CommunicationException
   *           If a communications problem prevented this constraint
   *           from being evaluated.
   */
  public boolean isModifyAcceptable(ManagementContext context,
      ManagedObject<?> managedObject, Collection<LocalizableMessage> unacceptableReasons)
      throws AuthorizationException, CommunicationException {
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
   * @param context
   *          The management context.
   * @param path
   *          The path of the managed object which is about to be
   *          deleted.
   * @param unacceptableReasons
   *          A list of messages to which error messages should be
   *          added.
   * @return Returns <code>true</code> if this constraint is
   *         satisfied, or <code>false</code> if it is not.
   * @throws AuthorizationException
   *           If an authorization failure prevented this constraint
   *           from being evaluated.
   * @throws CommunicationException
   *           If a communications problem prevented this constraint
   *           from being evaluated.
   */
  public boolean isDeleteAcceptable(ManagementContext context,
      ManagedObjectPath<?, ?> path, Collection<LocalizableMessage> unacceptableReasons)
      throws AuthorizationException, CommunicationException {
    return true;
  }
}
