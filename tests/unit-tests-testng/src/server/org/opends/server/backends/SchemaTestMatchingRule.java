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
package org.opends.server.backends;



import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.schema.CaseIgnoreEqualityMatchingRuleFactory;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;


/**
 * This class implements an equality matching rule that is intended for testing
 * purposes within the server (e.g., in conjunction with matching rule uses).
 * For all practical purposes, it behaves like the standard caseIgnoreMatch
 * matching rule.
 */
public class SchemaTestMatchingRule
       extends EqualityMatchingRule
{
  // Indicates whether this matching rule should be considered OBSOLETE.
  private final boolean isObsolete;

  // The matching rule that will do all the real work behind the scenes.
  private final EqualityMatchingRule caseIgnoreMatchingRule;

  // The name for this matching rule.
  private final String name;

  // The OID for this matching rule.
  private final String oid;



  /**
   * Creates a new instance of this matching rule with the provided information.
   *
   * @param  name  The name to use for this matching rule.
   * @param  oid   The OID to use for this matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public SchemaTestMatchingRule(String name, String oid)
         throws Exception
  {
    super();

    this.name = name;
    this.oid  = oid;

    CaseIgnoreEqualityMatchingRuleFactory factory =
            new CaseIgnoreEqualityMatchingRuleFactory();
    factory.initializeMatchingRule(null);
    caseIgnoreMatchingRule = (EqualityMatchingRule)factory.
            getMatchingRules().iterator().next();
    isObsolete = false;
  }



  /**
   * Creates a new instance of this matching rule with the provided information.
   *
   * @param  name        The name to use for this matching rule.
   * @param  oid         The OID to use for this matching rule.
   * @param  isObsolete  Indicates whether this matching rule should be marked
   *                     OBSOLETE.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public SchemaTestMatchingRule(String name, String oid, boolean isObsolete)
         throws Exception
  {
    super();

    this.name       = name;
    this.oid        = oid;
    this.isObsolete = isObsolete;

    CaseIgnoreEqualityMatchingRuleFactory factory =
            new CaseIgnoreEqualityMatchingRuleFactory();
    factory.initializeMatchingRule(null);
    caseIgnoreMatchingRule = (EqualityMatchingRule)factory.
            getMatchingRules().iterator().next();
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
    return name;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return oid;
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
    return caseIgnoreMatchingRule.getSyntaxOID();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isObsolete()
  {
    return isObsolete;
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
    return caseIgnoreMatchingRule.normalizeValue(value);
  }



  /**
   * Indicates whether the two provided normalized values are equal to each
   * other.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  <CODE>true</CODE> if the provided values are equal, or
   *          <CODE>false</CODE> if not.
   */
  @Override
  public boolean areEqual(ByteSequence value1, ByteSequence value2)
  {
    return caseIgnoreMatchingRule.areEqual(value1, value2);
  }
}

