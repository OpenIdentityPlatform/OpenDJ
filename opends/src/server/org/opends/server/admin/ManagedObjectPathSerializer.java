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



/**
 * A strategy for serializing managed object paths.
 * <p>
 * This interface provides a generic means for serializing managed object paths
 * into application specific forms. For example, a JNDI client would use this
 * interface to construct <code>LdapName</code> objects from a path.
 * Similarly, on the server side, a serialization strategy is used to construct
 * <code>DN</code> instances from a path.
 * <p>
 * During serialization the serializer is invoked for each element in the
 * managed object path in big-endian order, starting from the root and
 * proceeding down to the leaf element.
 */
public interface ManagedObjectPathSerializer {

  /**
   * Append a managed object path element identified by an instantiable relation
   * and an instance name.
   *
   * @param r
   *          The instantiable relation.
   * @param name
   *          The instance name.
   */
  void appendManagedObjectPathElement(InstantiableRelationDefinition<?, ?> r,
      String name);



  /**
   * Append a managed object path element identified by a optional relation.
   *
   * @param r
   *          The optional relation.
   */
  void appendManagedObjectPathElement(OptionalRelationDefinition<?, ?> r);



  /**
   * Append a managed object path element identified by a singleton relation.
   *
   * @param r
   *          The singleton relation.
   */
  void appendManagedObjectPathElement(SingletonRelationDefinition<?, ?> r);

}
