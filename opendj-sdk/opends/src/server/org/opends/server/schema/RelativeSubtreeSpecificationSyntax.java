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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.AttributeValueDecoder;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.RelativeSubtreeSpecification;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

/**
 * This class defines the relative subtree specification attribute
 * syntax, which is used to specify the scope of access controls and
 * their parameters.
 */
public final class RelativeSubtreeSpecificationSyntax extends
    AttributeSyntax {

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;

  /**
   * Create a new attribute value decoder with the specified root DN.
   *
   * @param rootDN
   *          The root DN for all decoded relative subtree
   *          specifications.
   * @return The attribute value decoder.
   */
  public static AttributeValueDecoder<RelativeSubtreeSpecification>
      createAttributeValueDecoder(DN rootDN) {
    return new Decoder(rootDN);
  }

  /**
   * Internal class implementing an attribute value decoder.
   */
  private static class Decoder implements
      AttributeValueDecoder<RelativeSubtreeSpecification> {

    // The root DN for all decoded relative subtree specifications.
    private DN rootDN;

    /**
     * Create a new decoder with the specified root DN.
     *
     * @param rootDN
     *          The root DN for all decoded relative subtree
     *          specifications.
     */
    public Decoder(DN rootDN) {
      this.rootDN = rootDN;
    }

    /**
     * {@inheritDoc}
     */
    public RelativeSubtreeSpecification decode(AttributeValue value)
        throws DirectoryException {
      return RelativeSubtreeSpecification.valueOf(rootDN, value
          .getStringValue());
    }
  }

  /**
   * Creates a new instance of this syntax. Note that the only thing
   * that should be done here is to invoke the default constructor for
   * the superclass. All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public RelativeSubtreeSpecificationSyntax() {
    // No implementation required.
  }

  /**
   * Initializes this attribute syntax based on the information in the
   * provided configuration entry.
   *
   * @param configEntry
   *          The configuration entry that contains the information to
   *          use to initialize this attribute syntax.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization.
   */
  public void initializeSyntax(ConfigEntry configEntry)
      throws ConfigException {

    defaultEqualityMatchingRule = DirectoryServer
        .getEqualityMatchingRule(EMR_OCTET_STRING_OID);
    if (defaultEqualityMatchingRule == null) {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
          MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
          EMR_OCTET_STRING_OID, SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_NAME);
    }

    defaultOrderingMatchingRule = DirectoryServer
        .getOrderingMatchingRule(OMR_OCTET_STRING_OID);
    if (defaultOrderingMatchingRule == null) {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
          MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
          OMR_OCTET_STRING_OID, SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_NAME);
    }

    defaultSubstringMatchingRule = DirectoryServer
        .getSubstringMatchingRule(SMR_OCTET_STRING_OID);
    if (defaultSubstringMatchingRule == null) {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
          MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
          SMR_OCTET_STRING_OID, SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_NAME);
    }
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return The common name for this attribute syntax.
   */
  public String getSyntaxName() {

    return SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return The OID for this attribute syntax.
   */
  public String getOID() {

    return SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return A description for this attribute syntax.
   */
  public String getDescription() {

    return SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_DESCRIPTION;
  }

  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return The default equality matching rule that will be used for
   *         attributes with this syntax, or <CODE>null</CODE> if
   *         equality matches will not be allowed for this type by
   *         default.
   */
  public EqualityMatchingRule getEqualityMatchingRule() {

    return defaultEqualityMatchingRule;
  }

  /**
   * Retrieves the default ordering matching rule that will be used for
   * attributes with this syntax.
   *
   * @return The default ordering matching rule that will be used for
   *         attributes with this syntax, or <CODE>null</CODE> if
   *         ordering matches will not be allowed for this type by
   *         default.
   */
  public OrderingMatchingRule getOrderingMatchingRule() {

    return defaultOrderingMatchingRule;
  }

  /**
   * Retrieves the default substring matching rule that will be used for
   * attributes with this syntax.
   *
   * @return The default substring matching rule that will be used for
   *         attributes with this syntax, or <CODE>null</CODE> if
   *         substring matches will not be allowed for this type by
   *         default.
   */
  public SubstringMatchingRule getSubstringMatchingRule() {

    return defaultSubstringMatchingRule;
  }

  /**
   * Retrieves the default approximate matching rule that will be used
   * for attributes with this syntax.
   *
   * @return The default approximate matching rule that will be used for
   *         attributes with this syntax, or <CODE>null</CODE> if
   *         approximate matches will not be allowed for this type by
   *         default.
   */
  public ApproximateMatchingRule getApproximateMatchingRule() {

    // There is no approximate matching rule by default.
    return null;
  }

  /**
   * Indicates whether the provided value is acceptable for use in an
   * attribute with this syntax. If it is not, then the reason may be
   * appended to the provided buffer.
   *
   * @param value
   *          The value for which to make the determination.
   * @param invalidReason
   *          The buffer to which the invalid reason should be appended.
   * @return <CODE>true</CODE> if the provided value is acceptable for
   *         use with this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteString value,
                                   StringBuilder invalidReason) {

    // Use the subtree specification code to make this determination.
    try {
      RelativeSubtreeSpecification.valueOf(DN.nullDN(), value.stringValue());

      return true;
    } catch (DirectoryException e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      invalidReason.append(e.getErrorMessage());
      return false;
    }
  }
}
