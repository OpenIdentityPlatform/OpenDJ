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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.admin.server;



import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.client.ClientConstraintHandler;
import org.opends.server.config.ConfigEntry;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.testng.Assert;



/**
 * A mock constraint which can be configured to refuse various types
 * of operation.
 */
public final class MockConstraint extends Constraint {

  /**
   * Mock server constraint handler.
   */
  private class Handler extends ServerConstraintHandler {

    /** {@inheritDoc} */
    @Override
    public boolean isDeleteAllowed(ServerManagedObject<?> managedObject,
        Collection<LocalizableMessage> unacceptableReasons) throws ConfigException {
      if (!isDeleteAllowed) {
        unacceptableReasons
            .add(LocalizableMessage.raw("Configuration cannot be deleted."));
      }

      return isDeleteAllowed;
    }



    /** {@inheritDoc} */
    @Override
    public boolean isUsable(ServerManagedObject<?> managedObject,
        Collection<LocalizableMessage> unacceptableReasons) throws ConfigException {
      if (!isUsable) {
        unacceptableReasons.add(LocalizableMessage.raw("Configuration is not usable."));
      }

      return isUsable;
    }



    /** {@inheritDoc} */
    @Override
    public void performPostAdd(ServerManagedObject<?> managedObject)
        throws ConfigException {
      // Make sure that the associated config entry exists.
      DN targetDN = managedObject.getDN();
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(targetDN);
      Assert.assertNotNull(configEntry);
    }



    /** {@inheritDoc} */
    @Override
    public void performPostDelete(ServerManagedObject<?> managedObject)
        throws ConfigException {
      // Make sure that the associated config entry does not exist.
      DN targetDN = managedObject.getDN();
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(targetDN);
      Assert.assertNull(configEntry);
    }



    /** {@inheritDoc} */
    @Override
    public void performPostModify(ServerManagedObject<?> managedObject)
        throws ConfigException {
      // Make sure that the associated config entry exists.
      DN targetDN = managedObject.getDN();
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(targetDN);
      Assert.assertNotNull(configEntry);
    }

  }

  /** Determines if delete operations are allowed. */
  private final boolean isDeleteAllowed;

  /** Determines if configurations can be decoded. */
  private final boolean isUsable;



  /**
   * Creates a new mock constraint.
   *
   * @param isUsable
   *          Determines if configurations can be decoded.
   * @param isDeleteAllowed
   *          Determines if delete operations are allowed.
   */
  public MockConstraint(boolean isUsable, boolean isDeleteAllowed) {
    this.isUsable = isUsable;
    this.isDeleteAllowed = isDeleteAllowed;
  }



  /** {@inheritDoc} */
  public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
    return Collections.emptySet();
  }



  /** {@inheritDoc} */
  public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
    return Collections.<ServerConstraintHandler> singleton(new Handler());
  }

}
