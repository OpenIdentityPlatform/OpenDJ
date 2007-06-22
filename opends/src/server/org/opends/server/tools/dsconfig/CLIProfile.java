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

import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinitionResource;
import org.opends.server.admin.RelationDefinition;



/**
 * This class is used to access CLI profile annotations.
 */
public class CLIProfile {

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
   * Gets the command line operand name which should be used to
   * identify the names of managed objects associated with an
   * instantiable relation.
   *
   * @param r
   *          The instantiable relation definition.
   * @return Returns the command line operand name which should be
   *         used to identify the names of managed objects associated
   *         with an instantiable relation.
   */
  public String getOperandName(InstantiableRelationDefinition<?, ?> r) {
    return resource.getString(r.getParentDefinition(), "relation."
        + r.getName() + ".operand-name");
  }
}
