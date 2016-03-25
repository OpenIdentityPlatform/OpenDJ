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

import static org.opends.server.replication.plugin.HistAttrModificationKey.*;
import static org.opends.server.util.StaticUtils.*;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Modification;

/**
 * This class stores an internal usable representation of the value of
 * the historical related to an entry.
 * It encodes/decodes from the String form stored in the DB attribute
 * from/to the internal usable form.
 *
 *
 * an historical attribute value looks like :
 *  description:00000108b3a65541000000000001:add:added_value
 *  or
 *  description:00000108b3a6cbb8000000000001:del:deleted_value
 *  or
 *  description:00000108b3a6cbb8000000000001:repl:new_value
 *  or
 *  description:00000108b3a6cbb8000000000001:attrDel
 *  or
 *  description:00000108b3a65541000000000001:add
 *  or
 *  dn:00000108b3a65541000000000001:add (ADD operation)
 *  or
 *  dn:00000108b3a65541000000000001:moddn (MODIFYDN operation)
 *
 *  so after split
 *  token[0] will contain the attribute name
 *  token[1] will contain the CSN
 *  token[2] will contain the type of historical information
 *  token[3] will contain the attribute value
 *
 *  options are stored with the attribute names using; as a separator
 *  example :
 *  description;FR;France:00000108b3a65541000000000001:add:added_value
 */
class HistoricalAttributeValue
{
  private final AttributeDescription attrDesc;
  /** The lowercase attribute string representation. */
  private final String attrString;
  private final ByteString attributeValue;
  private final CSN csn;
  private final HistAttrModificationKey histKey;
  private final String stringValue;
  private boolean attrTypeIsNull;
  /**
   * This flag indicates that this value was generated to store the last date
   * when the entry was renamed.
   */
  private final boolean isModDN;

  /**
   * Create a new object from the String encoded form.
   *
   * @param strVal The String encoded form of historical attribute value.
   * @see EntryHistorical#encodeAndPurge() encoding in EntryHistorical
   */
  HistoricalAttributeValue(String strVal)
  {
    String[] token = strVal.split(":", 4);

    attrDesc = AttributeDescription.valueOf(token[0]);
    attrString = toLowerCase(attrDesc.getNameOrOID());
    // This HistVal was used to store the date when some
    // modifications were done to the entries.
    attrTypeIsNull = attrString.equalsIgnoreCase("dn");
    // This HistVal is used to store the date when the entry
    // was added to the directory or when it was last renamed.
    isModDN = attrTypeIsNull && token.length >= 3 && token[2].compareTo("moddn") == 0;

    csn = new CSN(token[1]);
    histKey = HistAttrModificationKey.decodeKey(token[2]);
    if (histKey != ATTRDEL)
    {
      if (token.length == 4)
      {
        stringValue = token[3];
        attributeValue = ByteString.valueOfUtf8(stringValue);
      }
      else
      {
        stringValue = null;
        attributeValue = null;
      }
    }
    else
    {
      stringValue = null;
      attributeValue = null;
    }
  }

  /**
   * Get the String form of the attribute type.
   *
   * @return Returns the String form of the attribute type.
   */
  public String getAttrString()
  {
    return attrString;
  }

  /**
   * Get the attribute description of this HistVal.
   *
   * @return Returns the attribute description of this HistVal.
   *         Can return {@code null} if the HistVal was generated for a ADD Operation.
   */
  AttributeDescription getAttributeDescription()
  {
    return attrDesc;
  }

  /**
   * Get the CSN of this HistVal.
   * @return Returns the CSN of this HistVal.
   */
  public CSN getCSN()
  {
    return csn;
  }

  /**
   * Get the HistKey.
   * @return Returns the histKey.
   */
  public HistAttrModificationKey getHistKey()
  {
    return histKey;
  }

  /**
   * Get the Attribute Value.
   * @return The Attribute Value.
   */
  public ByteString getAttributeValue()
  {
    return attributeValue;
  }

  /**
   * Generate a Modification equivalent to this value of the historical
   * attribute.
   *
   * @return A modification equivalent to this value of the historical
   * attribute.
   */
  public Modification generateMod()
  {
    AttributeBuilder builder = new AttributeBuilder(attrDesc.getAttributeType(), attrString);
    builder.setOptions(attrDesc.getOptions());

    if (histKey != ATTRDEL)
    {
      builder.add(attributeValue);
    }
    Attribute attr = builder.toAttribute();

    switch (histKey)
    {
    case ADD:
      return new Modification(ModificationType.ADD, attr);
    case DEL:
    case ATTRDEL:
      return new Modification(ModificationType.DELETE, attr);
    case REPL:
      return new Modification(ModificationType.REPLACE, attr);
    default:
      return null;
    }
  }

  /**
   * Indicates if this value of the historical attribute was generated
   * for a ADD operation.
   *
   * @return a boolean indicating if this was generated for a ADD operation.
   */
  public boolean isADDOperation()
  {
    return attrTypeIsNull && !isModDN;
  }

  /**
   * Indicates if this value of the historical attribute was generated
   * for a MODDN operation.
   *
   * @return a boolean indicating if this was generated for a ADDMODDN operation.
   */
  public boolean isMODDNOperation()
  {
    return attrTypeIsNull && isModDN;
  }

  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    sb.append(attrDesc);
    sb.append(":").append(csn).append(":").append(getModificationType());
    if (stringValue != null)
    {
      sb.append(":").append(stringValue);
    }
    return sb.toString();
  }

  private String getModificationType()
  {
    if (isModDN)
    {
      return "moddn";
    }
    else if (histKey != null)
    {
      return histKey.toString();
    }
    return "TODO";
  }
}
