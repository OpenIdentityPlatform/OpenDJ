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
import java.util.SortedSet;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * A client-side interface for reading and modifying Test Parent
 * settings.
 * <p>
 * A configuration for testing components that have child components.
 * It re-uses the virtual-attribute configuration LDAP profile.
 */
public interface TestParentCfgClient extends ConfigurationClient {

  /**
   * Get the configuration definition associated with this Test Parent.
   *
   * @return Returns the configuration definition associated with this Test Parent.
   */
  ManagedObjectDefinition<? extends TestParentCfgClient, ? extends TestParentCfg> definition();



  /**
   * Get the "mandatory-boolean-property" property.
   * <p>
   * A mandatory boolean property.
   *
   * @return Returns the value of the "mandatory-boolean-property" property.
   */
  Boolean isMandatoryBooleanProperty();



  /**
   * Set the "mandatory-boolean-property" property.
   * <p>
   * A mandatory boolean property.
   *
   * @param value The value of the "mandatory-boolean-property" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   */
  void setMandatoryBooleanProperty(boolean value) throws IllegalPropertyValueException;



  /**
   * Get the "mandatory-class-property" property.
   * <p>
   * A mandatory Java-class property requiring a component restart.
   *
   * @return Returns the value of the "mandatory-class-property" property.
   */
  String getMandatoryClassProperty();



  /**
   * Set the "mandatory-class-property" property.
   * <p>
   * A mandatory Java-class property requiring a component restart.
   *
   * @param value The value of the "mandatory-class-property" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   */
  void setMandatoryClassProperty(String value) throws IllegalPropertyValueException;



  /**
   * Get the "mandatory-read-only-attribute-type-property" property.
   * <p>
   * A mandatory read-only attribute type property.
   *
   * @return Returns the value of the "mandatory-read-only-attribute-type-property" property.
   */
  AttributeType getMandatoryReadOnlyAttributeTypeProperty();



  /**
   * Set the "mandatory-read-only-attribute-type-property" property.
   * <p>
   * A mandatory read-only attribute type property.
   * <p>
   * This property is read-only and can only be modified during
   * creation of a Test Parent.
   *
   * @param value The value of the "mandatory-read-only-attribute-type-property" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   * @throws PropertyIsReadOnlyException
   *           If this Test Parent is not being initialized.
   */
  void setMandatoryReadOnlyAttributeTypeProperty(AttributeType value) throws IllegalPropertyValueException, PropertyIsReadOnlyException;



  /**
   * Get the "optional-multi-valued-dn-property" property.
   * <p>
   * An optional multi-valued DN property with a defined default
   * behavior.
   *
   * @return Returns the values of the "optional-multi-valued-dn-property" property.
   */
  SortedSet<DN> getOptionalMultiValuedDNProperty();



  /**
   * Set the "optional-multi-valued-dn-property" property.
   * <p>
   * An optional multi-valued DN property with a defined default
   * behavior.
   *
   * @param values The values of the "optional-multi-valued-dn-property" property.
   * @throws IllegalPropertyValueException
   *           If one or more of the new values are invalid.
   */
  void setOptionalMultiValuedDNProperty(Collection<DN> values) throws IllegalPropertyValueException;



  /**
   * Lists the Test Children.
   *
   * @return Returns an array containing the names of the Test
   *         Children.
   * @throws ConcurrentModificationException
   *           If this Test Parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to list the Test Children because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  String[] listTestChildren() throws ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Gets the named Test Child.
   *
   * @param name
   *           The name of the Test Child to retrieve.
   * @return Returns the named Test Child.
   * @throws DefinitionDecodingException
   *           If the named Test Child was found but its type
   *           could not be determined.
   * @throws ManagedObjectDecodingException
   *           If the named Test Child was found but one or
   *           more of its properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the named Test Child was not found on the
   *           server.
   * @throws ConcurrentModificationException
   *           If this Test Parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the named Multiple
   *           Children because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  TestChildCfgClient getTestChild(String name)
      throws DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Creates a new Test Child. The new Test Child will
   * initially not contain any property values (including mandatory
   * properties). Once the Test Child has been configured it can
   * be added to the server using the {@link #commit()} method.
   *
   * @param <C>
   *          The type of the Test Child being created.
   * @param d
   *          The definition of the Test Child to be created.
   * @param name
   *          The name of the new Test Child.
   * @param exceptions
   *          An optional collection in which to place any {@link
   *          DefaultBehaviorException}s that occurred whilst
   *          attempting to determine the default values of the
   *          Test Child. This argument can be <code>null<code>.
   * @return Returns a new Test Child configuration instance.
   * @throws IllegalManagedObjectNameException
   *          If the name is invalid.
   */
  <C extends TestChildCfgClient> C createTestChild(
      ManagedObjectDefinition<C, ?> d, String name, Collection<DefaultBehaviorException> exceptions) throws IllegalManagedObjectNameException;



  /**
   * Removes the named Test Child.
   *
   * @param name
   *          The name of the Test Child to remove.
   * @throws ManagedObjectNotFoundException
   *           If the Test Child does not exist.
   * @throws OperationRejectedException
   *           If the server refuses to remove the Test Child
   *           due to some server-side constraint which cannot be
   *           satisfied (for example, if it is referenced by another
   *           managed object).
   * @throws ConcurrentModificationException
   *           If this Test Parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to remove the Test Child
   *           because the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  void removeTestChild(String name)
      throws ManagedObjectNotFoundException, OperationRejectedException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException;



  /**
   * Determines whether or not the Optional Test Child exists.
   *
   * @return Returns <true> if the Optional Test Child exists.
   * @throws ConcurrentModificationException
   *           If this Test Parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to make the determination because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  boolean hasOptionalTestChild() throws ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Gets the Optional Test Child if it is present.
   *
   * @return Returns the Optional Test Child if it is present.
   * @throws DefinitionDecodingException
   *           If the Optional Test Child was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the Optional Test Child was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the Optional Test Child is not present.
   * @throws ConcurrentModificationException
   *           If this Test Parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the Optional Test Child
   *           because the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  TestChildCfgClient getOptionalChild()
      throws DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Creates a new Optional Test Child. The new Optional Test Child will
   * initially not contain any property values (including mandatory
   * properties). Once the Optional Test Child has been configured it can be
   * added to the server using the {@link #commit()} method.
   *
   * @param <C>
   *          The type of the Optional Test Child being created.
   * @param d
   *          The definition of the Optional Test Child to be created.
   * @param exceptions
   *          An optional collection in which to place any {@link
   *          DefaultBehaviorException}s that occurred whilst
   *          attempting to determine the default values of the
   *          Optional Test Child. This argument can be <code>null<code>.
   * @return Returns a new Optional Test Child configuration instance.
   */
  <C extends TestChildCfgClient> C createOptionalTestChild(
      ManagedObjectDefinition<C, ?> d, Collection<DefaultBehaviorException> exceptions);



  /**
   * Removes the Optional Test Child if it exists.
   *
   * @throws ManagedObjectNotFoundException
   *           If the Optional Test Child does not exist.
   * @throws OperationRejectedException
   *           If the server refuses to remove the Optional Test Child due
   *           to some server-side constraint which cannot be satisfied
   *           (for example, if it is referenced by another managed
   *           object).
   * @throws ConcurrentModificationException
   *           If this Test Parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to remove the Optional Test Child
   *           because the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  void removeOptionalTestChild()
      throws ManagedObjectNotFoundException, OperationRejectedException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException;

}
