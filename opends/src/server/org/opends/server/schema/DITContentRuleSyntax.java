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
package org.opends.server.schema;



import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
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
                                  SYNTAX_DIT_CONTENT_RULE_NAME);
      throw new InitializationException(msgID, message);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE;
      String message = getMessage(msgID, OMR_CASE_IGNORE_OID,
                                  SYNTAX_DIT_CONTENT_RULE_NAME);
      throw new InitializationException(msgID, message);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE;
      String message = getMessage(msgID, SMR_CASE_IGNORE_OID,
                                  SYNTAX_DIT_CONTENT_RULE_NAME);
      throw new InitializationException(msgID, message);
    }
  }



  /**
   * {@inheritDoc}
   */
  public String getSyntaxName()
  {

    return SYNTAX_DIT_CONTENT_RULE_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public String getOID()
  {

    return SYNTAX_DIT_CONTENT_RULE_OID;
  }



  /**
   * {@inheritDoc}
   */
  public String getDescription()
  {

    return SYNTAX_DIT_CONTENT_RULE_DESCRIPTION;
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


    // We'll use the decodeDITContentRule method to determine if the value is
    // acceptable.
    try
    {
      decodeDITContentRule(value, DirectoryServer.getSchema(), true);
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
   * Decodes the contents of the provided ASN.1 octet string as a DIT content
   * rule definition according to the rules of this syntax.  Note that the
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
   *                               reference a name form and/or superior rules
   *                               which are not defined in the server schema.
   *                               This should only be true when called by
   *                               {@code valueIsAcceptable}.
   *
   * @return  The decoded DIT content rule definition.
   *
   * @throws  DirectoryException  If the provided value cannot be decoded as an
   *                              DIT content rule definition.
   */
  public static DITContentRule decodeDITContentRule(ByteString value,
                                    Schema schema, boolean allowUnknownElements)
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
    // not structural, then fail.
    ObjectClass structuralClass = schema.getObjectClass(oid);
    if (structuralClass == null)
    {
      if (allowUnknownElements)
      {
        structuralClass = DirectoryServer.getDefaultObjectClass(oid);
      }
      else
      {
        int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_STRUCTURAL_CLASS;
        String message = getMessage(msgID, valueStr, oid);
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }
    }
    else if (structuralClass.getObjectClassType() != ObjectClassType.STRUCTURAL)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DCR_STRUCTURAL_CLASS_NOT_STRUCTURAL;
      String message =
           getMessage(msgID, valueStr, oid, structuralClass.getNameOrOID(),
                      String.valueOf(structuralClass.getObjectClassType()));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
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
    LinkedHashMap<String,String> names = new LinkedHashMap<String,String>();
    String description = null;
    boolean isObsolete = false;
    LinkedHashSet<ObjectClass> auxiliaryClasses =
         new LinkedHashSet<ObjectClass>();
    LinkedHashSet<AttributeType> requiredAttributes =
         new LinkedHashSet<AttributeType>();
    LinkedHashSet<AttributeType> optionalAttributes =
         new LinkedHashSet<AttributeType>();
    LinkedHashSet<AttributeType> prohibitedAttributes =
         new LinkedHashSet<AttributeType>();
    LinkedHashMap<String,List<String>> extraProperties =
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
              // This isn't good because it is an unknown auxiliary class.
              if (allowUnknownElements)
              {
                oc = DirectoryServer.getDefaultAuxiliaryObjectClass(
                                          woidBuffer.toString());
              }
              else
              {
                int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_AUXILIARY_CLASS;
                String message = getMessage(msgID, valueStr,
                                            woidBuffer.toString());
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                             message, msgID);
              }
            }
            else if (oc.getObjectClassType() != ObjectClassType.AUXILIARY)
            {
              // This isn't good because it isn't an auxiliary class.
              int msgID = MSGID_ATTR_SYNTAX_DCR_AUXILIARY_CLASS_NOT_AUXILIARY;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString(),
                                          oc.getObjectClassType().toString());
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
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
            // This isn't good because it is an unknown auxiliary class.
            if (allowUnknownElements)
            {
              oc = DirectoryServer.getDefaultAuxiliaryObjectClass(
                                        woidBuffer.toString());
            }
            else
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_AUXILIARY_CLASS;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message, msgID);
            }
          }
          else if (oc.getObjectClassType() != ObjectClassType.AUXILIARY)
          {
            // This isn't good because it isn't an auxiliary class.
            int msgID = MSGID_ATTR_SYNTAX_DCR_AUXILIARY_CLASS_NOT_AUXILIARY;
            String message = getMessage(msgID, valueStr, woidBuffer.toString(),
                                        oc.getObjectClassType().toString());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
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
              if (allowUnknownElements)
              {
                attr = DirectoryServer.getDefaultAttributeType(
                                            woidBuffer.toString());
              }
              else
              {
                int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_REQUIRED_ATTR;
                String message = getMessage(msgID, valueStr,
                                            woidBuffer.toString());
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                             message, msgID);
              }
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
            // requires an attribute type that we don't know anything about.
            if (allowUnknownElements)
            {
              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }
            else
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_REQUIRED_ATTR;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message, msgID);
            }
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
              // allows an attribute type that we don't know anything about.
              if (allowUnknownElements)
              {
                attr = DirectoryServer.getDefaultAttributeType(
                                            woidBuffer.toString());
              }
              else
              {
                int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_OPTIONAL_ATTR;
                String message = getMessage(msgID, valueStr,
                                            woidBuffer.toString());
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                             message, msgID);
              }
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
            // an attribute type that we don't know anything about.
            if (allowUnknownElements)
            {
              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }
            else
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_OPTIONAL_ATTR;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message, msgID);
            }
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
              if (allowUnknownElements)
              {
                attr = DirectoryServer.getDefaultAttributeType(
                                            woidBuffer.toString());
              }
              else
              {
                int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_PROHIBITED_ATTR;
                String message = getMessage(msgID, valueStr,
                                            woidBuffer.toString());
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                             message, msgID);
              }
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
            if (allowUnknownElements)
            {
              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }
            else
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DCR_UNKNOWN_PROHIBITED_ATTR;
              String message = getMessage(msgID, valueStr,
                                          woidBuffer.toString());
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message, msgID);
            }
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
        LinkedList<String> valueList = new LinkedList<String>();
        pos = readExtraParameterValues(valueStr, valueList, pos);
        extraProperties.put(tokenName, valueList);
      }
    }


    // Make sure that none of the prohibited attributes is required by the
    // structural or any of the auxiliary classes.
    for (AttributeType t : prohibitedAttributes)
    {
      if (structuralClass.isRequired(t))
      {
        int msgID = MSGID_ATTR_SYNTAX_DCR_PROHIBITED_REQUIRED_BY_STRUCTURAL;
        String message = getMessage(msgID, valueStr, t.getNameOrOID(),
                                    structuralClass.getNameOrOID());
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                     msgID);
      }

      for (ObjectClass oc : auxiliaryClasses)
      {
        if (oc.isRequired(t))
        {
          int msgID = MSGID_ATTR_SYNTAX_DCR_PROHIBITED_REQUIRED_BY_AUXILIARY;
          String message = getMessage(msgID, valueStr, t.getNameOrOID(),
                                      oc.getNameOrOID());
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                       msgID);
        }
      }
    }


    return new DITContentRule(value.stringValue(), structuralClass, names,
                              description, auxiliaryClasses, requiredAttributes,
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
                                              List<String> valueList,
                                              int startPos)
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

