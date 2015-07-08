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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

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
  private CSN deleteTime;
  /** Last time when a value was added. */
  private CSN addTime;
  /** Last added value. */
  private ByteString value;

  /**
   * Last operation applied. This is only used for multiple mods on the same
   * single valued attribute in the same modification.
   */
  private HistAttrModificationKey lastMod;

  /** {@inheritDoc} */
  @Override
  public CSN getDeleteTime()
  {
    return this.deleteTime;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public void processLocalOrNonConflictModification(CSN csn, Modification mod)
  {
    ByteString newValue = null;
    Attribute modAttr = mod.getAttribute();
    if (modAttr != null && !modAttr.isEmpty())
    {
      newValue = modAttr.iterator().next();
    }

    switch (mod.getModificationType().asEnum())
    {
    case DELETE:
      this.addTime = null;
      this.deleteTime = csn;
      this.value = newValue;
      lastMod = HistAttrModificationKey.DEL;
      break;

    case ADD:
      this.addTime = csn;
      this.value = newValue;
      lastMod = HistAttrModificationKey.ADD;
      break;

    case REPLACE:
      if (newValue == null)
      {
        // REPLACE with null value is actually a DELETE
        this.addTime = null;
        this.deleteTime = csn;
        this.value = null;
        lastMod = HistAttrModificationKey.DEL;
      }
      else
      {
        this.deleteTime = addTime = csn;
        lastMod = HistAttrModificationKey.REPL;
      }
      this.value = newValue;
      break;

    case INCREMENT:
      /* FIXME : we should update CSN */
      break;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean replayOperation(Iterator<Modification> modsIterator, CSN csn,
      Entry modifiedEntry, Modification mod)
  {
    boolean conflict = false;

    ByteString newValue = null;
    Attribute modAttr = mod.getAttribute();
    if (modAttr != null && !modAttr.isEmpty())
    {
      newValue = modAttr.iterator().next();
    }

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
      else if (csn.equals(addTime))
      {
        if ((lastMod == HistAttrModificationKey.ADD)
            || (lastMod == HistAttrModificationKey.REPL))
        {
          if (csn.isNewerThan(deleteTime))
          {
            deleteTime = csn;
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
      if (csn.isNewerThanOrEqualTo(deleteTime) && csn.isOlderThan(addTime))
      {
        conflict = true;
        mod.setModificationType(ModificationType.REPLACE);
        addTime = csn;
        value = newValue;
        lastMod = HistAttrModificationKey.REPL;
      }
      else
      {
        if (csn.isNewerThanOrEqualTo(deleteTime)
            && ((addTime == null ) || addTime.isOlderThan(deleteTime)))
        {
          // no conflict : don't do anything beside setting the addTime
          addTime = csn;
          value = newValue;
          lastMod = HistAttrModificationKey.ADD;
        }
        else
        {
          // Case where CSN = addTime = deleteTime
          if (csn.equals(deleteTime) && csn.equals(addTime)
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
      if (csn.isOlderThan(deleteTime))
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
          deleteTime = csn;
          lastMod = HistAttrModificationKey.DEL;
        }
        else
        {
          addTime = csn;
          value = newValue;
          deleteTime = csn;
          lastMod = HistAttrModificationKey.REPL;
        }
      }
      break;

    case INCREMENT:
      /* FIXME : we should update CSN */
      break;
    }
    return conflict;
  }

  /** {@inheritDoc} */
  @Override
  public void assign(HistAttrModificationKey histKey, ByteString value, CSN csn)
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

    case DELATTR:
      this.deleteTime = csn;
      break;
    }
  }
}

