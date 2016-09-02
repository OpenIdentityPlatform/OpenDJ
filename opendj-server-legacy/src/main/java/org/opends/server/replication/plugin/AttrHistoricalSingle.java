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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.opends.server.replication.plugin.HistAttrModificationKey.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

/**
 * This class is used to store historical information for single valued attributes.
 * One object of this type is created for each attribute that was changed in the entry.
 * It allows to record the last time a given value was added,
 * and the last time the whole attribute was deleted.
 */
public class AttrHistoricalSingle extends AttrHistorical
{
  /** Last added value. */
  private ByteString value;
  /** Attribute type for this historical value */
  private AttributeType attributeType;
  /** Last time when a value was added. */
  private CSN addTime;
  /** Last time when the attribute was deleted. */
  private CSN deleteTime;
  /**
   * Last operation applied. This is only used for multiple mods on the same
   * single valued attribute in the same modification.
   */
  private HistAttrModificationKey lastMod;

  /**
   * Builds an {@link AttrHistoricalSingle} object.
   *
   * @param attributeType
   *          the attribute type for this historical value
   */
  public AttrHistoricalSingle(AttributeType attributeType)
  {
    this.attributeType = attributeType;
  }

  @Override
  public CSN getDeleteTime()
  {
    return this.deleteTime;
  }

  @Override
  public Set<AttrValueHistorical> getValuesHistorical()
  {
    if (addTime != null)
    {
      return Collections.singleton(new AttrValueHistorical(value, attributeType, addTime, null));
    }
    return Collections.emptySet();
  }

  @Override
  public void processLocalOrNonConflictModification(CSN csn, Modification mod)
  {
    Attribute modAttr = mod.getAttribute();
    ByteString newValue = getSingleValue(modAttr);

    switch (mod.getModificationType().asEnum())
    {
    case DELETE:
      delete(csn, newValue);
      break;

    case ADD:
      add(csn, newValue);
      break;

    case REPLACE:
      replaceOrDelete(csn, newValue);
      break;

    case INCREMENT:
      /* FIXME : we should update CSN */
      break;
    }
  }

  private void replaceOrDelete(CSN csn, ByteString newValue)
  {
    if (newValue != null)
    {
      replace(csn, newValue);
    }
    else
    {
      delete(csn, null);
    }
  }

  private void add(CSN csn, ByteString newValue)
  {
    addTime = csn;
    value = newValue;
    lastMod = ADD;
  }

  private void replace(CSN csn, ByteString newValue)
  {
    addTime = csn;
    deleteTime = csn;
    value = newValue;
    lastMod = REPL;
  }

  private void delete(CSN csn, ByteString newValue)
  {
    addTime = null;
    deleteTime = csn;
    value = newValue;
    lastMod = DEL;
  }

  private void deleteWithoutDeleteTime()
  {
    addTime = null;
    value = null;
    lastMod = DEL;
  }

  @Override
  public boolean replayOperation(Iterator<Modification> modsIterator, CSN csn,
      Entry modifiedEntry, Modification mod)
  {
    Attribute modAttr = mod.getAttribute();
    ByteString newValue = getSingleValue(modAttr);

    boolean conflict = false;
    switch (mod.getModificationType().asEnum())
    {
    case DELETE:
      if (csn.isNewerThan(addTime))
      {
        if (newValue == null || newValue.equals(value) || value == null)
        {
          if (csn.isNewerThan(deleteTime))
          {
            deleteTime = csn;
          }
          AttributeType type = modAttr.getAttributeDescription().getAttributeType();
          if (!modifiedEntry.hasAttribute(type))
          {
            conflict = true;
            modsIterator.remove();
          }
          else if (newValue != null &&
              !modifiedEntry.hasValue(modAttr.getAttributeDescription(), newValue))
          {
            conflict = true;
            modsIterator.remove();
          }
          else
          {
            deleteWithoutDeleteTime();
          }
        }
        else
        {
          conflict = true;
          modsIterator.remove();
        }
      }
      else if (csn.equals(addTime))
      {
        if (lastMod == ADD || lastMod == REPL)
        {
          if (csn.isNewerThan(deleteTime))
          {
            deleteTime = csn;
          }
          deleteWithoutDeleteTime();
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
      if (csn.isNewerThanOrEqualTo(deleteTime) && csn.isOlderThan(addTime))
      {
        conflict = true;
        mod.setModificationType(ModificationType.REPLACE);
        addTime = csn;
        value = newValue;
        lastMod = REPL;
      }
      else
      {
        if (csn.isNewerThanOrEqualTo(deleteTime)
            && (addTime == null || addTime.isOlderThan(deleteTime)))
        {
          add(csn, newValue);
        }
        else
        {
          // Case where CSN = addTime = deleteTime
          if (csn.equals(deleteTime) && csn.equals(addTime)
              && lastMod == DEL)
          {
            add(csn, newValue);
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
      if (csn.isOlderThan(deleteTime))
      {
        conflict = true;
        modsIterator.remove();
      }
      else
      {
        replaceOrDelete(csn, newValue);
      }
      break;

    case INCREMENT:
      /* FIXME : we should update CSN */
      break;
    }
    return conflict;
  }

  private ByteString getSingleValue(Attribute modAttr)
  {
    if (modAttr != null && !modAttr.isEmpty())
    {
      return modAttr.iterator().next();
    }
    return null;
  }

  @Override
  public void assign(HistAttrModificationKey histKey, AttributeType attrType, ByteString value, CSN csn)
  {
    switch (histKey)
    {
    case ADD:
      this.addTime = csn;
      this.value = value;
      break;

    case DEL:
      this.deleteTime = csn;
      if (value != null)
      {
        this.value = value;
      }
      break;

    case REPL:
      this.addTime = this.deleteTime = csn;
      if (value != null)
      {
        this.value = value;
      }
      break;

    case ATTRDEL:
      this.deleteTime = csn;
      break;
    }
  }

  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    if (deleteTime != null)
    {
      sb.append("deleteTime=").append(deleteTime);
    }
    if (addTime != null)
    {
      if (sb.length() > 0)
      {
        sb.append(", ");
      }
      sb.append("addTime=").append(addTime);
    }
    if (sb.length() > 0)
    {
      sb.append(", ");
    }
    sb.append("value=").append(value)
      .append(", lastMod=").append(lastMod);
    return getClass().getSimpleName() + "(" + sb + ")";
  }
}
