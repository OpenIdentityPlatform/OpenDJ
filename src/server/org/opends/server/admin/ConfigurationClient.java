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

package org.opends.server.admin;



import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;



/**
 * A common base interface for all managed object configuration
 * clients.
 */
public interface ConfigurationClient {

  /**
   * Get the configuration definition associated with this
   * configuration.
   *
   * @return Returns the configuration definition associated with this
   *         configuration.
   */
  ManagedObjectDefinition<? extends ConfigurationClient,
      ? extends Configuration> definition();



  /**
   * Get a property provider view of this configuration.
   *
   * @return Returns a property provider view of this configuration.
   */
  PropertyProvider properties();



  /**
   * If this is a new configuration this method will attempt to add it
   * to the server, otherwise it will commit any changes made to this
   * configuration.
   *
   * @throws ManagedObjectAlreadyExistsException
   *           If this is a new configuration but it could not be
   *           added to the server because it already exists.
   * @throws MissingMandatoryPropertiesException
   *           If this configuration contains some mandatory
   *           properties which have been left undefined.
   * @throws ConcurrentModificationException
   *           If this is a new configuration which is being added to
   *           the server but its parent has been removed by another
   *           client, or if this configuration is being modified but
   *           it has been removed from the server by another client.
   * @throws OperationRejectedException
   *           If the server refuses to add or modify this
   *           configuration due to some server-side constraint which
   *           cannot be satisfied.
   * @throws AuthorizationException
   *           If the server refuses to add or modify this
   *           configuration because the client does not have the
   *           correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  void commit() throws ManagedObjectAlreadyExistsException,
      MissingMandatoryPropertiesException, ConcurrentModificationException,
      OperationRejectedException, AuthorizationException,
      CommunicationException;

}
