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
 * This interface is used to determine the "best match" managed object
 * definition in a definition hierarchy.
 * <p>
 * Managed object definitions, like Java classes, are arranged in an
 * inheritance hierarchy. When managed objects are decoded (e.g. from
 * LDAP entries), the driver implementation is provided with an
 * "expected managed object definition". However, the actual decoded
 * managed object is often an instance of a sub-type of this
 * definition. For example, when decoding a connection handler managed
 * object, the actual type can never be a connection handler because
 * it is an abstract managed object type. Instead, the decoded managed
 * object must be a "concrete" sub-type: an LDAP connection handler or
 * JMX connection handler.
 * <p>
 * This resolution process is coordinated by the
 * <code>resolveManagedObjectDefinition</code> method in managed
 * object definitions, where it is passed a
 * <code>DefinitionResolver</code> implementation. The
 * <code>resolveManagedObjectDefinition</code> method takes care of
 * recursively descending through the definition hierarchy and invokes
 * the {@link #matches(AbstractManagedObjectDefinition)} method
 * against each potential sub-type. It is the job of the resolver to
 * indicate whether the provided managed object definition is a
 * candidate definition. For example, the LDAP driver provides a
 * definition resolver which uses the decoded LDAP entry's object
 * classes to determine the final appropriate managed object
 * definition.
 */
public interface DefinitionResolver {

  /**
   * Determines whether or not the provided managed object definition matches
   * this resolver's criteria.
   *
   * @param d
   *          The managed object definition.
   * @return Returns <code>true</code> if the the provided managed object
   *         definition matches this resolver's criteria.
   */
  boolean matches(AbstractManagedObjectDefinition<?, ?> d);
}
