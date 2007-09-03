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

package org.opends.server.admin.client;



import java.util.SortedSet;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.client.spi.Driver;
import org.opends.server.admin.std.client.RootCfgClient;



/**
 * Client management connection context.
 */
public abstract class ManagementContext {

  /**
   * Creates a new management context.
   */
  protected ManagementContext() {
    // No implementation required.
  }



  /**
   * Deletes the named instantiable child managed object from the
   * named parent managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The instantiable relation definition.
   * @param name
   *          The name of the child managed object to be removed.
   * @return Returns <code>true</code> if the named instantiable
   *         child managed object was found, or <code>false</code>
   *         if it was not found.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws OperationRejectedException
   *           If the managed object cannot be removed due to some
   *           client-side or server-side constraint which cannot be
   *           satisfied (for example, if it is referenced by another
   *           managed object).
   * @throws AuthorizationException
   *           If the server refuses to remove the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  boolean deleteManagedObject(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
      String name) throws IllegalArgumentException,
      ManagedObjectNotFoundException, OperationRejectedException,
      AuthorizationException, CommunicationException {
    return getDriver().deleteManagedObject(parent, rd, name);
  }



  /**
   * Deletes the optional child managed object from the named parent
   * managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The optional relation definition.
   * @return Returns <code>true</code> if the optional child managed
   *         object was found, or <code>false</code> if it was not
   *         found.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws OperationRejectedException
   *           If the managed object cannot be removed due to some
   *           client-side or server-side constraint which cannot be
   *           satisfied (for example, if it is referenced by another
   *           managed object).
   * @throws AuthorizationException
   *           If the server refuses to remove the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  boolean deleteManagedObject(
      ManagedObjectPath<?, ?> parent, OptionalRelationDefinition<C, S> rd)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      OperationRejectedException, AuthorizationException,
      CommunicationException {
    return getDriver().deleteManagedObject(parent, rd);
  }



  /**
   * Gets the named managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param path
   *          The path of the managed object.
   * @return Returns the named managed object.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the managed object was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getManagedObject(
      ManagedObjectPath<C, S> path) throws DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException {
    return getDriver().getManagedObject(path);
  }



  /**
   * Gets the effective value of a property in the named managed
   * object.
   *
   * @param <PD>
   *          The type of the property to be retrieved.
   * @param path
   *          The path of the managed object containing the property.
   * @param pd
   *          The property to be retrieved.
   * @return Returns the property's effective value, or
   *         <code>null</code> if there are no values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with the
   *           referenced managed object's definition.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws PropertyException
   *           If the managed object was found but the requested
   *           property could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <PD> PD getPropertyValue(ManagedObjectPath<?, ?> path,
      PropertyDefinition<PD> pd) throws IllegalArgumentException,
      DefinitionDecodingException, AuthorizationException,
      ManagedObjectNotFoundException, CommunicationException,
      PropertyException {
    return getDriver().getPropertyValue(path, pd);
  }



  /**
   * Gets the effective values of a property in the named managed
   * object.
   *
   * @param <PD>
   *          The type of the property to be retrieved.
   * @param path
   *          The path of the managed object containing the property.
   * @param pd
   *          The property to be retrieved.
   * @return Returns the property's effective values, or an empty set
   *         if there are no values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with the
   *           referenced managed object's definition.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws PropertyException
   *           If the managed object was found but the requested
   *           property could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <PD> SortedSet<PD> getPropertyValues(
      ManagedObjectPath<?, ?> path, PropertyDefinition<PD> pd)
      throws IllegalArgumentException, DefinitionDecodingException,
      AuthorizationException, ManagedObjectNotFoundException,
      CommunicationException, PropertyException {
    return getDriver().getPropertyValues(path, pd);
  }



  /**
   * Gets the root configuration client associated with this
   * management context.
   *
   * @return Returns the root configuration client associated with
   *         this management context.
   */
  public final RootCfgClient getRootConfiguration() {
    return getRootConfigurationManagedObject().getConfiguration();
  }



  /**
   * Gets the root configuration managed object associated with this
   * management context.
   *
   * @return Returns the root configuration managed object associated
   *         with this management context.
   */
  public final
  ManagedObject<RootCfgClient> getRootConfigurationManagedObject() {
    return getDriver().getRootConfigurationManagedObject();
  }



  /**
   * Lists the child managed objects of the named parent managed
   * object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The instantiable relation definition.
   * @return Returns the names of the child managed objects.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws AuthorizationException
   *           If the server refuses to list the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  String[] listManagedObjects(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException {
    return getDriver().listManagedObjects(parent, rd);
  }



  /**
   * Lists the child managed objects of the named parent managed
   * object which are a sub-type of the specified managed object
   * definition.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The instantiable relation definition.
   * @param d
   *          The managed object definition.
   * @return Returns the names of the child managed objects which are
   *         a sub-type of the specified managed object definition.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws AuthorizationException
   *           If the server refuses to list the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  String[] listManagedObjects(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
      AbstractManagedObjectDefinition<? extends C, ? extends S> d)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException {
    return getDriver().listManagedObjects(parent, rd, d);
  }



  /**
   * Determines whether or not the named managed object exists.
   *
   * @param path
   *          The path of the named managed object.
   * @return Returns <code>true</code> if the named managed object
   *         exists, <code>false</code> otherwise.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws AuthorizationException
   *           If the server refuses to make the determination because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final boolean managedObjectExists(ManagedObjectPath<?, ?> path)
      throws ManagedObjectNotFoundException, AuthorizationException,
      CommunicationException {
    return getDriver().managedObjectExists(path);
  }



  /**
   * Gets the driver associated with this management context.
   *
   * @return Returns the driver associated with this management
   *         context.
   */
  protected abstract Driver getDriver();
}
