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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;



import java.util.Collection;
import java.util.Collections;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;

import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class defines the generalizedTimeOrderingMatch matching rule defined in
 * X.520 and referenced in RFC 2252.
 */
public class GeneralizedTimeOrderingMatchingRule
       extends AbstractOrderingMatchingRule
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -6343622924726948145L;



  /**
   * Creates a new instance of this generalizedTimeMatch matching rule.
   */
  public GeneralizedTimeOrderingMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(OMR_GENERALIZED_TIME_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return OMR_GENERALIZED_TIME_OID;
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
    return SYNTAX_GENERALIZED_TIME_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DecodeException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeAttributeValue(ByteSequence value)
         throws DecodeException
  {
    try
    {
      long timestamp = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(value);
      return ByteString.valueOf(GeneralizedTimeSyntax.format(timestamp));
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          throw DecodeException.error(de.getMessageObject(), de);
        case WARN:
          logger.error(de.getMessageObject());
          break;
      }
      return value.toByteString();
    }
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
  @Override
  public int compareValues(ByteSequence value1, ByteSequence value2)
  {
    try
    {
      long time1 = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(value1);
      long time2 = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(value2);

      if (time1 == time2)
      {
        return 0;
      }
      else if (time1 > time2)
      {
        return 1;
      }
      else
      {
        return -1;
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      return 0;
    }
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
  @Override
  public int compare(byte[] b1, byte[] b2)
  {
    return compareValues(ByteString.wrap(b1), ByteString.wrap(b2));
}
}

