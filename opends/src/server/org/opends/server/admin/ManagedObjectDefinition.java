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



import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.server.ServerManagedObject;



/**
 * Defines the structure of a managed object which can be
 * instantiated.
 *
 * @param <C>
 *          The type of client managed object configuration that this
 *          definition represents.
 * @param <S>
 *          The type of server managed object configuration that this
 *          definition represents.
 */
public abstract class ManagedObjectDefinition
    <C extends ConfigurationClient, S extends Configuration>
    extends AbstractManagedObjectDefinition<C, S> {

  /**
   * Create a new managed object definition.
   *
   * @param name
   *          The name of the definition.
   * @param parent
   *          The parent definition, or <code>null</code> if there
   *          is no parent.
   */
  protected ManagedObjectDefinition(String name,
      AbstractManagedObjectDefinition<? super C, ? super S> parent) {
    super(name, parent);
  }



  /**
   * Creates a client configuration view of the provided managed
   * object. Modifications made to the underlying managed object will
   * be reflected in the client configuration view and vice versa.
   *
   * @param managedObject
   *          The managed object.
   * @return Returns a client configuration view of the provided
   *         managed object.
   */
  public abstract C createClientConfiguration(
      ManagedObject<? extends C> managedObject);



  /**
   * Creates a server configuration view of the provided server
   * managed object.
   *
   * @param managedObject
   *          The server managed object.
   * @return Returns a server configuration view of the provided
   *         server managed object.
   */
  public abstract S createServerConfiguration(
      ServerManagedObject<? extends S> managedObject);



  /**
   * Gets the server configuration class instance associated with this
   * managed object definition.
   *
   * @return Returns the server configuration class instance
   *         associated with this managed object definition.
   */
  public abstract Class<S> getServerConfigurationClass();
}
