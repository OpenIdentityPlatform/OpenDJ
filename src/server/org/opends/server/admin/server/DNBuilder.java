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

package org.opends.server.admin.server;



import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;



/**
 * A factory class for creating <code>DN</code>s from managed
 * object paths.
 */
final class DNBuilder {

  /**
   * Creates a new DN representing the specified managed object path.
   *
   * @param path
   *          The managed object path.
   * @return Returns a new DN representing the specified managed
   *         object path.
   */
  public static DN create(ManagedObjectPath<?, ?> path) {
    return path.toDN();
  }



  /**
   * Creates a new DN representing the specified managed object path
   * and relation.
   *
   * @param path
   *          The managed object path.
   * @param relation
   *          The child relation.
   * @return Returns a new DN representing the specified managed
   *         object path and relation.
   */
  public static DN create(ManagedObjectPath<?, ?> path,
      RelationDefinition<?, ?> relation) {
    DN dn = path.toDN();

    try {
      LDAPProfile profile = LDAPProfile.getInstance();
      DN localName = DN.decode(profile.getRelationRDNSequence(relation));
      return dn.concat(localName);
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
    }
  }



  // Prevent instantiation.
  private DNBuilder() {
    // No implementation required.
  }
}
