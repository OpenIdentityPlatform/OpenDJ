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
import org.opends.messages.Message;



import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.messages.SchemaMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the object class description syntax, which is used to
 * hold objectclass definitions in the server schema.  The format of this
 * syntax is defined in RFC 2252.
 */
public class ObjectClassSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
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
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public ObjectClassSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException, InitializationException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      Message message = ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_CASE_IGNORE_OID, SYNTAX_OBJECTCLASS_NAME);
      throw new InitializationException(message);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      Message message = ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_CASE_IGNORE_OID, SYNTAX_OBJECTCLASS_NAME);
      throw new InitializationException(message);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      Message message = ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_IGNORE_OID, SYNTAX_OBJECTCLASS_NAME);
      throw new InitializationException(message);
    }
  }



  /**
   * {@inheritDoc}
   */
  public String getSyntaxName()
  {
    return SYNTAX_OBJECTCLASS_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public String getOID()
  {
    return SYNTAX_OBJECTCLASS_OID;
  }



  /**
   * {@inheritDoc}
   */
  public String getDescription()
  {
    return SYNTAX_OBJECTCLASS_DESCRIPTION;
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
                                   MessageBuilder invalidReason)
  {
    // We'll use the decodeObjectClass method to determine if the value is
    // acceptable.
    try
    {
      decodeObjectClass(value, DirectoryServer.getSchema(), true);
      return true;
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      invalidReason.append(de.getMessageObject());
      return false;
    }
  }



  /**
   * Decodes the contents of the provided ASN.1 octet string as an objectclass
   * definition according to the rules of this syntax.  Note that the provided
   * octet string value does not need to be normalized (and in fact, it should
   * not be in order to allow the desired capitalization to be preserved).
   *
   * @param  value                 The ASN.1 octet string containing the value
   *                               to decode (it does not need to be
   *                               normalized).
   * @param  schema                The schema to use to resolve references to
   *                               other schema elements.
   * @param  allowUnknownElements  Indicates whether to allow values that
   *                               reference a superior class or required or
   *                               optional attribute types which are not
   *                               defined in the server schema.  This should
   *                               only be true when called by
   *                               {@code valueIsAcceptable}.
   *
   * @return  The decoded objectclass definition.
   *
   * @throws  DirectoryException  If the provided value cannot be decoded as an
   *                              objectclass definition.
   */
  public static ObjectClass decodeObjectClass(ByteString value, Schema schema,
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
      Message message = ERR_ATTR_SYNTAX_OBJECTCLASS_EMPTY_VALUE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);
    if (c != '(')
    {
      Message message = ERR_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_OPEN_PARENTHESIS.
          get(valueStr, (pos-1), String.valueOf(c));
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
            Message message =
                ERR_ATTR_SYNTAX_OBJECTCLASS_DOUBLE_PERIOD_IN_NUMERIC_OID.
                  get(valueStr, (pos-1));
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (! isDigit(c))
        {
          // This must have been an illegal character.
          Message message =
              ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_NUMERIC_OID.
                get(valueStr, String.valueOf(c), (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
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
          Message message =
              ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_STRING_OID.
                get(valueStr, String.valueOf(c), (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
    }


    // If we're at the end of the value, then it isn't a valid objectclass
    // description.  Otherwise, parse out the OID.
    String oid;
    if (pos >= length)
    {
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // At this point, we should have a pretty specific syntax that describes
    // what may come next, but some of the components are optional and it would
    // be pretty easy to put something in the wrong order, so we will be very
    // flexible about what we can accept.  Just look at the next token, figure
    // out what it is and how to treat what comes after it, then repeat until
    // we get to the end of the value.  But before we start, set default values
    // for everything else we might need to know.
    String primaryName = oid;
    List<String> names = new LinkedList<String>();
    String description = null;
    boolean isObsolete = false;
    ObjectClass superiorClass = DirectoryServer.getTopObjectClass();
    Set<AttributeType> requiredAttributes = new LinkedHashSet<AttributeType>();
    Set<AttributeType> optionalAttributes = new LinkedHashSet<AttributeType>();
    ObjectClassType objectClassType = superiorClass
        .getObjectClassType();
    Map<String, List<String>> extraProperties =
      new LinkedHashMap<String, List<String>>();


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
          Message message =
              ERR_ATTR_SYNTAX_OBJECTCLASS_UNEXPECTED_CLOSE_PARENTHESIS.
                get(valueStr, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }

        break;
      }
      else if (lowerTokenName.equals("name"))
      {
        // This specifies the set of names for the objectclass.  It may be a
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
          names.add(primaryName);
        }
        else if (c == '(')
        {
          StringBuilder userBuffer  = new StringBuilder();
          StringBuilder lowerBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, lowerStr, userBuffer, lowerBuffer,
                                 pos);
          primaryName = userBuffer.toString();
          names.add(primaryName);


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
              names.add(userBuffer.toString());
            }
          }
        }
        else
        {
          // This is an illegal character.
          Message message = ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR.get(
              valueStr, String.valueOf(c), (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
      else if (lowerTokenName.equals("desc"))
      {
        // This specifies the description for the objectclass.  It is an
        // arbitrary string of characters enclosed in single quotes.
        StringBuilder descriptionBuffer = new StringBuilder();
        pos = readQuotedString(valueStr, descriptionBuffer, pos);
        description = descriptionBuffer.toString();
      }
      else if (lowerTokenName.equals("obsolete"))
      {
        // This indicates whether the objectclass should be considered obsolete.
        // We do not need to do any more parsing for this token.
        isObsolete = true;
      }
      else if (lowerTokenName.equals("sup"))
      {
        // This specifies the name or OID of the superior objectclass from which
        // this objectclass should inherit its properties.
        StringBuilder woidBuffer = new StringBuilder();
        pos = readWOID(lowerStr, woidBuffer, pos);
        superiorClass = schema.getObjectClass(woidBuffer.toString());
        if (superiorClass == null)
        {
          if (allowUnknownElements)
          {
            superiorClass =
                 DirectoryServer.getDefaultObjectClass(woidBuffer.toString());
          }
          else
          {
            // This is bad because we don't know what the superior objectclass
            // is so we can't base this objectclass on it.
            Message message =
                WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_SUPERIOR_CLASS.
                  get(String.valueOf(oid), String.valueOf(woidBuffer));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }


        // Use the objectclass type of the superior objectclass to override the
        // objectclass type for the new one.  This could potentially cause a
        // problem if the components in the objectclass description are provided
        // out-of-order, but even if that happens then it doesn't really matter
        // since the objectclass type currently isn't used for anything anyway.
        objectClassType = superiorClass.getObjectClassType();
      }
      else if (lowerTokenName.equals("abstract"))
      {
        // This indicates that entries must not include this objectclass unless
        // they also include a non-abstract objectclass that inherits from this
        // class.  We do not need any more parsing for this token.
        objectClassType = ObjectClassType.ABSTRACT;
      }
      else if (lowerTokenName.equals("structural"))
      {
        // This indicates that this is a structural objectclass.  We do not need
        // any more parsing for this token.
        objectClassType = ObjectClassType.STRUCTURAL;
      }
      else if (lowerTokenName.equals("auxiliary"))
      {
        // This indicates that this is an auxiliary objectclass.  We do not need
        // any more parsing for this token.
        objectClassType = ObjectClassType.AUXILIARY;
      }
      else if (lowerTokenName.equals("must"))
      {
        LinkedList<AttributeType> attrs = new LinkedList<AttributeType>();

        // This specifies the set of required attributes for the objectclass.
        // It may be a single name or OID (not in quotes), or it may be an
        // open parenthesis followed by one or more names separated by spaces
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
              if (allowUnknownElements)
              {
                attr = DirectoryServer.getDefaultAttributeType(
                                            woidBuffer.toString());
              }
              else
              {
                // This isn't good because it means that the objectclass
                // requires an attribute type that we don't know anything about.
                Message message =
                    WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_REQUIRED_ATTR.
                      get(oid, woidBuffer.toString());
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                             message);
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
              Message message = ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR.get(
                  valueStr, String.valueOf(c), (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message);
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
            if (allowUnknownElements)
            {
              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }
            else
            {
              // This isn't good because it means that the objectclass requires
              // an attribute type that we don't know anything about.
              Message message =
                  WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_REQUIRED_ATTR.
                    get(oid, woidBuffer.toString());
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message);
            }
          }

          attrs.add(attr);
        }

        requiredAttributes.addAll(attrs);
      }
      else if (lowerTokenName.equals("may"))
      {
        LinkedList<AttributeType> attrs = new LinkedList<AttributeType>();

        // This specifies the set of optional attributes for the objectclass.
        // It may be a single name or OID (not in quotes), or it may be an
        // open parenthesis followed by one or more names separated by spaces
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
              if (allowUnknownElements)
              {
                attr = DirectoryServer.getDefaultAttributeType(
                                            woidBuffer.toString());
              }
              else
              {
                // This isn't good because it means that the objectclass allows
                // an attribute type that we don't know anything about.
                Message message =
                  WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_OPTIONAL_ATTR.
                      get(oid, woidBuffer.toString());
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                             message);
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
              Message message = ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR.get(
                  valueStr, String.valueOf(c), (pos-1));
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                           message);
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
            if (allowUnknownElements)
            {
              attr = DirectoryServer.getDefaultAttributeType(
                                          woidBuffer.toString());
            }
            else
            {
              // This isn't good because it means that the objectclass allows an
              // attribute type that we don't know anything about.
              Message message =
                WARN_ATTR_SYNTAX_OBJECTCLASS_UNKNOWN_OPTIONAL_ATTR.
                    get(oid, woidBuffer.toString());
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           message);
            }
          }

          attrs.add(attr);
        }

        optionalAttributes.addAll(attrs);
      }
      else
      {
        // This must be a non-standard property and it must be followed by
        // either a single value in single quotes or an open parenthesis
        // followed by one or more values in single quotes separated by spaces
        // followed by a close parenthesis.
        List<String> valueList = new LinkedList<String>();
        pos = readExtraParameterValues(valueStr, valueList, pos);
        extraProperties.put(tokenName, valueList);
      }
    }


    if (superiorClass.getOID().equals(oid))
    {
      // This should only happen for the "top" objectclass.
      superiorClass = null;
    }
    else
    {
      // Make sure that the inheritance configuration is acceptable.
      ObjectClassType superiorType = superiorClass.getObjectClassType();
      switch (objectClassType)
      {
        case ABSTRACT:
          // Abstract classes may only inherit from other abstract classes.
          if (superiorType != ObjectClassType.ABSTRACT)
          {
            Message message =
              WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE.
                  get(oid, objectClassType.toString(), superiorType.toString(),
                      superiorClass.getNameOrOID());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
          break;

        case AUXILIARY:
          // Auxiliary classes may only inherit from abstract classes or other
          // auxiliary classes.
          if ((superiorType != ObjectClassType.ABSTRACT) &&
              (superiorType != ObjectClassType.AUXILIARY))
          {
            Message message =
              WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE.
                  get(oid, objectClassType.toString(), superiorType.toString(),
                      superiorClass.getNameOrOID());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
          break;

        case STRUCTURAL:
          // Structural classes may only inherit from abstract classes or other
          // structural classes.
          if ((superiorType != ObjectClassType.ABSTRACT) &&
              (superiorType != ObjectClassType.STRUCTURAL))
          {
            Message message =
              WARN_ATTR_SYNTAX_OBJECTCLASS_INVALID_SUPERIOR_TYPE.
                  get(oid, objectClassType.toString(), superiorType.toString(),
                      superiorClass.getNameOrOID());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }

          // Structural classes must have the "top" objectclass somewhere in the
          // superior chain.
          if (! superiorChainIncludesTop(superiorClass))
          {
            Message message =
              WARN_ATTR_SYNTAX_OBJECTCLASS_STRUCTURAL_SUPERIOR_NOT_TOP.
                  get(oid);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
          break;
      }
    }



    return new ObjectClass(value.stringValue(), primaryName, names, oid,
                           description, superiorClass, requiredAttributes,
                           optionalAttributes, objectClassType, isObsolete,
                           extraProperties);
  }



  /**
   * Reads the next token name from the objectclass definition, skipping over
   * any leading or trailing spaces, and appends it to the provided buffer.
   *
   * @param  valueStr   The string representation of the objectclass definition.
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
   * @param  valueStr     The user-provided representation of the objectclass
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      Message message = WARN_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_QUOTE_AT_POS.get(
          valueStr, startPos, String.valueOf(c));
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping over the
   * quotes and any leading or trailing spaces, and appending the string to the
   * provided buffer.
   *
   * @param  valueStr     The user-provided representation of the objectclass
   *                      definition.
   * @param  lowerStr     The all-lowercase representation of the objectclass
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      Message message = WARN_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_QUOTE_AT_POS.get(
          valueStr, startPos, String.valueOf(c));
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the attribute type/objectclass description or numeric OID from the
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be either numeric (for an OID) or alphabetic (for
    // an objectclass description).
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
            Message message =
              ERR_ATTR_SYNTAX_OBJECTCLASS_DOUBLE_PERIOD_IN_NUMERIC_OID.
                  get(lowerStr, (startPos-1));
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
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
          Message message =
            ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_NUMERIC_OID.
                get(lowerStr, String.valueOf(c), (startPos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
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
      // This must be an objectclass description.  In this case, we will only
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
          Message message =
            ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR_IN_STRING_OID.
                get(lowerStr, String.valueOf(c), (startPos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
    }
    else
    {
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR.get(
                  lowerStr, String.valueOf(c), startPos);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Skip over any trailing spaces after the value.
    while ((startPos < length) && ((c = lowerStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(lowerStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
          Message message =
              ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }


        if (c == ')')
        {
          // This is the end of the list.
          break;
        }
        else if (c == '(')
        {
          // This is an illegal character.
          Message message = ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_CHAR.get(
              valueStr, String.valueOf(c), startPos);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
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
      Message message =
          ERR_ATTR_SYNTAX_OBJECTCLASS_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    return startPos;
  }



  /**
   * Indicates whether the provided objectclass or any of its superiors is equal
   * to the "top" objectclass.
   *
   * @param  superiorClass  The objectclass for which to make the determination.
   *
   * @return  {@code true} if the provided class or any of its superiors is
   *          equal to the "top" objectclass, or {@code false} if not.
   */
  private static boolean superiorChainIncludesTop(ObjectClass superiorClass)
  {
    if (superiorClass == null)
    {
      return false;
    }
    else if (superiorClass.hasName(OC_TOP))
    {
      return true;
    }
    else
    {
      return superiorChainIncludesTop(superiorClass.getSuperiorClass());
    }
  }
}

