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
package org.opends.server.types;



/**
 * This interface defines a set of methods that must be provided by a
 * schema file element, which is a schema element that is loaded from
 * a schema configuration file.
 * <BR><BR>
 * Note that this interface is not meant to be implemented by
 * third-party code, and only the following classes should be
 * considered schema file elements:
 * <UL>
 *   <LI>{@code org.opends.server.types.AttributeType}</LI>
 *   <LI>{@code org.opends.server.types.ObjectClass}</LI>
 *   <LI>{@code org.opends.server.types.NameForm}</LI>
 *   <LI>{@code org.opends.server.types.DITContentRule}</LI>
 *   <LI>{@code org.opends.server.types.DITStructureRule}</LI>
 *   <LI>{@code org.opends.server.types.MatchingRuleUse}</LI>
 * </UL>
 */
public interface SchemaFileElement
{
  /**
   * Retrieves the name of the schema file in which this element is
   * defined.
   *
   * @return  The name of the schema file in which this element is
   *          defined, or {@code null} if it is not known or this
   *          element is not defined in any schema file.
   */
  public String getSchemaFile();



  /**
   * Specifies the name of the schema file in which this element is
   * defined.
   *
   * @param  schemaFile  The name of the schema file in which this
   *                     element is defined, or {@code null} if it is
   *                     not defined in any schema file.
   */
  public void setSchemaFile(String schemaFile);



  /**
   * Retrieves the definition string that is used to represent this
   * element in the schema configuration file.
   *
   * @return  The definition string that should be used to represent
   *          this element in the schema configuration file.
   */
  public String getDefinition();



  /**
   * Creates a new instance of this schema element based on the
   * definition from the schema file.  The new instance should also
   * be created with all appropriate state information that may not
   * be directly represented in the schema definition (e.g., the name
   * of the schema file containing the definition).
   * <BR><BR>
   * Whenever an existing schema file element is modified with the
   * server online, this method will be invoked to recreate any
   * schema elements that might have been dependent upon the
   * modified element.
   *
   * @return  A new instance of this schema element based on the
   *          definition.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to create the new instance of this
   *                              schema element.
   */
  public SchemaFileElement recreateFromDefinition()
         throws DirectoryException;
}

