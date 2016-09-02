/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Iterator;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/** This class store historical information for a provided attribute. */
public abstract class AttrHistorical
{
  /**
   * This method will be called when replaying an operation.
   * It should use whatever historical information is stored in this class
   * to solve the conflict and modify the mod and the mods iterator accordingly
   *
   * @param modsIterator  The iterator on the mods from which the mod is extracted.
   * @param csn  The CSN associated to the operation.
   * @param modifiedEntry The entry modified by this operation.
   * @param mod           The modification.
   * @return {@code true} if a conflict was detected, {@code false} otherwise.
   */
  public abstract boolean replayOperation(
      Iterator<Modification> modsIterator, CSN csn, Entry modifiedEntry, Modification mod);

  /**
   * This method calculates the historical information and update the hist
   * attribute to store the historical information for modify operation that
   * does not conflict with previous operation.
   * This is the usual path and should therefore be optimized.
   * <p>
   * It does not check if the operation to process is conflicting or not with
   * previous operations. The caller is responsible for this.
   *
   * @param csn The CSN of the operation to process
   * @param mod The modify operation to process.
   */
  public abstract void processLocalOrNonConflictModification(CSN csn, Modification mod);

  /**
   * Create a new object from a provided attribute type. Historical is empty.
   *
   * @param attrType the provided attribute type.
   * @return a new AttributeInfo object.
   */
  public static AttrHistorical createAttributeHistorical(AttributeType attrType)
  {
    return attrType.isSingleValue() ? new AttrHistoricalSingle(attrType) : new AttrHistoricalMultiple();
  }

  /**
   * Get the historical informations for this attribute Info.
   *
   * @return the historical informations
   */
  public abstract Set<AttrValueHistorical> getValuesHistorical();

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
   * @param attrType the associated attribute type.
   * @param value   the associated value or null if there is no value;
   * @param csn     the associated CSN.
   */
  public abstract void assign(HistAttrModificationKey histKey, AttributeType attrType, ByteString value, CSN csn);
}
