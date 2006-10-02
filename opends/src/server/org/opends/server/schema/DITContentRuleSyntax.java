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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.schema;



import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the DIT content rule description syntax, which is used
 * to hold DIT content rule definitions in the server schema.  The format of
 * this syntax is defined in RFC 2252.
 */
public class DITContentRuleSyntax
       extends AttributeSyntax
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.schema.DITContentRuleSyntax";



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
  public DITContentRuleSyntax()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this attribute syntax based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this attribute syntax.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */
  public void initializeSyntax(ConfigEntry configEntry)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "initializeSyntax",
                      String.valueOf(configEntry));

    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_CASE_IGNORE_OID, SYNTAX_DIT_CONTENT_RULE_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
               OMR_CASE_IGNORE_OID, SYNTAX_DIT_CONTENT_RULE_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
               SMR_CASE_IGNORE_OID, SYNTAX_DIT_CONTENT_RULE_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    assert debugEnter(CLASS_NAME, "getSyntaxName");

    return SYNTAX_DIT_CONTENT_RULE_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    assert debugEnter(CLASS_NAME, "getOID");

    return SYNTAX_DIT_CONTENT_RULE_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return SYNTAX_DIT_CONTENT_RULE_DESCRIPTION;
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
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRule");

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
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRule");

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
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRule");

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
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRule");

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
  public boolean valueIsAcceptable(ByteString value,
                                   StringBuilder invalidReason)
  {
    assert debugEnter(CLASS_NAME, "valueIsAcceptable", String.valueOf(value),
                      "java.lang.StringBuilder");


    // We'll use the decodeDITContentRule method to determine if the value is
    // acceptable.
    try
    {
      decodeDITContentRule(value, DirectoryServer.getSchema());
      return true;
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "valueIsAcceptable", de);

      invalidReason.append(de.getErrorMessage());
      return false;
    }
  }



  /**
   * Decodes the contents of the provided ASN.1 octet string as a DIT content
   * rule definition according to the rules of this syntax.  Note that the
   * provided octet string value does not need to be normalized (and in fact, it
   * should not be in order to allow the desired capitalization to be
   * preserved).
   *
   * @param  value   The ASN.1 octet string containing the value to decode (it
   *                 does not need to be normalized).
   * @param  schema  The schema to use to resolve references to other schema
   *                 elements.
   *
   * @return  The decoded DIT content rule definition.
   *
   * @throws  DirectoryException  If the provided value cannot be decoded as an
   *                              DIT content rule definition.
   */
  public static DITContentRule decodeDITContentRule(ByteString value,
                                                    Schema schema)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "decodeDITContentRule",
                      String.valueOf(value));


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
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_EMPTY_VALUE;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);
    if (c != '(')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_EXPECTED_OPEN_PARENTHESIS;
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
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next set of characters must be the OID.  Strictly speaking, this
    // should only be a numeric OID, but we'll also allow for the
    // "ocname-oid" case as well.  Look at the first character to figure out
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
            int    msgID   = MSGID_ATTR_SYNTAX_DCR_DOUBLE_PERIOD_IN_NUMERIC_OID;
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
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_NUMERIC_OID;
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
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_STRING_OID;
          String message = getMessage(msgID, valueStr, c, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
    }


    // If we're at the end of the value, then it isn't a valid DIT content rule
    // description.  Otherwise, parse out the OID.
    String oid;
    if (pos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }
    else
    {
      oid = lowerStr.substring(oidStartPos, (pos-1));
    }


    // Get the objectclass with the specified OID.  If it does not exist or is
    // not structural, then log a warning but continue on.
    ObjectClass structuralClass = schema.getObjectClass(oid);
    if (structuralClass == null)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_STRUCTURAL_CLASS;
      String message = getMessage(msgID, valueStr, oid);
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);

      structuralClass = DirectoryServer.getDefaultObjectClass(oid);
    }
    else if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_STRUCTURAL_CLASS_NOT_STRUCTURAL;
      String message =
           getMessage(msgID, valueStr, oid, structuralClass.getNameOrOID(),
                      String.valueOf(structuralClass.getObjectClassType()));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
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
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
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
    ConcurrentHashMap<String,String> names =
         new ConcurrentHashMap<String,String>();
    String description = null;
    boolean isObsolete = false;
    CopyOnWriteArraySet<ObjectClass> auxiliaryClasses =
         new CopyOnWriteArraySet<ObjectClass>();
    CopyOnWriteArraySet<AttributeType> requiredAttributes =
         new CopyOnWriteArraySet<AttributeType>();
    CopyOnWriteArraySet<AttributeType> optionalAttributes =
         new CopyOnWriteArraySet<AttributeType>();
    CopyOnWriteArraySet<AttributeType> prohibitedAttributes =
         new CopyOnWriteArraySet<AttributeType>();
    ConcurrentHashMap<String,CopyOnWriteArrayList<String>> extraProperties =
         new ConcurrentHashMap<String,CopyOnWriteArrayList<String>>();


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
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNEXPECTED_CLOSE_PARENTHESIS;
          String message = getMessage(msgID, valueStr, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }

        break;
      }
      else if (lowerTokenName.equals("name"))
      {
        // This specifies the set of names for the DIT content rule.  It may be
        // a single name in single quotes, or it may be an open parenthesis
        // followed by one or more names in single quotes separated by spaces.
        c = valueStr.charAt(pos++);
        if (c == '\'')
        {
          StringBuilder userBuffer  = new StringBuilder();
          StringBuilder lowerBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, lowerStr, userBuffer, lowerBuffer,
                                 (pos-1));
          names.put(lowerBuffer.toString(), userBuffer.toString());
        }
        else if (c == '(')
        {
          StringBuilder userBuffer  = new StringBuilder();
          StringBuilder lowerBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, lowerStr, userBuffer, lowerBuffer,
                                 pos);
          names.put(lowerBuffer.toString(), userBuffer.toString());


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
              names.put(lowerBuffer.toString(), userBuffer.toString());
            }
          }
        }
        else
        {
          // This is an illegal character.
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR;
          String message = getMessage(msgID, valueStr, c, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
      else if (lowerTokenName.equals("desc"))
      {
        // This specifies the description for the DIT content rule.  It is an
        // arbitrary string of characters enclosed in single quotes.
        StringBuilder descriptionBuffer = new StringBuilder();
        pos = readQuotedString(valueStr, descriptionBuffer, pos);
        description = descriptionBuffer.toString();
      }
      else if (lowerTokenName.equals("obsolete"))
      {
        // This indicates whether the DIT content rule should be considered
        // obsolete.  We do not need to do any more parsing for this token.
        isObsolete = true;
      }
      else if (lowerTokenName.equals("aux"))
      {
        LinkedList<ObjectClass> ocs = new LinkedList<ObjectClass>();

        // This specifies the set of required auxiliary objectclasses for this
        // DIT content rule.  It may be a single name or OID (not in quotes), or
        // it may be an open parenthesis followed by one or more names separated
        // by spaces and the dollar sign character, followed by a closing
        // parenthesis.
        c = valueStr.charAt(pos++);
        if (c == '(')
        {
          while (true)
          {
            StringBuilder woidBuffer = new StringBuilder();
            pos = readWOID(lowerStr, woidBuffer, (pos));

            ObjectClass oc = schema.getObjectClass(woidBuffer.toString());
            if (oc == null)
            {
              // This isn't good because it is an unknown auxiliary class.  Log
              // a message and construct a default.
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_AUXILIARY_CLASS;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);

              oc = DirectoryServer.getDefaultObjectClass(woidBuffer.toString());
            }
            else if (oc.getObjectClassType() != ObjectClassType.AUXILIARY)
            {
              // This isn't good because it isn't an auxiliary class.  Log a
              // message but continue on.
              int msgID = MSGID_ATTR_SYNTAX_DCR_AUXILIARY_CLASS_NOT_AUXILIARY;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);
            }

            ocs.add(oc);


            // The next character must be either a dollar sign or a closing
            // parenthesis.
            c = valueStr.charAt(pos++);
            if (c == ')')
            {
              // This denotes the end of the list.
              break;
            }
            else if (c != '$')
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR;
              String message = getMessage(msgID, valueStr, c, (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message, msgID);
            }
          }
        }
        else
        {
          StringBuilder woidBuffer = new StringBuilder();
          pos = readWOID(lowerStr, woidBuffer, (pos-1));

          ObjectClass oc = schema.getObjectClass(woidBuffer.toString());
          if (oc == null)
          {
            // This isn't good because it is an unknown auxiliary class.  Log
            // a message and construct a default.
            int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_AUXILIARY_CLASS;
            String message = getMessage(msgID, valueStr, woidBuffer.toString());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            oc = DirectoryServer.getDefaultObjectClass(woidBuffer.toString());
          }
          else if (oc.getObjectClassType() != ObjectClassType.AUXILIARY)
          {
            // This isn't good because it isn't an auxiliary class.  Log a
            // message but continue on.
            int msgID = MSGID_ATTR_SYNTAX_DCR_AUXILIARY_CLASS_NOT_AUXILIARY;
            String message = getMessage(msgID, valueStr, woidBuffer.toString());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);
          }

          ocs.add(oc);
        }

        auxiliaryClasses.addAll(ocs);
      }
      else if (lowerTokenName.equals("must"))
      {
        LinkedList<AttributeType> attrs = new LinkedList<AttributeType>();

        // This specifies the set of required attributes for the DIT content
        // rule.  It may be a single name or OID (not in quotes), or it may be
        // an open parenthesis followed by one or more names separated by spaces
        // and the dollar sign character, followed by a closing parenthesis.
        c = valueStr.charAt(pos++);
        if (c == '(')
        {
          while (true)
          {
            StringBuilder woidBuffer = new StringBuilder();
            pos = readWOID(lowerStr, woidBuffer, (pos));

            AttributeType attr = schema.getAttributeType(woidBuffer.toString());
            if (attr == null)
            {
              // This isn't good because it means that the DIT content rule
              // requires an attribute type that we don't know anything about.
              // in this case all we can do is log a message and construct a
              // default type.
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_REQUIRED_ATTR;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);

              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }

            attrs.add(attr);


            // The next character must be either a dollar sign or a closing
            // parenthesis.
            c = valueStr.charAt(pos++);
            if (c == ')')
            {
              // This denotes the end of the list.
              break;
            }
            else if (c != '$')
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR;
              String message = getMessage(msgID, valueStr, c, (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message, msgID);
            }
          }
        }
        else
        {
          StringBuilder woidBuffer = new StringBuilder();
          pos = readWOID(lowerStr, woidBuffer, (pos-1));

          AttributeType attr = schema.getAttributeType(woidBuffer.toString());
          if (attr == null)
          {
            // This isn't good because it means that the DIT content rule
            // requires an attribute type that we don't know anything about.  In
            // this case all we can do is log a message and construct a default
            // type.
            int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_REQUIRED_ATTR;
            String message = getMessage(msgID, valueStr, woidBuffer.toString());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            attr = DirectoryServer.getDefaultAttributeType(
                                        woidBuffer.toString());
          }

          attrs.add(attr);
        }

        requiredAttributes.addAll(attrs);
      }
      else if (lowerTokenName.equals("may"))
      {
        LinkedList<AttributeType> attrs = new LinkedList<AttributeType>();

        // This specifies the set of optional attributes for the DIT content
        // rule.  It may be a single name or OID (not in quotes), or it may be
        // an open parenthesis followed by one or more names separated by spaces
        // and the dollar sign character, followed by a closing parenthesis.
        c = valueStr.charAt(pos++);
        if (c == '(')
        {
          while (true)
          {
            StringBuilder woidBuffer = new StringBuilder();
            pos = readWOID(lowerStr, woidBuffer, (pos));

            AttributeType attr = schema.getAttributeType(woidBuffer.toString());
            if (attr == null)
            {
              // This isn't good because it means that the DIT content rule
              // allows an attribute type that we don't know anything about.  In
              // this case all we can do is log a message and construct a
              // default type.
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_OPTIONAL_ATTR;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);

              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }

            attrs.add(attr);


            // The next character must be either a dollar sign or a closing
            // parenthesis.
            c = valueStr.charAt(pos++);
            if (c == ')')
            {
              // This denotes the end of the list.
              break;
            }
            else if (c != '$')
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR;
              String message = getMessage(msgID, valueStr, c, (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message, msgID);
            }
          }
        }
        else
        {
          StringBuilder woidBuffer = new StringBuilder();
          pos = readWOID(lowerStr, woidBuffer, (pos-1));

          AttributeType attr = schema.getAttributeType(woidBuffer.toString());
          if (attr == null)
          {
            // This isn't good because it means that the DIT content rule allows
            // an attribute type that we don't know anything about.  In this
            // case all we can do is log a message and construct a default type.
            int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_OPTIONAL_ATTR;
            String message = getMessage(msgID, valueStr, woidBuffer.toString());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            attr = DirectoryServer.getDefaultAttributeType(
                                        woidBuffer.toString());
          }

          attrs.add(attr);
        }

        optionalAttributes.addAll(attrs);
      }
      else if (lowerTokenName.equals("not"))
      {
        LinkedList<AttributeType> attrs = new LinkedList<AttributeType>();

        // This specifies the set of prohibited attributes for the DIT content
        // rule.  It may be a single name or OID (not in quotes), or it may be
        // an open parenthesis followed by one or more names separated by spaces
        // and the dollar sign character, followed by a closing parenthesis.
        c = valueStr.charAt(pos++);
        if (c == '(')
        {
          while (true)
          {
            StringBuilder woidBuffer = new StringBuilder();
            pos = readWOID(lowerStr, woidBuffer, (pos));

            AttributeType attr = schema.getAttributeType(woidBuffer.toString());
            if (attr == null)
            {
              // This isn't good because it means that the DIT content rule
              // prohibits an attribute type that we don't know anything about.
              // In this case all we can do is log a message and construct a
              // default type.
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_PROHIBITED_ATTR;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                       message, msgID);

              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }

            attrs.add(attr);


            // The next character must be either a dollar sign or a closing
            // parenthesis.
            c = valueStr.charAt(pos++);
            if (c == ')')
            {
              // This denotes the end of the list.
              break;
            }
            else if (c != '$')
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR;
              String message = getMessage(msgID, valueStr, c, (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message, msgID);
            }
          }
        }
        else
        {
          StringBuilder woidBuffer = new StringBuilder();
          pos = readWOID(lowerStr, woidBuffer, (pos-1));

          AttributeType attr = schema.getAttributeType(woidBuffer.toString());
          if (attr == null)
          {
            // This isn't good because it means that the DIT content rule
            // prohibits an attribute type that we don't know anything about.
            // In this case all we can do is log a message and construct a
            // default type.
            int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_PROHIBITED_ATTR;
            String message = getMessage(msgID, valueStr, woidBuffer.toString());
            logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_WARNING,
                     message, msgID);

            attr = DirectoryServer.getDefaultAttributeType(
                                        woidBuffer.toString());
          }

          attrs.add(attr);
        }

        prohibitedAttributes.addAll(attrs);
      }
      else
      {
        // This must be a non-standard property and it must be followed by
        // either a single value in single quotes or an open parenthesis
        // followed by one or more values in single quotes separated by spaces
        // followed by a close parenthesis.
        CopyOnWriteArrayList<String> valueList =
             new CopyOnWriteArrayList<String>();
        pos = readExtraParameterValues(valueStr, valueList, pos);
        extraProperties.put(tokenName, valueList);
      }
    }


    return new DITContentRule(structuralClass, names, description,
                              auxiliaryClasses, requiredAttributes,
                              optionalAttributes, prohibitedAttributes,
                              isObsolete, extraProperties);
  }



  /**
   * Reads the next token name from the DIT content rule definition, skipping
   * over any leading or trailing spaces, and appends it to the provided buffer.
   *
   * @param  valueStr   The string representation of the DIT content rule
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
    assert debugEnter(CLASS_NAME, "readTokenName", String.valueOf(valueStr),
                      "java.lang.StringBuilder", String.valueOf(startPos));


    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
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
   * @param  valueStr     The user-provided representation of the DIT content
   *                      rule definition.
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
    assert debugEnter(CLASS_NAME, "readQuotedString", String.valueOf(valueStr),
                      "java.lang.StringBuilder", String.valueOf(startPos));


    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_EXPECTED_QUOTE_AT_POS;
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
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
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
   * @param  valueStr     The user-provided representation of the DIT content
   *                      rule definition.
   * @param  lowerStr     The all-lowercase representation of the DIT content
   *                      rule definition.
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
    assert debugEnter(CLASS_NAME, "readQuotedString", String.valueOf(valueStr),
                      String.valueOf(lowerStr), "java.lang.StringBuilder",
                      "java.lang.StringBuilder", String.valueOf(startPos));


    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = lowerStr.length();
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
      String message = getMessage(msgID, lowerStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_EXPECTED_QUOTE_AT_POS;
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
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
      String message = getMessage(msgID, lowerStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads an attributeType/objectclass description or numeric OID from the
   * provided string, skipping over any leading or trailing spaces, and
   * appending the value to the provided buffer.
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
    assert debugEnter(CLASS_NAME, "readWOID", String.valueOf(lowerStr),
                      "java.lang.StringBuilder", String.valueOf(startPos));




    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = lowerStr.length();
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
      String message = getMessage(msgID, lowerStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be either numeric (for an OID) or alphabetic (for
    // an attribute type/objectclass description).
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
            int    msgID   = MSGID_ATTR_SYNTAX_DCR_DOUBLE_PERIOD_IN_NUMERIC_OID;
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
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_NUMERIC_OID;
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
      // This must be an attribute type/objectclass description.  In this case,
      // we will only accept alphabetic characters, numeric digits, and the
      // hyphen.
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
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR_IN_STRING_OID;
          String message = getMessage(msgID, lowerStr, c, (startPos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message, msgID);
        }
      }
    }
    else
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR;
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
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
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
                          CopyOnWriteArrayList<String> valueList, int startPos)
          throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "readExtraParameterValues",
                      String.valueOf(valueStr),
                      "java.util.concurrent.CopyOnWriteArrayList<String>",
                      String.valueOf(startPos));


    // Skip over any leading spaces.
    int length = valueStr.length();
    char c = valueStr.charAt(startPos++);
    while ((startPos < length) && (c == ' '))
    {
      c = valueStr.charAt(startPos++);
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
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
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
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
          int    msgID   = MSGID_ATTR_SYNTAX_DCR_ILLEGAL_CHAR;
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
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    return startPos;
  }
}

