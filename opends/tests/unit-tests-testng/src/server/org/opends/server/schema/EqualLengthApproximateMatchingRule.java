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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import java.util.Collection;
import java.util.Collections;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryException;

import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class implements an extremely simple approximate matching rule that will
 * consider two values approximately equal only if they have the same length.
 * It is intended purely for testing purposes.
 */
class EqualLengthApproximateMatchingRule
       extends ApproximateMatchingRule
{
  /**
   * Creates a new instance of this equal length approximate matching rule.
   */
  public EqualLengthApproximateMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
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
  public String getName()
  {
    return "equalLengthApproximateMatch";
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    return "1.3.6.1.4.1.26027.1.999.26";
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  public String getDescription()
  {
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  public String getSyntaxOID()
  {
    return SYNTAX_DIRECTORY_STRING_OID;
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
  public ByteString normalizeValue(ByteString value)
         throws DirectoryException
  {
    // Any value is acceptable, so we can just return a copy of the
    // value.
    return ByteStringFactory.create(value.value());
  }



  /**
   * Indicates whether the two provided normalized values are approximately
   * equal to each other.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  <CODE>true</CODE> if the provided values are approximately equal,
   *          or <CODE>false</CODE> if not.
   */
  public boolean approximatelyMatch(ByteString value1, ByteString value2)
  {
    return (value1.value().length == value2.value().length);
  }
}

