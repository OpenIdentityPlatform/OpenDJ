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
package org.opends.server.admin;



import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.opends.messages.Message;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.ClientConstraintHandler;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.condition.Condition;
import org.opends.server.admin.server.ServerConstraintHandler;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.config.ConfigException;



/**
 * A generic constraint which comprises of an underlying condition and
 * a description. The condition must evaluate to <code>true</code>
 * in order for a new managed object to be created or modified.
 */
public class GenericConstraint extends Constraint {

  /**
   * The client-side constraint handler.
   */
  private class ClientHandler extends ClientConstraintHandler {

    // Private constructor.
    private ClientHandler() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAddAcceptable(ManagementContext context,
        ManagedObject<?> managedObject, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      if (!condition.evaluate(context, managedObject)) {
        unacceptableReasons.add(getSynopsis());
        return false;
      } else {
        return true;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModifyAcceptable(ManagementContext context,
        ManagedObject<?> managedObject, Collection<Message> unacceptableReasons)
        throws AuthorizationException, CommunicationException {
      if (!condition.evaluate(context, managedObject)) {
        unacceptableReasons.add(getSynopsis());
        return false;
      } else {
        return true;
      }
    }

  };



  /**
   * The server-side constraint handler.
   */
  private class ServerHandler extends ServerConstraintHandler {

    // Private constructor.
    private ServerHandler() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUsable(ServerManagedObject<?> managedObject,
        Collection<Message> unacceptableReasons) throws ConfigException {
      if (!condition.evaluate(managedObject)) {
        unacceptableReasons.add(getSynopsis());
        return false;
      } else {
        return true;
      }
    }

  };

  // The client-side constraint handler.
  private final ClientConstraintHandler clientHandler = new ClientHandler();

  // The condition associated with this constraint.
  private final Condition condition;

  // The managed object definition associated with this constraint.
  private final AbstractManagedObjectDefinition<?, ?> definition;

  // The constraint ID.
  private final int id;

  // The server-side constraint handler.
  private final ServerConstraintHandler serverHandler = new ServerHandler();



  /**
   * Creates a new generic constraint.
   *
   * @param definition
   *          The managed object definition associated with this
   *          constraint.
   * @param id
   *          The constraint ID.
   * @param condition
   *          The condition associated with this constraint.
   */
  public GenericConstraint(AbstractManagedObjectDefinition<?, ?> definition,
      int id, Condition condition) {
    this.definition = definition;
    this.id = id;
    this.condition = condition;
  }



  /**
   * {@inheritDoc}
   */
  public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
    return Collections.singleton(clientHandler);
  }



  /**
   * {@inheritDoc}
   */
  public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
    return Collections.singleton(serverHandler);
  }



  /**
   * Gets the synopsis of this constraint in the default locale.
   *
   * @return Returns the synopsis of this constraint in the default
   *         locale.
   */
  public final Message getSynopsis() {
    return getSynopsis(Locale.getDefault());
  }



  /**
   * Gets the synopsis of this constraint in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the synopsis of this constraint in the specified
   *         locale.
   */
  public final Message getSynopsis(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "constraint." + id + ".synopsis";
    return resource.getMessage(definition, property, locale);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected void initialize() throws Exception {
    condition.initialize(definition);
  }

}
