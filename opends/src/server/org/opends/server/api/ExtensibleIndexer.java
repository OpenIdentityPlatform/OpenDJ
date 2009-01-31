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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.api;

import java.util.Map;
import java.util.Set;
import org.opends.server.types.AttributeValue;

/**
 * This class is  registered with a Backend  and it provides call-
 * backs for indexing attribute values. An index implementation will
 * use this interface to create the keys for an attribute value.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class ExtensibleIndexer
{
  /**
   * Returns the index name preferred by this indexer. This name
   * appended with the identifier returned from
   * {@link #getExtensibleIndexID()} will be used as the index
   * database name.
   * @return  The name of the index for this indexer.
   */
  public abstract String getPreferredIndexName();


  /**
   * Returns an index identifier associated with this indexer. An
   * identifier should be selected based on the matching rule type.
   * A unique identifier will map to  a unique index database in
   * the backend implementation. If multiple matching rules
   * need to share the index database, the corresponding indexers
   * should always use the same identifier.
   * @return index ID A String containing the ID associated with
   *                          this indexer.
   */
  public abstract String getExtensibleIndexID();


  /**
   * Generates the set of index keys for an attribute.
   * @param value The attribute value  for which  keys are required.
   * @param keys The set into which the generated keys will be
   *                          inserted.
   */
  public abstract void getKeys(AttributeValue value,
                                                  Set<byte[]> keys);


  /**
   * Generates a map of index keys and a boolean flag indicating
   * whether the corresponding key will be inserted or deleted.
   * @param value The attribute for which keys are required.
   * @param modifiedKeys A map containing the keys and a boolean.
   *              Keys corresponding to the boolean value <code>true
   *              </code> should be inserted and <code>false</code>
   *              should be deleted.
   * @param insert <code>true</code> if generated keys should
   *            be inserted or <code>false</code> otherwise.
   */
  public abstract void getKeys(AttributeValue value,
                                  Map<byte[], Boolean> modifiedKeys,
                                  Boolean insert);
}