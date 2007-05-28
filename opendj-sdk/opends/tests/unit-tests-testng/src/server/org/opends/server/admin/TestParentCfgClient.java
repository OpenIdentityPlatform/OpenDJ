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

import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.OperationRejectedException;



/**
 * A sample client-side configuration interface for testing.
 */
public interface TestParentCfgClient extends ConfigurationClient {

  /**
   * {@inheritDoc}
   */
  ManagedObjectDefinition<? extends TestParentCfgClient, ? extends TestParentCfg> definition();



  /**
   * Lists the test children.
   *
   * @return Returns an array containing the names of the test
   *         children.
   * @throws ConcurrentModificationException
   *           If this test parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to list the test children because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  String[] listTestChildren() throws ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Gets the named test child.
   *
   * @param name
   *          The name of the test child to retrieve.
   * @return Returns the named test child.
   * @throws DefinitionDecodingException
   *           If the named test child was found but its type could
   *           not be determined.
   * @throws ManagedObjectDecodingException
   *           If the named test child was found but one or more of
   *           its properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the named test child was not found on the server.
   * @throws ConcurrentModificationException
   *           If this test parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the named test child
   *           because the client does not have the correct
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
   * Creates a new test child.
   *
   * @param <C>
   *          The type of the test child being added.
   * @param d
   *          The definition of the test child to be created.
   * @param name
   *          The name of the new test child.
   * @param exceptions
   *          An optional collection in which to place any {@link
   *          DefaultBehaviorException}s that occurred whilst
   *          attempting to determine the default values of the test
   *          child. This argument can be <code>null<code>.
   * @return Returns a new test child instance representing the test
   *         child that was created.
   */
  <C extends TestChildCfgClient> C createTestChild(
      ManagedObjectDefinition<C, ?> d, String name,
      Collection<DefaultBehaviorException> exceptions);



  /**
   * Removes the named test child.
   *
   * @param name
   *          The name of the test child to remove.
   * @throws ManagedObjectNotFoundException
   *           If the test child does not exist.
   * @throws OperationRejectedException
   *           If the server refuses to remove the test child due to
   *           some server-side constraint which cannot be satisfied
   *           (for example, if it is referenced by another managed
   *           object).
   * @throws ConcurrentModificationException
   *           If this test parent has been removed from the server by
   *           another client.
   * @throws AuthorizationException
   *           If the server refuses to remove the test child because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  void removeTestChild(String name) throws ManagedObjectNotFoundException,
      OperationRejectedException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Get the "maximum-length" property.
   *
   * @return Returns the value of the "maximum-length" property.
   */
  int getMaximumLength();



  /**
   * Set the "maximum-length" property.
   *
   * @param value
   *          The value of the "maximum-length" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   */
  void setMaximumLength(Integer value) throws IllegalPropertyValueException;



  /**
   * Get the "minimum-length" property.
   *
   * @return Returns the value of the "minimum-length" property.
   */
  int getMinimumLength();



  /**
   * Set the "minimum-length" property.
   *
   * @param value
   *          The value of the "minimum-length" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   */
  void setMinimumLength(Integer value) throws IllegalPropertyValueException;
}
