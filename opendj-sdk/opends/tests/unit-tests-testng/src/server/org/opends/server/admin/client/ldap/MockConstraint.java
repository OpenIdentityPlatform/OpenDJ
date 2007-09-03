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
package org.opends.server.admin.client.ldap;



import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.ClientConstraintHandler;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagementContext;



/**
 * A mock constraint which can be configured to refuse various types
 * of operation.
 */
public final class MockConstraint implements Constraint {

  /**
   * Mock client constraint handler.
   */
  private class Handler extends ClientConstraintHandler {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAddAcceptable(ManagementContext context,
        ManagedObject<?> managedObject, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      if (!allowAdds) {
        unacceptableReasons.add(Message.raw("Adds not allowed"));
      }

      return allowAdds;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDeleteAcceptable(ManagementContext context,
        ManagedObjectPath<?, ?> path, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      if (!allowDeletes) {
        unacceptableReasons.add(Message.raw("Deletes not allowed"));
      }

      return allowDeletes;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModifyAcceptable(ManagementContext context,
        ManagedObject<?> managedObject, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      if (!allowModifies) {
        unacceptableReasons.add(Message.raw("Modifies not allowed"));
      }

      return allowModifies;
    }

  }

  // Determines if add operations are allowed.
  private final boolean allowAdds;

  // Determines if modify operations are allowed.
  private final boolean allowModifies;

  // Determines if delete operations are allowed.
  private final boolean allowDeletes;



  /**
   * Creates a new mock constraint.
   *
   * @param allowAdds
   *          Determines if add operations are allowed.
   * @param allowModifies
   *          Determines if modify operations are allowed.
   * @param allowDeletes
   *          Determines if delete operations are allowed.
   */
  public MockConstraint(boolean allowAdds, boolean allowModifies,
      boolean allowDeletes) {
    this.allowAdds = allowAdds;
    this.allowModifies = allowModifies;
    this.allowDeletes = allowDeletes;
  }



  /**
   * {@inheritDoc}
   */
  public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
    return Collections.<ClientConstraintHandler> singleton(new Handler());
  }

}
