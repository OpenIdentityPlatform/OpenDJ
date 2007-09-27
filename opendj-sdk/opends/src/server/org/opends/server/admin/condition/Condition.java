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
package org.opends.server.admin.condition;



import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.config.ConfigException;



/**
 * An interface for evaluating conditions.
 */
public interface Condition {

  /**
   * Initializes this condition.
   *
   * @param d
   *          The abstract managed object definition associated with
   *          this condition.
   * @throws Exception
   *           If this condition could not be initialized.
   */
  void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception;



  /**
   * Evaluates this condition against the provided client managed
   * object.
   *
   * @param context
   *          The client management context.
   * @param managedObject
   *          The client managed object.
   * @return Returns <code>true</code> if this condition is
   *         satisfied.
   * @throws AuthorizationException
   *           If the condition could not be evaluated due to an
   *           authorization problem.
   * @throws CommunicationException
   *           If the condition could not be evaluated due to an
   *           communication problem.
   */
  boolean evaluate(ManagementContext context, ManagedObject<?> managedObject)
      throws AuthorizationException, CommunicationException;



  /**
   * Evaluates this condition against the provided server managed
   * object.
   *
   * @param managedObject
   *          The server managed object.
   * @return Returns <code>true</code> if this condition is
   *         satisfied.
   * @throws ConfigException
   *           If the condition could not be evaluated due to an
   *           unexpected configuration exception.
   */
  boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException;
}
