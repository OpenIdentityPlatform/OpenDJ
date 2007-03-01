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



import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the attribute type description syntax, which is used to
 * hold attribute type definitions in the server schema.  The format of this
 * syntax is defined in RFC 2252.
 */
public class AttributeTypeSyntax
       extends AttributeSyntax
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
  public AttributeTypeSyntax()
  {
    super();

  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE;
      String message = getMessage(msgID, EMR_CASE_IGNORE_OID,
                                  SYNTAX_ATTRIBUTE_TYPE_NAME);
      throw new InitializationException(msgID, message);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE;
      String message = getMessage(msgID, OMR_CASE_IGNORE_OID,
                                  SYNTAX_ATTRIBUTE_TYPE_NAME);
      throw new InitializationException(msgID, message);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE;
      String message = getMessage(msgID, SMR_CASE_IGNORE_OID,
                                  SYNTAX_ATTRIBUTE_TYPE_NAME);
      throw new InitializationException(msgID, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  public String getSyntaxName()
  {
    return SYNTAX_ATTRIBUTE_TYPE_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public String getOID()
  {
    return SYNTAX_ATTRIBUTE_TYPE_OID;
  }



  /**
   * {@inheritDoc}
   */
  public String getDescription()
  {
    return SYNTAX_ATTRIBUTE_TYPE_DESCRIPTION;
  }



  /**
   * {@inheritDoc}
   */
  public EqualityMatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    return defaultOrderingMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    return defaultSubstringMatchingRule;
  }



  /**
   * {@inheritDoc}
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    // There is no approximate matching rule by default.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public boolean valueIsAcceptable(ByteString value,
                                   StringBuilder invalidReason)
  {
    // We'll use the decodeAttributeType method to determine if the value is
    // acceptable.
    try
    {
      decodeAttributeType(value, DirectoryServer.getSchema(), true);
      return true;
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, de);
      }

      invalidReason.append(de.getErrorMessage());
      return false;
    }
  }



  /**
   * Decodes the contents of the provided ASN.1 octet string as an attribute
   * type definition according to the rules of this syntax.  Note that the
   * provided octet string value does not need to be normalized (and in fact, it
   * should not be in order to allow the desired capitalization to be
   * preserved).
   *
   * @param  value                 The ASN.1 octet string containing the value
   *                               to decode (it does not need to be
   *                               normalized).
   * @param  schema                The schema to use to resolve references to
   *                               other schema elements.
   * @param  allowUnknownElements  Indicates whether to allow values that
   *                               reference a superior attribute type which are
   *                               not defined in the server schema. This should
   *                               only be true when called by
   *                               {@code valueIsAcceptable}.
   *
   * @return  The decoded attribute type definition.
   *
   * @throws  DirectoryException  If the provided value cannot be decoded as an
   *                              attribute type definition.
   */
  public static AttributeType decodeAttributeType(ByteString value,
                                                  Schema schema,
                                                  boolean allowUnknownElements)
         throws DirectoryException
  {
    // Get string representations of the provided value using the provided form
    // and with all lowercase characters.
    String valueStr = value.stringValue();
    String lowerStr = toLowerCase(valueStr);


    // We'll do this a character at a time.  First, skip over any leading
    // whitespace.
    int pos    = 0;
    int length = valueStr.length();
    while ((pos < length) && (valueStr.charAt(pos) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the value was empty or contained only whitespace.  That
      // is illegal.
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_EMPTY_VALUE;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);
    if (c != '(')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_OPEN_PARENTHESIS;
      String message = getMessage(msgID, valueStr, (pos-1), c);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Skip over any spaces immediately following the opening parenthesis.
    while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next set of characters must be the OID.  Strictly speaking, this
    // should only be a numeric OID, but we'll also allow for the
    // "attrname-oid" case as well.  Look at the first character to figure out
    // which we will be using.
    int oidStartPos = pos;
    if (isDigit(c))
    {
      // This must be a numeric OID.  In that case, we will accept only digits
      // and periods, but not consecutive periods.
      boolean lastWasPeriod = false;
      while ((pos < length) && ((c = valueStr.charAt(pos++)) != ' '))
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID;
            String message = getMessage(msgID, valueStr, (pos-1));
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (! isDigit(c))
        {
          // This must have been an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID;
          String message = getMessage(msgID, valueStr, c, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
        else
        {
          lastWasPeriod = false;
        }
      }
    }
    else
    {
      // This must be a "fake" OID.  In this case, we will only accept
      // alphabetic characters, numeric digits, and the hyphen.
      while ((pos < length) && ((c = valueStr.charAt(pos++)) != ' '))
      {
        if (isAlpha(c) || isDigit(c) || (c == '-') ||
            ((c == '_') && DirectoryServer.allowAttributeNameExceptions()))
        {
          // This is fine.  It is an acceptable character.
        }
        else
        {
          // This must have been an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_STRING_OID;
          String message = getMessage(msgID, valueStr, c, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
    }


    // If we're at the end of the value, then it isn't a valid attribute type
    // description.  Otherwise, parse out the OID.
    String oid;
    if (pos >= length)
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }
    else
    {
      oid = lowerStr.substring(oidStartPos, (pos-1));
    }


    // Skip over the space(s) after the OID.
    while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // At this point, we should have a pretty specific syntax that describes
    // what may come next, but some of the components are optional and it would
    // be pretty easy to put something in the wrong order, so we will be very
    // flexible about what we can accept.  Just look at the next token, figure
    // out what it is and how to treat what comes after it, then repeat until
    // we get to the end of the value.  But before we start, set default values
    // for everything else we might need to know.
    String  primaryName = oid;
    List<String> typeNames = new LinkedList<String>();
    String description = null;
    AttributeType superiorType = null;
    AttributeSyntax syntax = DirectoryServer.getDefaultAttributeSyntax();
    ApproximateMatchingRule approximateMatchingRule = null;
    EqualityMatchingRule equalityMatchingRule = null;
    OrderingMatchingRule orderingMatchingRule = null;
    SubstringMatchingRule substringMatchingRule = null;
    AttributeUsage attributeUsage = AttributeUsage.USER_APPLICATIONS;
    boolean isCollective = false;
    boolean isNoUserModification = false;
    boolean isObsolete = false;
    boolean isSingleValue = false;
    HashMap<String,List<String>> extraProperties =
         new LinkedHashMap<String,List<String>>();


    while (true)
    {
      StringBuilder tokenNameBuffer = new StringBuilder();
      pos = readTokenName(valueStr, tokenNameBuffer, pos);
      String tokenName = tokenNameBuffer.toString();
      String lowerTokenName = toLowerCase(tokenName);
      if (tokenName.equals(")"))
      {
        // We must be at the end of the value.  If not, then that's a problem.
        if (pos < length)
        {
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_UNEXPECTED_CLOSE_PARENTHESIS;
          String message = getMessage(msgID, valueStr, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }

        break;
      }
      else if (lowerTokenName.equals("name"))
      {
        // This specifies the set of names for the attribute type.  It may be a
        // single name in single quotes, or it may be an open parenthesis
        // followed by one or more names in single quotes separated by spaces.
        c = valueStr.charAt(pos++);
        if (c == '\'')
        {
          StringBuilder userBuffer  = new StringBuilder();
          StringBuilder lowerBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, lowerStr, userBuffer, lowerBuffer,
                                 (pos-1));
          primaryName = userBuffer.toString();
          typeNames.add(primaryName);
        }
        else if (c == '(')
        {
          StringBuilder userBuffer  = new StringBuilder();
          StringBuilder lowerBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, lowerStr, userBuffer, lowerBuffer,
                                 pos);
          primaryName = userBuffer.toString();
          typeNames.add(primaryName);


          while (true)
          {
            if (valueStr.charAt(pos) == ')')
            {
              // Skip over any spaces after the parenthesis.
              pos++;
              while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
              {
                pos++;
              }

              break;
            }
            else
            {
              userBuffer  = new StringBuilder();
              lowerBuffer = new StringBuilder();

              pos = readQuotedString(valueStr, lowerStr, userBuffer,
                                     lowerBuffer, pos);
              typeNames.add(userBuffer.toString());
            }
          }
        }
        else
        {
          // This is an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR;
          String message = getMessage(msgID, valueStr, c, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
      else if (lowerTokenName.equals("desc"))
      {
        // This specifies the description for the attribute type.  It is an
        // arbitrary string of characters enclosed in single quotes.
        StringBuilder descriptionBuffer = new StringBuilder();
        pos = readQuotedString(valueStr, descriptionBuffer, pos);
        description = descriptionBuffer.toString();
      }
      else if (lowerTokenName.equals("obsolete"))
      {
        // This indicates whether the attribute type should be considered
        // obsolete.  We do not need to do any more parsing for this token.
        isObsolete = true;
      }
      else if (lowerTokenName.equals("sup"))
      {
        // This specifies the name or OID of the superior attribute type from
        // which this attribute type should inherit its properties.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        superiorType = schema.getAttributeType(woidBuffer.toString());
        if (superiorType == null)
        {
          if (allowUnknownElements)
          {
            superiorType = DirectoryServer.getDefaultAttributeType(
                                                woidBuffer.toString());
          }
          else
          {
            // This is bad because we don't know what the superior attribute
            // type is so we can't base this attribute type on it.
            int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUPERIOR_TYPE;
            String message = getMessage(msgID, String.valueOf(oid),
                                        String.valueOf(woidBuffer));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message, msgID);
          }
        }


        // Use the information in the superior type to provide defaults for the
        // rest of the components in this attribute type description.
        // Technically, the definition of the superior type should be provided
        // before the matching rule, syntax, single-value, collective,
        // no-user-modification, and usage components, and in that case we won't
        // undo something else that has already been set by an earlier
        // definition.  However, if the information is provided out-of-order,
        // then it is possible that this could overwrite some desired setting
        // that is different from that of the supertype.
        approximateMatchingRule = superiorType.getApproximateMatchingRule();
        equalityMatchingRule    = superiorType.getEqualityMatchingRule();
        orderingMatchingRule    = superiorType.getOrderingMatchingRule();
        substringMatchingRule   = superiorType.getSubstringMatchingRule();
        syntax                  = superiorType.getSyntax();
        isSingleValue           = superiorType.isSingleValue();
        isCollective            = superiorType.isCollective();
        isNoUserModification    = superiorType.isNoUserModification();
        attributeUsage          = superiorType.getUsage();
      }
      else if (lowerTokenName.equals("equality"))
      {
        // This specifies the name or OID of the equality matching rule to use
        // for this attribute type.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        EqualityMatchingRule emr =
             schema.getEqualityMatchingRule(woidBuffer.toString());
        if (emr == null)
        {
          // This is bad because we have no idea what the equality matching
          // rule should be.
          int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_EQUALITY_MR;
          String message = getMessage(msgID, String.valueOf(oid),
                                      String.valueOf(woidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message, msgID);
        }
        else
        {
          equalityMatchingRule = emr;
        }
      }
      else if (lowerTokenName.equals("ordering"))
      {
        // This specifies the name or OID of the ordering matching rule to use
        // for this attribute type.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        OrderingMatchingRule omr =
             schema.getOrderingMatchingRule(woidBuffer.toString());
        if (omr == null)
        {
          // This is bad because we have no idea what the ordering matching
          // rule should be.
          int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_ORDERING_MR;
          String message = getMessage(msgID, String.valueOf(oid),
                                      String.valueOf(woidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message, msgID);
        }
        else
        {
          orderingMatchingRule = omr;
        }
      }
      else if (lowerTokenName.equals("substr"))
      {
        // This specifies the name or OID of the substring matching rule to use
        // for this attribute type.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        SubstringMatchingRule smr =
             schema.getSubstringMatchingRule(woidBuffer.toString());
        if (smr == null)
        {
          // This is bad because we have no idea what the substring matching
          // rule should be.
          int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SUBSTRING_MR;
          String message = getMessage(msgID, String.valueOf(oid),
                                      String.valueOf(woidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message, msgID);
        }
        else
        {
          substringMatchingRule = smr;
        }
      }
      else if (lowerTokenName.equals("syntax"))
      {
        // This specifies the numeric OID of the syntax for this matching rule.
        // It may optionally be immediately followed by an open curly brace, an
        // integer value, and a close curly brace to suggest the minimum number
        // of characters that should be allowed in values of that type.  This
        // implementation will ignore any such length because it does not
        // impose any practical limit on the length of attribute values.
        boolean inBrace         = false;
        boolean lastWasPeriod   = false;
        StringBuilder oidBuffer = new StringBuilder();
        while (pos < length)
        {
          c = lowerStr.charAt(pos++);
          if (inBrace)
          {
            // The only thing we'll allow here will be numeric digits and the
            // closing curly brace.
            if (c == '}')
            {
              // The next character must be a space.
              if ((c = lowerStr.charAt(pos)) != ' ')
              {
                int msgID =
                     MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID;
                String message = getMessage(msgID, valueStr, c, (pos-1));
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID);
              }

              break;
            }
            else if (! isDigit(c))
            {
              int msgID =
                   MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID;
              String message = getMessage(msgID, valueStr, c, (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message, msgID);
            }
          }
          else
          {
            if (isDigit(c))
            {
              oidBuffer.append(c);
              lastWasPeriod = false;
            }
            else if (c == '.')
            {
              if (lastWasPeriod)
              {
                int msgID =
                     MSGID_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID;
                String message = getMessage(msgID, valueStr, (pos-1));
                throw new DirectoryException(
                               ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                               msgID);
              }
              else
              {
                oidBuffer.append(c);
                lastWasPeriod = true;
              }
            }
            else if (c == '{')
            {
              // It's the start of the length specification.
              inBrace = true;
            }
            else if (c == ' ')
            {
              // It's the end of the value.
              break;
            }
            else
            {
              int msgID =
                   MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID;
              String message = getMessage(msgID, valueStr, c, (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message, msgID);
            }
          }
        }

        syntax = schema.getSyntax(oidBuffer.toString());
        if (syntax == null)
        {
          int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_SYNTAX;
          String message = getMessage(msgID, String.valueOf(oid),
                                      String.valueOf(oidBuffer));
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message, msgID);
        }

        if (approximateMatchingRule == null)
        {
          approximateMatchingRule = syntax.getApproximateMatchingRule();
        }

        if (equalityMatchingRule == null)
        {
          equalityMatchingRule = syntax.getEqualityMatchingRule();
        }

        if (orderingMatchingRule == null)
        {
          orderingMatchingRule = syntax.getOrderingMatchingRule();
        }

        if (substringMatchingRule == null)
        {
          substringMatchingRule = syntax.getSubstringMatchingRule();
        }
      }
      else if (lowerTokenName.equals("single-value"))
      {
        // This indicates that attributes of this type are allowed to have at
        // most one value.  We do not need any more parsing for this token.
        isSingleValue = true;
      }
      else if (lowerTokenName.equals("collective"))
      {
        // This indicates that attributes of this type are collective (i.e.,
        // have their values generated dynamically in some way).  We do not need
        // any more parsing for this token.
        isCollective = true;
      }
      else if (lowerTokenName.equals("no-user-modification"))
      {
        // This indicates that the values of attributes of this type are not to
        // be modified by end users.  We do not need any more parsing for this
        // token.
        isNoUserModification = true;
      }
      else if (lowerTokenName.equals("usage"))
      {
        // This specifies the usage string for this attribute type.  It should
        // be followed by one of the strings "userApplications",
        // "directoryOperation", "distributedOperation", or "dSAOperation".
        StringBuilder usageBuffer = new StringBuilder();
        while (pos < length)
        {
          c = lowerStr.charAt(pos++);
          if (c == ' ')
          {
            break;
          }
          else
          {
            usageBuffer.append(c);
          }
        }

        String usageStr = usageBuffer.toString();
        if (usageStr.equals("userapplications"))
        {
          attributeUsage = AttributeUsage.USER_APPLICATIONS;
        }
        else if (usageStr.equals("directoryoperation"))
        {
          attributeUsage = AttributeUsage.DIRECTORY_OPERATION;
        }
        else if (usageStr.equals("distributedoperation"))
        {
          attributeUsage = AttributeUsage.DISTRIBUTED_OPERATION;
        }
        else if (usageStr.equals("dsaoperation"))
        {
          attributeUsage = AttributeUsage.DSA_OPERATION;
        }
        else
        {
          // This must be an illegal usage.
          attributeUsage = AttributeUsage.USER_APPLICATIONS;

          int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_INVALID_ATTRIBUTE_USAGE;
          String message = getMessage(msgID, String.valueOf(oid),
                                      String.valueOf(usageBuffer),
                                      String.valueOf(attributeUsage));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
      else
      {
        // This must be a non-standard property and it must be followed by
        // either a single value in single quotes or an open parenthesis
        // followed by one or more values in single quotes separated by spaces
        // followed by a close parenthesis.
        List<String> valueList = new ArrayList<String>();
        pos = readExtraParameterValues(valueStr, valueList, pos);
        extraProperties.put(tokenName, valueList);
      }
    }

    List<String> approxRules = extraProperties.get(SCHEMA_PROPERTY_APPROX_RULE);
    if ((approxRules != null) && (! approxRules.isEmpty()))
    {
      String ruleName  = approxRules.get(0);
      String lowerName = toLowerCase(ruleName);
      ApproximateMatchingRule amr =
           schema.getApproximateMatchingRule(lowerName);
      if (amr == null)
      {
        // This is bad because we have no idea what the approximate matching
        // rule should be.
        int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_UNKNOWN_APPROXIMATE_MR;
        String message = getMessage(msgID, String.valueOf(oid),
                                    String.valueOf(ruleName));
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
      else
      {
        approximateMatchingRule = amr;
      }
    }


    // If there is a superior type, then it must have the same usage as the
    // subordinate type.  Also, if the superior type is collective, then so must
    // the subordinate type be collective.
    if (superiorType != null)
    {
      if (superiorType.getUsage() != attributeUsage)
      {
        int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_INVALID_SUPERIOR_USAGE;
        String message = getMessage(msgID, oid, String.valueOf(attributeUsage),
                                    superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }

      if (superiorType.isCollective() != isCollective)
      {
        int msgID;
        if (isCollective)
        {
          msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_COLLECTIVE_FROM_NONCOLLECTIVE;
        }
        else
        {
          msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_NONCOLLECTIVE_FROM_COLLECTIVE;
        }

        String message = getMessage(msgID, oid, superiorType.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }


    // If the attribute type is COLLECTIVE, then it must have a usage of
    // userApplications.
    if (isCollective && (attributeUsage != AttributeUsage.USER_APPLICATIONS))
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_COLLECTIVE_IS_OPERATIONAL;
      String message = getMessage(msgID, oid);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }


    // If the attribute type is NO-USER-MODIFICATION, then it must not have a
    // usage of userApplications.
    if (isNoUserModification &&
        (attributeUsage == AttributeUsage.USER_APPLICATIONS))
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_NO_USER_MOD_NOT_OPERATIONAL;
      String message = getMessage(msgID, oid);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }


    return new AttributeType(value.stringValue(), primaryName, typeNames, oid,
                             description, superiorType, syntax,
                             approximateMatchingRule, equalityMatchingRule,
                             orderingMatchingRule, substringMatchingRule,
                             attributeUsage, isCollective, isNoUserModification,
                             isObsolete, isSingleValue, extraProperties);
  }



  /**
   * Reads the next token name from the attribute type definition, skipping over
   * any leading or trailing spaces, and appends it to the provided buffer.
   *
   * @param  valueStr   The string representation of the attribute type
   *                    definition.
   * @param  tokenName  The buffer into which the token name will be written.
   * @param  startPos   The position in the provided string at which to start
   *                    reading the token name.
   *
   * @return  The position of the first character that is not part of the token
   *          name or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              token name.
   */
  private static int readTokenName(String valueStr, StringBuilder tokenName,
                                   int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Read until we find the next space.
    while ((startPos < length) && ((c = valueStr.charAt(startPos++)) != ' '))
    {
      tokenName.append(c);
    }


    // Skip over any trailing spaces after the value.
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping over the
   * quotes and any leading or trailing spaces, and appending the string to the
   * provided buffer.
   *
   * @param  valueStr     The user-provided representation of the attribute type
   *                      definition.
   * @param  valueBuffer  The buffer into which the user-provided representation
   *                      of the value will be placed.
   * @param  startPos     The position in the provided string at which to start
   *                      reading the quoted string.
   *
   * @return  The position of the first character that is not part of the quoted
   *          string or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              quoted string.
   */
  private static int readQuotedString(String valueStr,
                                      StringBuilder valueBuffer, int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE_AT_POS;
      String message = getMessage(msgID, valueStr, startPos, c);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Read until we find the closing quote.
    startPos++;
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) != '\''))
    {
      valueBuffer.append(c);
      startPos++;
    }


    // Skip over any trailing spaces after the value.
    startPos++;
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping over the
   * quotes and any leading or trailing spaces, and appending the string to the
   * provided buffer.
   *
   * @param  valueStr     The user-provided representation of the attribute type
   *                      definition.
   * @param  lowerStr     The all-lowercase representation of the attribute type
   *                      definition.
   * @param  userBuffer   The buffer into which the user-provided representation
   *                      of the value will be placed.
   * @param  lowerBuffer  The buffer into which the all-lowercase representation
   *                      of the value will be placed.
   * @param  startPos     The position in the provided string at which to start
   *                      reading the quoted string.
   *
   * @return  The position of the first character that is not part of the quoted
   *          string or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              quoted string.
   */
  private static int readQuotedString(String valueStr, String lowerStr,
                                      StringBuilder userBuffer,
                                      StringBuilder lowerBuffer, int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = lowerStr.length();
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, lowerStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_EXPECTED_QUOTE_AT_POS;
      String message = getMessage(msgID, valueStr, startPos, c);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Read until we find the closing quote.
    startPos++;
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) != '\''))
    {
      lowerBuffer.append(c);
      userBuffer.append(valueStr.charAt(startPos));
      startPos++;
    }


    // Skip over any trailing spaces after the value.
    startPos++;
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, lowerStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the attribute description or numeric OID from the provided string,
   * skipping over any leading or trailing spaces, and appending the value to
   * the provided buffer.
   *
   * @param  lowerStr    The string from which the name or OID is to be read.
   * @param  woidBuffer  The buffer into which the name or OID should be
   *                     appended.
   * @param  startPos    The position at which to start reading.
   *
   * @return  The position of the first character after the name or OID that is
   *          not a space.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              name or OID.
   */
  private static int readWOID(String lowerStr, StringBuilder woidBuffer,
                              int startPos)
          throws DirectoryException
  {
    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = lowerStr.length();
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, lowerStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be either numeric (for an OID) or alphabetic (for
    // an attribute description).
    if (isDigit(c))
    {
      // This must be a numeric OID.  In that case, we will accept only digits
      // and periods, but not consecutive periods.
      boolean lastWasPeriod = false;
      while ((startPos < length) && ((c = lowerStr.charAt(startPos++)) != ' '))
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_DOUBLE_PERIOD_IN_NUMERIC_OID;
            String message = getMessage(msgID, lowerStr, (startPos-1));
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          }
          else
          {
            woidBuffer.append(c);
            lastWasPeriod = true;
          }
        }
        else if (! isDigit(c))
        {
          // Technically, this must be an illegal character.  However, it is
          // possible that someone just got sloppy and did not include a space
          // between the name/OID and a closing parenthesis.  In that case,
          // we'll assume it's the end of the value.  What's more, we'll have
          // to prematurely return to nasty side effects from stripping off
          // additional characters.
          if (c == ')')
          {
            return (startPos-1);
          }

          // This must have been an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_NUMERIC_OID;
          String message = getMessage(msgID, lowerStr, c, (startPos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
        else
        {
          woidBuffer.append(c);
          lastWasPeriod = false;
        }
      }
    }
    else if (isAlpha(c))
    {
      // This must be an attribute description.  In this case, we will only
      // accept alphabetic characters, numeric digits, and the hyphen.
      while ((startPos < length) && ((c = lowerStr.charAt(startPos++)) != ' '))
      {
        if (isAlpha(c) || isDigit(c) || (c == '-') ||
            ((c == '_') && DirectoryServer.allowAttributeNameExceptions()))
        {
          woidBuffer.append(c);
        }
        else
        {
          // Technically, this must be an illegal character.  However, it is
          // possible that someone just got sloppy and did not include a space
          // between the name/OID and a closing parenthesis.  In that case,
          // we'll assume it's the end of the value.  What's more, we'll have
          // to prematurely return to nasty side effects from stripping off
          // additional characters.
          if (c == ')')
          {
            return (startPos-1);
          }

          // This must have been an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR_IN_STRING_OID;
          String message = getMessage(msgID, lowerStr, c, (startPos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
    }
    else
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR;
      String message = getMessage(msgID, lowerStr, c, startPos);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Skip over any trailing spaces after the value.
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, lowerStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value for an "extra" parameter.  It will handle a single unquoted
   * word (which is technically illegal, but we'll allow it), a single quoted
   * string, or an open parenthesis followed by a space-delimited set of quoted
   * strings or unquoted words followed by a close parenthesis.
   *
   * @param  valueStr   The string containing the information to be read.
   * @param  valueList  The list of "extra" parameter values read so far.
   * @param  startPos   The position in the value string at which to start
   *                    reading.
   *
   * @return  The "extra" parameter value that was read.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to read
   *                              the value.
   */
  private static int readExtraParameterValues(String valueStr,
                          List<String> valueList, int startPos)
          throws DirectoryException
  {
    // Skip over any leading spaces.
    int length = valueStr.length();
    char c = valueStr.charAt(startPos++);
    while ((startPos < length) && (c == ' '))
    {
      c = valueStr.charAt(startPos++);
    }

    if (startPos >= length)
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Look at the next character.  If it is a quote, then parse until the next
    // quote and end.  If it is an open parenthesis, then parse individual
    // values until the close parenthesis and end.  Otherwise, parse until the
    // next space and end.
    if (c == '\'')
    {
      // Parse until the closing quote.
      StringBuilder valueBuffer = new StringBuilder();
      while ((startPos < length) && ((c = valueStr.charAt(startPos++)) != '\''))
      {
        valueBuffer.append(c);
      }

      valueList.add(valueBuffer.toString());
    }
    else if (c == '(')
    {
      while (true)
      {
        // Skip over any leading spaces;
        startPos++;
        while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
        {
          startPos++;
        }

        if (startPos >= length)
        {
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
          String message = getMessage(msgID, valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }


        if (c == ')')
        {
          // This is the end of the list.
          break;
        }
        else if (c == '(')
        {
          // This is an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_CHAR;
          String message = getMessage(msgID, valueStr, c, startPos);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
        else
        {
          // We'll recursively call this method to deal with this.
          startPos = readExtraParameterValues(valueStr, valueList, startPos);
        }
      }
    }
    else
    {
      // Parse until the next space.
      StringBuilder valueBuffer = new StringBuilder();
      while ((startPos < length) && ((c = valueStr.charAt(startPos++)) != ' '))
      {
        valueBuffer.append(c);
      }

      valueList.add(valueBuffer.toString());
    }



    // Skip over any trailing spaces.
    while ((startPos < length) && (valueStr.charAt(startPos) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRTYPE_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    return startPos;
  }
}

