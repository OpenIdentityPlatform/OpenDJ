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
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;

/**
 * This classes is used to store historical information for single valued
 * attributes.
 * One object of this type is created for each attribute that was changed in
 * the entry.
 * It allows to record the last time a given value was added,
 * and the last time the whole attribute was deleted.
 */
public class AttrInfoSingle extends AttributeInfo
{
  private ChangeNumber deleteTime = null; // last time when the attribute was
                                          // deleted
  private ChangeNumber addTime = null;    // last time when a value was added
  private AttributeValue value = null;    // last added value

  /**
   * {@inheritDoc}
   */
  @Override
  public ChangeNumber getDeleteTime()
  {
    return deleteTime;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ArrayList<ValueInfo> getValuesInfo()
  {
    if (addTime == null)
    {
      return new ArrayList<ValueInfo>();
    }
    else
    {
      ArrayList<ValueInfo> values = new ArrayList<ValueInfo>();
      values.add(new ValueInfo(value, addTime, null));
      return values;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processLocalOrNonConflictModification(ChangeNumber changeNumber,
      Modification mod)
  {
    Attribute modAttr = mod.getAttribute();
    LinkedHashSet<AttributeValue> values = null;
    if (modAttr != null)
      values = modAttr.getValues();
    AttributeValue newValue = null;
    if (values.size() != 0)
      newValue = values.iterator().next();

    switch (mod.getModificationType())
    {
    case DELETE:
      deleteTime = changeNumber;
      value = newValue;
      break;

    case ADD:
      addTime = changeNumber;
      value = newValue;
      break;

    case REPLACE:
      deleteTime = addTime = changeNumber;
      value = newValue;
      break;

    case INCREMENT:
      /* FIXME : we should update ChangeNumber */
      break;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean replayOperation(Iterator<Modification> modsIterator,
      ChangeNumber changeNumber, Entry modifiedEntry, Modification mod)
  {
    boolean conflict = false;

    Attribute modAttr = mod.getAttribute();
    LinkedHashSet<AttributeValue> values = null;
    if (modAttr != null)
      values = modAttr.getValues();
    AttributeValue newValue = null;
    if (values.size() != 0)
      newValue = values.iterator().next();

    switch (mod.getModificationType())
    {
    case DELETE:
      if ((changeNumber.newer(addTime)) &&
          ((newValue == null) || ((newValue != null)
                                  && (newValue.equals(value)))))
      {
        deleteTime = changeNumber;
      }
      else
      {
        conflict = true;
        modsIterator.remove();
      }
      break;

    case ADD:
      if (changeNumber.newerOrEquals(deleteTime) && changeNumber.older(addTime))
      {
        conflict = true;
        mod.setModificationType(ModificationType.REPLACE);
        addTime = changeNumber;
        value = newValue;
      }
      else
      {
        if (changeNumber.newerOrEquals(deleteTime)
            && ((addTime == null ) || addTime.older(deleteTime)))
        {
          // no conflict : don't do anything beside setting the addTime
          addTime = changeNumber;
          value = newValue;
        }
        else
        {
          conflict = true;
          modsIterator.remove();
        }
      }

      break;

    case REPLACE:
      if ((changeNumber.older(deleteTime)) && (changeNumber.older(deleteTime)))
      {
        conflict = true;
        modsIterator.remove();
      }
      else
      {
        addTime = changeNumber;
        value = newValue;
        deleteTime = changeNumber;
      }
      break;

    case INCREMENT:
      /* FIXME : we should update ChangeNumber */
      break;
    }
    return conflict;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void load(HistKey histKey, AttributeValue value, ChangeNumber cn)
  {
    switch (histKey)
    {
    case ADD:
      addTime = cn;
      this.value = value;
      break;

    case DEL:
      deleteTime = cn;
      if (value != null)
        this.value = value;
      break;

    case REPL:
      addTime = deleteTime = cn;
      if (value != null)
        this.value = value;
      break;

    case DELATTR:
      deleteTime = cn;
      break;
    }
  }
}

