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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.plugin;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;

/**
 * Used to establish an order between historical information and index them.
 */
public class HistoricalCsnOrderingMatchingRule extends OrderingMatchingRule
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -3424403930225609943L;



  /**
   * Construct a new  HistoricalCsnOrderingMatchingRule object.
   *
   */
  public HistoricalCsnOrderingMatchingRule()
  {
    super();
    // TODO Auto-generated constructor stub
  }

  /**
   * Compare two ByteString values containing historical information.
   * @param value1 first value to compare
   * @param value2 second value to compare
   * @return 0 when equals, -1 ot 1 to establish order
   */
  @Override
  public int compareValues(ByteString value1, ByteString value2)
  {
    String[] token1 = value1.stringValue().split(":", 3);
    String[] token2 = value2.stringValue().split(":", 3);

    if ((token1[1] == null) || (token2[1] == null))
      return -1;

    return token1[1].compareTo(token2[1]);
  }

  /**
   * Initialization method.
   * Currently not used
   * @param configEntry unused
   */
  @Override
  public void initializeMatchingRule(ConfigEntry configEntry)
  {
    // TODO Auto-generated method stub
  }

  /**
   * Get the name of this class.
   * @return name of the class in String form
   */
  @Override
  public String getName()
  {
    return "historicalCsnOrderingMatch";
  }

  /**
   * Get the OID of the class.
   * @return the OID of the class in String form.
   */
  @Override
  public String getOID()
  {
    return "2.1.5.6.7.8.9.11.22.33.44"; // TODO use valid OID
  }

  /**
   * Get the description of this Class.
   * @return the Class description in String form, currently not used.
   */
  @Override
  public String getDescription()
  {
    return null;
  }

  /**
   * Get the Syntax OID for this class.
   * @return the syntax OID in String form
   */
  @Override
  public String getSyntaxOID()
  {
    return "2.1.5.6.7.8.9.11.22.33.445";  //TODO use valid OID
  }

  /**
   * Normalize historical information representation.
   * @param value the value that must be normalized
   * @return The String form that must be used for historical information
   * comparison
   */
  @Override
  public ByteString normalizeValue(ByteString value)
  {
    String[] token = value.stringValue().split(":", 3);
    return new ASN1OctetString(token[1]);
  }

  /**
   * Compares two normalized representation of historical information.
   * @param b1 first value to compare
   * @param b2 second value to compare
   * @return 0, -1 or 1 depending on relative positions
   */
  public int compare(byte[] b1, byte[] b2)
  {
    int minLength = Math.min(b1.length, b2.length);

    for (int i=0; i < minLength; i++)
    {
      if (b1[i] == b2[i])
      {
        continue;
      }
      else if (b1[i] < b2[i])
      {
        return -1;
      }
      else if (b1[i] > b2[i])
      {
        return 1;
      }
    }

    if (b1.length == b2.length)
    {
      return 0;
    }
    else if (b1.length < b2.length)
    {
      return -1;
    }
    else
    {
      return 1;
    }
  }

}
