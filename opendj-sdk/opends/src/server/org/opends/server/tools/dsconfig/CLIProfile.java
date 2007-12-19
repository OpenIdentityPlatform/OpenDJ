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

package org.opends.server.tools.dsconfig;



import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinitionResource;
import org.opends.server.admin.RelationDefinition;



/**
 * This class is used to access CLI profile annotations.
 */
final class CLIProfile {

  // The singleton instance.
  private static final CLIProfile INSTANCE = new CLIProfile();



  /**
   * Get the CLI profile instance.
   *
   * @return Returns the CLI profile instance.
   */
  public static CLIProfile getInstance() {
    return INSTANCE;
  }

  // The CLI profile property table.
  private final ManagedObjectDefinitionResource resource;



  // Private constructor.
  private CLIProfile() {
    this.resource = ManagedObjectDefinitionResource.createForProfile("cli");
  }



  /**
   * Gets the default set of properties which should be displayed in a
   * list-xxx operation.
   *
   * @param r
   *          The relation definition.
   * @return Returns the default set of properties which should be
   *         displayed in a list-xxx operation.
   */
  public Set<String> getDefaultListPropertyNames(RelationDefinition<?, ?> r) {
    String s = resource.getString(r.getParentDefinition(), "relation."
        + r.getName() + ".list-properties");
    return new LinkedHashSet<String>(Arrays.asList(s.split(",")));
  }



  /**
   * Gets the naming argument which should be used for a relation
   * definition.
   *
   * @param r
   *          The relation definition.
   * @return Returns the naming argument which should be used for a
   *         relation definition.
   */
  public String getNamingArgument(InstantiableRelationDefinition<?, ?> r) {
    String s = resource.getString(r.getParentDefinition(),
        "relation." + r.getName() + ".naming-argument-override").trim();

    if (s.length() == 0) {
      // Use the last word in the managed object name as the argument
      // prefix.
      StringBuilder builder = new StringBuilder();
      s = r.getChildDefinition().getName();
      int i = s.lastIndexOf('-');
      if (i < 0 || i == (s.length() - 1)) {
        builder.append(s);
      } else {
        builder.append(s.substring(i + 1));
      }
      builder.append("-name");
      s = builder.toString();
    }

    return s;
  }



  /**
   * Determines if instances of the specified managed object
   * definition are to be used for customization.
   *
   * @param d
   *          The managed object definition.
   * @return Returns <code>true</code> if instances of the specified
   *         managed object definition are to be used for
   *         customization.
   */
  public boolean isForCustomization(AbstractManagedObjectDefinition<?, ?> d) {
    String s = resource.getString(d, "is-for-customization");
    return Boolean.parseBoolean(s);
  }
}
