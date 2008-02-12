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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.Iterator;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;


/**
 * This classes is used to store historical information.
 * One object of this type is created for each attribute that was changed in
 * the entry.
 */
public abstract class AttributeInfo
{
  /**
   * This method will be called when replaying an operation.
   * It should use whatever historical information is stored in this class
   * to solve the conflict and modify the mod and the mods iterator accordingly
   *
   * @param modsIterator  The iterator on the mods from which the mod is\
   *                      extracted.
   * @param changeNumber  The changeNumber associated to the operation.
   * @param modifiedEntry The entry modified by this operation.
   * @param mod           The modification.
   *
   * @return a boolean indicating if a conflict was detected.
   */
  public abstract boolean replayOperation(
      Iterator<Modification> modsIterator, ChangeNumber changeNumber,
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
   * @param changeNumber The changeNumber of the operation to process
   * @param mod The modify operation to process.
   */
  public abstract void processLocalOrNonConflictModification(
      ChangeNumber changeNumber, Modification mod);

  /**
   * Create a new AttributeInfo object that will be used with the givene type.
   *
   * @param type the AttrbuteType with which the ATtributeInfo is going to be
   *             used.
   * @return a new AttributeInfo object.
   */
  public static AttributeInfo createAttributeInfo(AttributeType type)
  {
    if (type.isSingleValue())
      return new AttrInfoSingle();
    else
      return new AttrInfoMultiple();
  }

  /**
   * Get the List of ValueInfo for this attribute Info.
   *
   * @return the List of ValueInfo
   */
  public abstract ArrayList<ValueInfo> getValuesInfo();


  /**
   * Returns the last time when the attribute was deleted.
   *
   * @return the last time when the attribute was deleted
   */
  public abstract ChangeNumber getDeleteTime();

  /**
   * Load the provided information.
   *
   * @param histKey the key to load.
   * @param value   the associated value or null if there is no value;
   * @param cn      the associated ChangeNumber.
   */
  public abstract void load(
      HistKey histKey, AttributeValue value, ChangeNumber cn);

}

