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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock, AS.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Iterator;
import java.util.Map;

import org.opends.server.replication.common.CSN;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/**
 * This class store historical information for a provided attribute.
 */
public abstract class AttrHistorical
{
  /**
   * This method will be called when replaying an operation.
   * It should use whatever historical information is stored in this class
   * to solve the conflict and modify the mod and the mods iterator accordingly
   *
   * @param modsIterator  The iterator on the mods from which the mod is
   *                      extracted.
   * @param csn  The CSN associated to the operation.
   * @param modifiedEntry The entry modified by this operation.
   * @param mod           The modification.
   *
   * @return a boolean indicating if a conflict was detected.
   */
  public abstract boolean replayOperation(
      Iterator<Modification> modsIterator, CSN csn,
      Entry modifiedEntry, Modification mod);

  /**
   * This method calculate the historical information and update the hist
   * attribute to store the historical information for modify operation that
   * does not conflict with previous operation.
   * This is the usual path and should therefore be optimized.
   *
   * It does not check if the operation to process is conflicting or not with
   * previous operations. The caller is responsible for this.
   *
   * @param csn The CSN of the operation to process
   * @param mod The modify operation to process.
   */
  public abstract void processLocalOrNonConflictModification(
      CSN csn, Modification mod);

  /**
   * Create a new object from a provided attribute type. Historical is empty.
   *
   * @param type the provided attribute type.
   * @return a new AttributeInfo object.
   */
  public static AttrHistorical createAttributeHistorical(
      AttributeType type)
  {
    return type.isSingleValue() ? new AttrHistoricalSingle() : new AttrHistoricalMultiple();
  }

  /**
   * Get the List of ValueInfo for this attribute Info.
   *
   * @return the List of ValueInfo
   */
  public abstract Map<AttrValueHistorical, AttrValueHistorical> getValuesHistorical();


  /**
   * Returns the last time when this attribute was deleted.
   *
   * @return the last time when this attribute was deleted
   */
  public abstract CSN getDeleteTime();

  /**
   * Assign the provided information to this object.
   *
   * @param histKey the key to assign.
   * @param value   the associated value or null if there is no value;
   * @param csn     the associated CSN.
   */
  public abstract void assign(
      HistAttrModificationKey histKey, ByteString value, CSN csn);

}

