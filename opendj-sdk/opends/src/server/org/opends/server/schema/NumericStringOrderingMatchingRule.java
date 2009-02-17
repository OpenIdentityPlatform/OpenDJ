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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.server.api.AbstractMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;



/**
 * This implements defines the numericStringOrderingMatch matching rule defined
 * in X.520 and referenced in RFC 2252.
 */
class NumericStringOrderingMatchingRule
       extends AbstractMatchingRule
       implements OrderingMatchingRule
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = 388436004219363604L;



  /**
   * Creates a new instance of this numericStringOrderingMatch matching rule.
   */
  public NumericStringOrderingMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getAllNames()
  {
    return Collections.singleton(getName());
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  @Override
  public String getName()
  {
    return OMR_NUMERIC_STRING_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return OMR_NUMERIC_STRING_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
    return SYNTAX_NUMERIC_STRING_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeValue(ByteSequence value)
         throws DirectoryException
  {
    String        valueString = value.toString();
    int           valueLength = valueString.length();
    StringBuilder valueBuffer = new StringBuilder(valueLength);

    boolean logged = false;
    for (int i=0; i < valueLength; i++)
    {
      char c = valueString.charAt(i);
      if (isDigit(c))
      {
        valueBuffer.append(c);
      }
      else if (c != ' ')
      {
        // This is an illegal character.  Either log it or reject it.

        Message message = WARN_ATTR_SYNTAX_NUMERIC_STRING_ILLEGAL_CHAR.get(
                valueString, String.valueOf(c), i);

        switch (DirectoryServer.getSyntaxEnforcementPolicy())
        {
          case REJECT:
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          case WARN:
            if (! logged)
            {
              ErrorLogger.logError(message);
              logged = true;
            }
        }
      }
    }

    return ByteString.valueOf(valueBuffer.toString());
  }



  /**
   * Compares the first value to the second and returns a value that indicates
   * their relative order.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  A negative integer if <CODE>value1</CODE> should come before
   *          <CODE>value2</CODE> in ascending order, a positive integer if
   *          <CODE>value1</CODE> should come after <CODE>value2</CODE> in
   *          ascending order, or zero if there is no difference between the
   *          values with regard to ordering.
   */
  public int compareValues(ByteSequence value1, ByteSequence value2)
  {
    return value1.compareTo(value2);
  }



  /**
   * Compares the contents of the provided byte arrays to determine their
   * relative order.
   *
   * @param  b1  The first byte array to use in the comparison.
   * @param  b2  The second byte array to use in the comparison.
   *
   * @return  A negative integer if <CODE>b1</CODE> should come before
   *          <CODE>b2</CODE> in ascending order, a positive integer if
   *          <CODE>b1</CODE> should come after <CODE>b2</CODE> in ascending
   *          order, or zero if there is no difference between the values with
   *          regard to ordering.
   */
  public int compare(byte[] b1, byte[] b2)
  {
    return StaticUtils.compare(b1, b2);
}
}

