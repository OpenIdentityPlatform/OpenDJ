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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.util;

import java.util.*;

import org.opends.server.types.DN;
import org.opends.server.types.RawModification;

import static org.forgerock.util.Reject.*;

/**
 * This class defines a data structure for a change record entry for
 * an modify operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class ModifyChangeRecordEntry extends ChangeRecordEntry
{
  /**
   * The modifications for this change record.
   */
  private final List<RawModification> modifications;



  /**
   * Creates a new entry with the provided information.
   *
   * @param  dn             The distinguished name for this entry.  It must not
   *                        be  <CODE>null</CODE>.
   * @param  modifications  The modifications for this change record.  It must
   *                        not be <CODE>null</CODE>.
   */
  public ModifyChangeRecordEntry(DN dn,
      Collection<RawModification> modifications)
  {
    super(dn);


    ifNull(modifications);

    this.modifications = new ArrayList<>(modifications);
  }


  /**
   * Get the list of modifications.
   * <p>
   * The returned list is read-only.
   *
   * @return Returns the unmodifiable list of modifications.
   */
  public List<RawModification> getModifications()
  {
    return Collections.unmodifiableList(modifications);
  }



  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  @Override
  public ChangeOperationType getChangeOperationType()
  {
    return ChangeOperationType.MODIFY;
  }



  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("ModifyChangeRecordEntry(dn=\"");
    buffer.append(getDN());
    buffer.append("\", mods={");

    Iterator<RawModification> iterator = modifications.iterator();
    while (iterator.hasNext())
    {
      RawModification mod = iterator.next();
      buffer.append(mod.getModificationType());
      buffer.append(" ");
      buffer.append(mod.getAttribute().getAttributeType());

      if (iterator.hasNext())
      {
        buffer.append(", ");
      }
    }
    buffer.append("})");

    return buffer.toString();
  }
}

