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
package org.opends.server.tools.makeldif;



import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;

import org.opends.server.types.InitializationException;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a tag that may be used to generate random values.  It has
 * a number of subtypes based on the type of information that should be
 * generated, including:
 * <UL>
 *   <LI>alpha:length</LI>
 *   <LI>alpha:minlength:maxlength</LI>
 *   <LI>numeric:length</LI>
 *   <LI>numeric:minvalue:maxvalue</LI>
 *   <LI>numeric:minvalue:maxvalue:format</LI>
 *   <LI>alphanumeric:length</LI>
 *   <LI>alphanumeric:minlength:maxlength</LI>
 *   <LI>chars:characters:length</LI>
 *   <LI>chars:characters:minlength:maxlength</LI>
 *   <LI>hex:length</LI>
 *   <LI>hex:minlength:maxlength</LI>
 *   <LI>base64:length</LI>
 *   <LI>base64:minlength:maxlength</LI>
 *   <LI>month</LI>
 *   <LI>month:maxlength</LI>
 *   <LI>telephone</LI>
 * </UL>
 */
public class RandomTag
       extends Tag
{
  /**
   * The value that indicates that the value is to be generated from a fixed
   * number of characters from a given character set.
   */
  public static final int RANDOM_TYPE_CHARS_FIXED = 1;



  /**
   * The value that indicates that the value is to be generated from a variable
   * number of characters from a given character set.
   */
  public static final int RANDOM_TYPE_CHARS_VARIABLE = 2;



  /**
   * The value that indicates that the value should be a random number.
   */
  public static final int RANDOM_TYPE_NUMERIC = 3;



  /**
   * The value that indicates that the value should be a random month.
   */
  public static final int RANDOM_TYPE_MONTH = 4;



  /**
   * The value that indicates that the value should be a telephone number.
   */
  public static final int RANDOM_TYPE_TELEPHONE = 5;



  /**
   * The character set that will be used for alphabetic characters.
   */
  public static final char[] ALPHA_CHARS =
       "abcdefghijklmnopqrstuvwxyz".toCharArray();



  /**
   * The character set that will be used for numeric characters.
   */
  public static final char[] NUMERIC_CHARS = "01234567890".toCharArray();



  /**
   * The character set that will be used for alphanumeric characters.
   */
  public static final char[] ALPHANUMERIC_CHARS =
       "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();



  /**
   * The character set that will be used for hexadecimal characters.
   */
  public static final char[] HEX_CHARS = "01234567890abcdef".toCharArray();



  /**
   * The character set that will be used for base64 characters.
   */
  public static final char[] BASE64_CHARS =
       ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" +
        "01234567890+/").toCharArray();



  /**
   * The set of month names that will be used.
   */
  public static final String[] MONTHS =
  {
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
  };



  // The character set that should be used to generate the values.
  private char[] characterSet;

  // The decimal format used to format numeric values.
  private DecimalFormat decimalFormat;

  // The number of characters between the minimum and maximum length
  // (inclusive).
  private int lengthRange;

  // The maximum number of characters to include in the value.
  private int maxLength;

  // The minimum number of characters to include in the value.
  private int minLength;

  // The type of random value that should be generated.
  private int randomType;

  // The maximum numeric value that should be generated.
  private long maxValue;

  // The minimum numeric value that should be generated.
  private long minValue;

  // The number of values between the minimum and maximum value (inclusive).
  private long valueRange;

  // The random number generator for this tag.
  private Random random;



  /**
   * Creates a new instance of this random tag.
   */
  public RandomTag()
  {
    characterSet  = null;
    decimalFormat = null;
    lengthRange   = 1;
    maxLength     = 0;
    minLength     = 0;
    randomType    = 0;
    maxValue      = 0L;
    minValue      = 0L;
    valueRange    = 1L;
  }



  /**
   * Retrieves the name for this tag.
   *
   * @return  The name for this tag.
   */
  public String getName()
  {
    return "Random";
  }



  /**
   * Indicates whether this tag is allowed for use in the extra lines for
   * branches.
   *
   * @return  <CODE>true</CODE> if this tag may be used in branch definitions,
   *          or <CODE>false</CODE> if not.
   */
  public boolean allowedInBranch()
  {
    return true;
  }



  /**
   * Performs any initialization for this tag that may be needed while parsing
   * a branch definition.
   *
   * @param  templateFile  The template file in which this tag is used.
   * @param  branch        The branch in which this tag is used.
   * @param  arguments     The set of arguments provided for this tag.
   * @param  lineNumber    The line number on which this tag appears in the
   *                       template file.
   * @param  warnings      A list into which any appropriate warning messages
   *                       may be placed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this tag.
   */
  public void initializeForBranch(TemplateFile templateFile, Branch branch,
                                  String[] arguments, int lineNumber,
                                  List<String> warnings)
         throws InitializationException
  {
    initializeInternal(templateFile, arguments, lineNumber, warnings);
  }



  /**
   * Performs any initialization for this tag that may be needed while parsing
   * a template definition.
   *
   * @param  templateFile  The template file in which this tag is used.
   * @param  template      The template in which this tag is used.
   * @param  arguments     The set of arguments provided for this tag.
   * @param  lineNumber    The line number on which this tag appears in the
   *                       template file.
   * @param  warnings      A list into which any appropriate warning messages
   *                       may be placed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this tag.
   */
  public void initializeForTemplate(TemplateFile templateFile,
                                    Template template, String[] arguments,
                                    int lineNumber, List<String> warnings)
         throws InitializationException
  {
    initializeInternal(templateFile, arguments, lineNumber, warnings);
  }



  /**
   * Performs any initialization for this tag that may be needed while parsing
   * either a branch or template definition.
   *
   * @param  templateFile  The template file in which this tag is used.
   * @param  arguments     The set of arguments provided for this tag.
   * @param  lineNumber    The line number on which this tag appears in the
   *                       template file.
   * @param  warnings      A list into which any appropriate warning messages
   *                       may be placed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   this tag.
   */
  private void initializeInternal(TemplateFile templateFile, String[] arguments,
                                  int lineNumber, List<String> warnings)
          throws InitializationException
  {
    random = templateFile.getRandom();

    // There must be at least one argument, to specify the type of random value
    // to generate.
    if ((arguments == null) || (arguments.length == 0))
    {
      int    msgID   = MSGID_MAKELDIF_TAG_NO_RANDOM_TYPE_ARGUMENT;
      String message = getMessage(msgID, lineNumber);
      throw new InitializationException(msgID, message);
    }

    int numArgs = arguments.length;
    String randomTypeString = toLowerCase(arguments[0]);

    if (randomTypeString.equals("alpha"))
    {
      characterSet = ALPHA_CHARS;
      decodeLength(arguments, 1, lineNumber, warnings);
    }
    else if (randomTypeString.equals("numeric"))
    {
      if (numArgs == 2)
      {
        randomType   = RANDOM_TYPE_CHARS_FIXED;
        characterSet = NUMERIC_CHARS;

        try
        {
          minLength = Integer.parseInt(arguments[1]);

          if (minLength < 0)
          {
            int    msgID   = MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND;
            String message = getMessage(msgID, minLength, 0, getName(),
                                        lineNumber);
            throw new InitializationException(msgID, message);
          }
          else if (minLength == 0)
          {
            int    msgID   = MSGID_MAKELDIF_TAG_WARNING_EMPTY_VALUE;
            String message = getMessage(msgID, lineNumber);
            warnings.add(message);
          }
        }
        catch (NumberFormatException nfe)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
          String message = getMessage(msgID, arguments[1], getName(),
                                      lineNumber);
          throw new InitializationException(msgID, message, nfe);
        }
      }
      else if ((numArgs == 3) || (numArgs == 4))
      {
        randomType = RANDOM_TYPE_NUMERIC;

        if (numArgs == 4)
        {
          try
          {
            decimalFormat = new DecimalFormat(arguments[3]);
          }
          catch (Exception e)
          {
            int    msgID   = MSGID_MAKELDIF_TAG_INVALID_FORMAT_STRING;
            String message = getMessage(msgID, arguments[3], getName(),
                                        lineNumber);
            throw new InitializationException(msgID, message, e);
          }
        }
        else
        {
          decimalFormat = null;
        }

        try
        {
          minValue = Long.parseLong(arguments[1]);
        }
        catch (NumberFormatException nfe)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
          String message = getMessage(msgID, arguments[1], getName(),
                                      lineNumber);
          throw new InitializationException(msgID, message, nfe);
        }

        try
        {
          maxValue = Long.parseLong(arguments[2]);
          if (maxValue < minValue)
          {
            int    msgID   = MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND;
            String message = getMessage(msgID, maxValue, minValue, getName(),
                                        lineNumber);
            throw new InitializationException(msgID, message);
          }

          valueRange = maxValue - minValue + 1;
        }
        catch (NumberFormatException nfe)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
          String message = getMessage(msgID, arguments[2], getName(),
                                      lineNumber);
          throw new InitializationException(msgID, message, nfe);
        }
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
        String message = getMessage(msgID, getName(), lineNumber, 2, 4,
                                    numArgs);
        throw new InitializationException(msgID, message);
      }
    }
    else if (randomTypeString.equals("alphanumeric"))
    {
      characterSet = ALPHANUMERIC_CHARS;
      decodeLength(arguments, 1, lineNumber, warnings);
    }
    else if (randomTypeString.equals("chars"))
    {
      if ((numArgs < 3) || (numArgs > 4))
      {
        int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
        String message = getMessage(msgID, getName(), lineNumber, 3, 4,
                                    numArgs);
        throw new InitializationException(msgID, message);
      }

      characterSet = arguments[1].toCharArray();
      decodeLength(arguments, 2, lineNumber, warnings);
    }
    else if (randomTypeString.equals("hex"))
    {
      characterSet = HEX_CHARS;
      decodeLength(arguments, 1, lineNumber, warnings);
    }
    else if (randomTypeString.equals("base64"))
    {
      characterSet = BASE64_CHARS;
      decodeLength(arguments, 1, lineNumber, warnings);
    }
    else if (randomTypeString.equals("month"))
    {
      randomType = RANDOM_TYPE_MONTH;

      if (numArgs == 1)
      {
        maxLength = 0;
      }
      else if (numArgs == 2)
      {
        try
        {
          maxLength = Integer.parseInt(arguments[1]);
          if (maxLength <= 0)
          {
            int    msgID   = MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND;
            String message = getMessage(msgID, maxLength, 1, getName(),
                                        lineNumber);
            throw new InitializationException(msgID, message);
          }
        }
        catch (NumberFormatException nfe)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
          String message = getMessage(msgID, arguments[1], getName(),
                                      lineNumber);
          throw new InitializationException(msgID, message, nfe);
        }
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
        String message = getMessage(msgID, getName(), lineNumber, 1, 2,
                                    numArgs);
        throw new InitializationException(msgID, message);
      }
    }
    else if (randomTypeString.equals("telephone"))
    {
      randomType    = RANDOM_TYPE_TELEPHONE;
    }
    else
    {
      int    msgID   = MSGID_MAKELDIF_TAG_UNKNOWN_RANDOM_TYPE;
      String message = getMessage(msgID, lineNumber, randomTypeString);
      throw new InitializationException(msgID, message);
    }
  }



  /**
   * Decodes the information in the provided argument list as either a single
   * integer specifying the number of characters, or two integers specifying the
   * minimum and maximum number of characters.
   *
   * @param  arguments   The set of arguments to be processed.
   * @param  startPos    The position at which the first legth value should
   *                     appear in the argument list.
   * @param  lineNumber  The line number on which the tag appears in the
   *                     template file.
   * @param  warnings    A list into which any appropriate warning messages may
   *                     be placed.
   */
  private void decodeLength(String[] arguments, int startPos, int lineNumber,
                            List<String> warnings)
          throws InitializationException
  {
    int numArgs = arguments.length - startPos + 1;

    if (numArgs == 2)
    {
      // There is a fixed number of characters in the value.
      randomType = RANDOM_TYPE_CHARS_FIXED;

      try
      {
        minLength = Integer.parseInt(arguments[startPos]);

        if (minLength < 0)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND;
          String message = getMessage(msgID, minLength, 0, getName(),
                                      lineNumber);
          throw new InitializationException(msgID, message);
        }
        else if (minLength == 0)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_WARNING_EMPTY_VALUE;
          String message = getMessage(msgID, lineNumber);
          warnings.add(message);
        }
      }
      catch (NumberFormatException nfe)
      {
        int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
        String message = getMessage(msgID, arguments[startPos], getName(),
                                    lineNumber);
        throw new InitializationException(msgID, message, nfe);
      }
    }
    else if (numArgs == 3)
    {
      // There are minimum and maximum lengths.
      randomType = RANDOM_TYPE_CHARS_VARIABLE;

      try
      {
        minLength = Integer.parseInt(arguments[startPos]);

        if (minLength < 0)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND;
          String message = getMessage(msgID, minLength, 0, getName(),
                                      lineNumber);
          throw new InitializationException(msgID, message);
        }
      }
      catch (NumberFormatException nfe)
      {
        int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
        String message = getMessage(msgID, arguments[startPos], getName(),
                                    lineNumber);
        throw new InitializationException(msgID, message, nfe);
      }

      try
      {
        maxLength   = Integer.parseInt(arguments[startPos+1]);
        lengthRange = maxLength - minLength + 1;

        if (maxLength < minLength)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_INTEGER_BELOW_LOWER_BOUND;
          String message = getMessage(msgID, maxLength, minLength, getName(),
                                      lineNumber);
          throw new InitializationException(msgID, message);
        }
        else if (maxLength == 0)
        {
          int    msgID   = MSGID_MAKELDIF_TAG_WARNING_EMPTY_VALUE;
          String message = getMessage(msgID, getName(), lineNumber);
          warnings.add(message);
        }
      }
      catch (NumberFormatException nfe)
      {
        int    msgID   = MSGID_MAKELDIF_TAG_CANNOT_PARSE_AS_INTEGER;
        String message = getMessage(msgID, arguments[startPos+1], getName(),
                                    lineNumber);
        throw new InitializationException(msgID, message, nfe);
      }
    }
    else
    {
      int    msgID   = MSGID_MAKELDIF_TAG_INVALID_ARGUMENT_RANGE_COUNT;
      String message = getMessage(msgID, getName(), lineNumber, startPos+1,
                                  startPos+2, numArgs);
      throw new InitializationException(msgID, message);
    }
  }



  /**
   * Generates the content for this tag by appending it to the provided tag.
   *
   * @param  templateEntry  The entry for which this tag is being generated.
   * @param  templateValue  The template value to which the generated content
   *                        should be appended.
   *
   * @return  The result of generating content for this tag.
   */
  public TagResult generateValue(TemplateEntry templateEntry,
                                 TemplateValue templateValue)
  {
    StringBuilder buffer = templateValue.getValue();

    switch (randomType)
    {
      case RANDOM_TYPE_CHARS_FIXED:
        for (int i=0; i < minLength; i++)
        {
          buffer.append(characterSet[random.nextInt(characterSet.length)]);
        }
        break;

      case RANDOM_TYPE_CHARS_VARIABLE:
        int numChars = random.nextInt(lengthRange) + minLength;
        for (int i=0; i < numChars; i++)
        {
          buffer.append(characterSet[random.nextInt(characterSet.length)]);
        }
        break;

      case RANDOM_TYPE_NUMERIC:
        long randomValue =
          ((random.nextLong() & 0x7FFFFFFFFFFFFFFFL) % valueRange) + minValue;
        if (decimalFormat == null)
        {
          buffer.append(randomValue);
        }
        else
        {
          buffer.append(decimalFormat.format(randomValue));
        }
        break;

      case RANDOM_TYPE_MONTH:
        String month = MONTHS[random.nextInt(MONTHS.length)];
        if ((maxLength == 0) || (month.length() <= maxLength))
        {
          buffer.append(month);
        }
        else
        {
          buffer.append(month.substring(0, maxLength));
        }
        break;

      case RANDOM_TYPE_TELEPHONE:
        buffer.append("+1 ");
        for (int i=0; i < 3; i++)
        {
          buffer.append(NUMERIC_CHARS[random.nextInt(NUMERIC_CHARS.length)]);
        }
        buffer.append(' ');
        for (int i=0; i < 3; i++)
        {
          buffer.append(NUMERIC_CHARS[random.nextInt(NUMERIC_CHARS.length)]);
        }
        buffer.append(' ');
        for (int i=0; i < 4; i++)
        {
          buffer.append(NUMERIC_CHARS[random.nextInt(NUMERIC_CHARS.length)]);
        }
        break;
    }

    return TagResult.SUCCESS_RESULT;
  }
}

