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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;

/**
 * This class is used to store historical information for single valued
 * attributes.
 * One object of this type is created for each attribute that was changed in
 * the entry.
 * It allows to record the last time a given value was added,
 * and the last time the whole attribute was deleted.
 */
public class AttrHistoricalSingle extends AttrHistorical
{
  /** Last time when the attribute was deleted. */
  private ChangeNumber deleteTime = null;
  /** Last time when a value was added. */
  private ChangeNumber addTime = null;
  /** Last added value. */
  private AttributeValue value = null;

/**
 * last operation applied. This is only used for multiple mods on the same
 * single valued attribute in the same modification.
 */
  private HistAttrModificationKey lastMod = null;

  /**
   * {@inheritDoc}
   */
  @Override
  public ChangeNumber getDeleteTime()
  {
    return this.deleteTime;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<AttrValueHistorical,AttrValueHistorical> getValuesHistorical()
  {
    if (addTime == null)
    {
      return Collections.emptyMap();
    }
    else
    {
      AttrValueHistorical val = new AttrValueHistorical(value, addTime, null);
      return Collections.singletonMap(val, val);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processLocalOrNonConflictModification(ChangeNumber changeNumber,
      Modification mod)
  {
    AttributeValue newValue = null;
    Attribute modAttr = mod.getAttribute();
    if (modAttr != null && !modAttr.isEmpty())
    {
      newValue = modAttr.iterator().next();
    }

    switch (mod.getModificationType())
    {
    case DELETE:
      this.addTime = null;
      this.deleteTime = changeNumber;
      this.value = newValue;
      lastMod = HistAttrModificationKey.DEL;
      break;

    case ADD:
      this.addTime = changeNumber;
      this.value = newValue;
      lastMod = HistAttrModificationKey.ADD;
      break;

    case REPLACE:
      if (newValue == null)
      {
        // REPLACE with null value is actually a DELETE
        this.addTime = null;
        this.deleteTime = changeNumber;
        this.value = null;
        lastMod = HistAttrModificationKey.DEL;
      }
      else
      {
        this.deleteTime = addTime = changeNumber;
        lastMod = HistAttrModificationKey.REPL;
      }
      this.value = newValue;
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

    AttributeValue newValue = null;
    Attribute modAttr = mod.getAttribute();
    if (modAttr != null && !modAttr.isEmpty())
    {
      newValue = modAttr.iterator().next();
    }

    switch (mod.getModificationType())
    {
    case DELETE:
      if (changeNumber.newer(addTime))
      {
        if (newValue == null || newValue.equals(value) || value == null)
        {
          if (changeNumber.newer(deleteTime))
          {
            deleteTime = changeNumber;
          }
          AttributeType type = modAttr.getAttributeType();
          if (!modifiedEntry.hasAttribute(type))
          {
            conflict = true;
            modsIterator.remove();
          }
          else if ((newValue != null) &&
              (!modifiedEntry.hasValue(type, modAttr.getOptions(), newValue)))
          {
            conflict = true;
            modsIterator.remove();
          }
          else
          {
            addTime = null;
            lastMod = HistAttrModificationKey.DEL;
            value = null;
          }
        }
        else
        {
          conflict = true;
          modsIterator.remove();
        }
      }
      else if (changeNumber.equals(addTime))
      {
        if ((lastMod == HistAttrModificationKey.ADD)
            || (lastMod == HistAttrModificationKey.REPL))
        {
          if (changeNumber.newer(deleteTime))
          {
            deleteTime = changeNumber;
          }
          addTime = null;
          lastMod = HistAttrModificationKey.DEL;
          value = null;
        }
        else
        {
          conflict = true;
          modsIterator.remove();
        }
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
        lastMod = HistAttrModificationKey.REPL;
      }
      else
      {
        if (changeNumber.newerOrEquals(deleteTime)
            && ((addTime == null ) || addTime.older(deleteTime)))
        {
          // no conflict : don't do anything beside setting the addTime
          addTime = changeNumber;
          value = newValue;
          lastMod = HistAttrModificationKey.ADD;
        }
        else
        {
          // Case where changeNumber = addTime = deleteTime
          if (changeNumber.equals(deleteTime)
              && changeNumber.equals(addTime)
              && (lastMod == HistAttrModificationKey.DEL))
          {
            // No conflict, record the new value.
            value = newValue;
            lastMod = HistAttrModificationKey.ADD;
          }
          else
          {
            conflict = true;
            modsIterator.remove();
          }
        }
      }

      break;

    case REPLACE:
      if (changeNumber.older(deleteTime))
      {
        conflict = true;
        modsIterator.remove();
      }
      else
      {
        if (newValue == null)
        {
          addTime = null;
          value = newValue;
          deleteTime = changeNumber;
          lastMod = HistAttrModificationKey.DEL;
        }
        else
        {
          addTime = changeNumber;
          value = newValue;
          deleteTime = changeNumber;
          lastMod = HistAttrModificationKey.REPL;
        }
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
  public void assign(HistAttrModificationKey histKey,
      AttributeValue value, ChangeNumber cn)
  {
    switch (histKey)
    {
    case ADD:
      this.addTime = cn;
      this.value = value;
      break;

    case DEL:
      this.deleteTime = cn;
      if (value != null)
        this.value = value;
      break;

    case REPL:
      this.addTime = this.deleteTime = cn;
      if (value != null)
        this.value = value;
      break;

    case DELATTR:
      this.deleteTime = cn;
      break;
    }
  }
}

