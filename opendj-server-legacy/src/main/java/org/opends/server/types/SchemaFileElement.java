/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.List;
import java.util.Map;

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
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface SchemaFileElement
{

  /**
   * Retrieves the "extra" properties for this schema definition.
   * <p>
   * FIXME Contrary to the SDK, this method returns a modifiable Map.
   *
   * @return Returns a Map of the "extra" properties for this schema definition,
   *         where the key is the property name and the value is a List of
   *         Strings representing the property values.
   *         Single valued properties have a List with a single element inside.
   */
  Map<String, List<String>> getExtraProperties();

}
