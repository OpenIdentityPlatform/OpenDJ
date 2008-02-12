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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.condition;



import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.config.ConfigException;
import org.opends.server.util.Validator;



/**
 * A condition which evaluates to <code>true</code> if the
 * sub-condition is <code>false</code>, or <code>false</code> if
 * the sub-condition is <code>true</code>.
 */
public final class NOTCondition implements Condition {

  // The single sub-condition.
  private final Condition condition;



  /**
   * Creates a new logical NOT condition with the provided
   * sub-condition.
   *
   * @param condition
   *          The sub-condition which will be inverted.
   */
  public NOTCondition(Condition condition) {
    Validator.ensureNotNull(condition);
    this.condition = condition;
  }



  /**
   * {@inheritDoc}
   */
  public boolean evaluate(ManagementContext context,
      ManagedObject<?> managedObject) throws AuthorizationException,
      CommunicationException {
    return !condition.evaluate(context, managedObject);
  }



  /**
   * {@inheritDoc}
   */
  public boolean evaluate(ServerManagedObject<?> managedObject)
      throws ConfigException {
    return !condition.evaluate(managedObject);
  }



  /**
   * {@inheritDoc}
   */
  public void initialize(AbstractManagedObjectDefinition<?, ?> d)
      throws Exception {
    condition.initialize(d);
  }

}
