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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteSequence;


import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;


/**
 * This class implements the enhanced guide attribute syntax, which may be used
 * to provide criteria for generating search filters for entries of a given
 * objectclass.
 */
public class EnhancedGuideSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public EnhancedGuideSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_OCTET_STRING_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_OCTET_STRING_OID, SYNTAX_ENHANCED_GUIDE_NAME));
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_OCTET_STRING_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_OCTET_STRING_OID, SYNTAX_ENHANCED_GUIDE_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_OCTET_STRING_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_OCTET_STRING_OID, SYNTAX_ENHANCED_GUIDE_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_ENHANCED_GUIDE_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_ENHANCED_GUIDE_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_ENHANCED_GUIDE_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }



  /**
   * Retrieves the default ordering matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default ordering matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if ordering
   *          matches will not be allowed for this type by default.
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    return defaultOrderingMatchingRule;
  }



  /**
   * Retrieves the default substring matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default substring matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if substring
   *          matches will not be allowed for this type by default.
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    return defaultSubstringMatchingRule;
  }



  /**
   * Retrieves the default approximate matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default approximate matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if approximate
   *          matches will not be allowed for this type by default.
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    // There is no approximate matching rule by default.
    return null;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
   *                        appended.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteSequence value,
                                   MessageBuilder invalidReason)
  {
    // Get a lowercase string version of the provided value.
    String valueStr = toLowerCase(value.toString());


    // Find the position of the first octothorpe.  It should denote the end of
    // the objectclass.
    int sharpPos = valueStr.indexOf('#');
    if (sharpPos < 0)
    {

      invalidReason.append(
              ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SHARP.get(valueStr));
      return false;
    }


    // Get the objectclass and see if it is a valid name or OID.
    String ocName   = valueStr.substring(0, sharpPos).trim();
    int    ocLength = ocName.length();
    if (ocLength == 0)
    {

      invalidReason.append(ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_OC.get(valueStr));
      return false;
    }

    if (! isValidSchemaElement(ocName, 0, ocLength, invalidReason))
    {
      return false;
    }


    // Find the last octothorpe and make sure it is followed by a valid scope.
    int lastSharpPos = valueStr.lastIndexOf('#');
    if (lastSharpPos == sharpPos)
    {

      invalidReason.append(
              ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_FINAL_SHARP.get(valueStr));
      return false;
    }

    String scopeStr = valueStr.substring(lastSharpPos+1).trim();
    if (! (scopeStr.equals("baseobject") || scopeStr.equals("onelevel") ||
           scopeStr.equals("wholesubtree") ||
           scopeStr.equals("subordinatesubtree")))
    {
      if (scopeStr.length() == 0)
      {

        invalidReason.append(
                ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_SCOPE.get(valueStr));
      }
      else
      {

        invalidReason.append(
                ERR_ATTR_SYNTAX_ENHANCEDGUIDE_INVALID_SCOPE.get(
                        valueStr, scopeStr));
      }

      return false;
    }


    // Everything between the two octothorpes must be the criteria.  Make sure
    // it is valid.
    String criteria       = valueStr.substring(sharpPos+1, lastSharpPos).trim();
    int    criteriaLength = criteria.length();
    if (criteriaLength == 0)
    {

      invalidReason.append(
              ERR_ATTR_SYNTAX_ENHANCEDGUIDE_NO_CRITERIA.get(valueStr));
      return false;
    }

    return GuideSyntax.criteriaIsValid(criteria, valueStr, invalidReason);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBinary()
  {
    return false;
  }
}

