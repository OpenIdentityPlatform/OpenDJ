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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import org.opends.server.types.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.messages.AciMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import org.opends.server.protocols.asn1.ASN1OctetString;
import static org.opends.server.util.StaticUtils.isDigit;
import static org.opends.server.util.StaticUtils.isHexDigit;
import static org.opends.server.util.StaticUtils.hexStringToByteArray;
import org.opends.server.util.Validator;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to encapsulate DN pattern matching using wildcards.
 * The following wildcard uses are supported.
 *
 * Value substring:  Any number of wildcards may appear in RDN attribute
 * values where they match zero or more characters, just like substring filters:
 *   uid=b*jensen*
 *
 * Whole-Type:  A single wildcard may also be used to match any RDN attribute
 * type, and the wildcard in this case may be omitted as a shorthand:
 *   *=bjensen
 *   bjensen
 *
 * Whole-RDN.  A single wildcard may be used to match exactly one RDN component
 * (which may be single or multi-valued):
 *   *,dc=example,dc=com
 *
 * Multiple-Whole-RDN:  A double wildcard may be used to match one or more
 * RDN components:
 *   uid=bjensen,**,dc=example,dc=com
 *
 */
public class PatternDN
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * If the pattern did not include any Multiple-Whole-RDN wildcards, then
   * this is the sequence of RDN patterns in the DN pattern.  Otherwise it
   * is null.
   */
  PatternRDN[] equality = null;


  /**
   * If the pattern included any Multiple-Whole-RDN wildcards, then these
   * are the RDN pattern sequences that appear between those wildcards.
   */
  PatternRDN[] subInitial = null;
  List<PatternRDN[]> subAnyElements = null;
  PatternRDN[] subFinal = null;


  /**
   * When there is no initial sequence, this is used to distinguish between
   * the case where we have a suffix pattern (zero or more RDN components
   * allowed before matching elements) and the case where it is not a
   * suffix pattern but the pattern started with a Multiple-Whole-RDN wildcard
   * (one or more RDN components allowed before matching elements).
   */
  boolean isSuffix = false;


  /**
   * Create a DN pattern that does not include any Multiple-Whole-RDN wildcards.
   * @param equality The sequence of RDN patterns making up the DN pattern.
   */
  private PatternDN(PatternRDN[] equality)
  {
    this.equality = equality;
  }


  /**
   * Create a DN pattern that includes Multiple-Whole-RDN wildcards.
   * @param subInitial     The sequence of RDN patterns appearing at the
   *                       start of the DN, or null if there are none.
   * @param subAnyElements The list of sequences of RDN patterns appearing
   *                       in order anywhere in the DN.
   * @param subFinal       The sequence of RDN patterns appearing at the
   *                       end of the DN, or null if there are none.
   */
  private PatternDN(PatternRDN[] subInitial,
                    List<PatternRDN[]> subAnyElements,
                    PatternRDN[] subFinal)
  {
    Validator.ensureNotNull(subAnyElements);
    this.subInitial = subInitial;
    this.subAnyElements = subAnyElements;
    this.subFinal = subFinal;
  }


  /**
   * Determine whether a given DN matches this pattern.
   * @param dn The DN to be matched.
   * @return true if the DN matches the pattern.
   */
  public boolean matchesDN(DN dn)
  {
    if (equality != null)
    {
      // There are no Multiple-Whole-RDN wildcards in the pattern.
      if (equality.length != dn.getNumComponents())
      {
        return false;
      }

      for (int i = 0; i < dn.getNumComponents(); i++)
      {
        if (!equality[i].matchesRDN(dn.getRDN(i)))
        {
          return false;
        }
      }

      return true;
    }
    else
    {
      // There are Multiple-Whole-RDN wildcards in the pattern.
      int valueLength = dn.getNumComponents();

      int pos = 0;
      if (subInitial != null)
      {
        int initialLength = subInitial.length;
        if (initialLength > valueLength)
        {
          return false;
        }

        for (; pos < initialLength; pos++)
        {
          if (!subInitial[pos].matchesRDN(dn.getRDN(pos)))
          {
            return false;
          }
        }
        pos++;
      }
      else
      {
        if (!isSuffix)
        {
          pos++;
        }
      }


      if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
      {
        for (PatternRDN[] element : subAnyElements)
        {
          int anyLength = element.length;

          int end = valueLength - anyLength;
          boolean match = false;
          for (; pos < end; pos++)
          {
            if (element[0].matchesRDN(dn.getRDN(pos)))
            {
              boolean subMatch = true;
              for (int i=1; i < anyLength; i++)
              {
                if (!element[i].matchesRDN(dn.getRDN(pos+i)))
                {
                  subMatch = false;
                  break;
                }
              }

              if (subMatch)
              {
                match = subMatch;
                break;
              }
            }
          }

          if (match)
          {
            pos += anyLength + 1;
          }
          else
          {
            return false;
          }
        }
      }


      if (subFinal != null)
      {
        int finalLength = subFinal.length;

        if ((valueLength - finalLength) < pos)
        {
          return false;
        }

        pos = valueLength - finalLength;
        for (int i=0; i < finalLength; i++,pos++)
        {
          if (!subFinal[i].matchesRDN(dn.getRDN(pos)))
          {
            return false;
          }
        }
      }

      return pos <= valueLength;
    }
  }


  /**
   * Create a new DN pattern matcher to match a suffix.
   * @param pattern The suffix pattern string.
   * @throws org.opends.server.types.DirectoryException If the pattern string
   * is not valid.
   * @return A new DN pattern matcher.
   */
  public static PatternDN decodeSuffix(String pattern)
       throws DirectoryException
  {
    // Parse the user supplied pattern.
    PatternDN patternDN = decode(pattern);

    // Adjust the pattern so that it matches any DN ending with the pattern.
    if (patternDN.equality != null)
    {
      // The pattern contained no Multiple-Whole-RDN wildcards,
      // so we just convert the whole thing into a final fragment.
      patternDN.subInitial = null;
      patternDN.subFinal = patternDN.equality;
      patternDN.subAnyElements = null;
      patternDN.equality = null;
    }
    else if (patternDN.subInitial != null)
    {
      // The pattern had an initial fragment so we need to convert that into
      // the head of the list of any elements.
      patternDN.subAnyElements.add(0, patternDN.subInitial);
      patternDN.subInitial = null;
    }
    patternDN.isSuffix = true;
    return patternDN;
  }


  /**
   * Create a new DN pattern matcher from a pattern string.
   * @param dnString The DN pattern string.
   * @throws org.opends.server.types.DirectoryException If the pattern string
   * is not valid.
   * @return A new DN pattern matcher.
   */
  public static PatternDN decode(String dnString)
         throws DirectoryException
  {
    ArrayList<PatternRDN> rdnComponents = new ArrayList<PatternRDN>();
    ArrayList<Integer> doubleWildPos = new ArrayList<Integer>();

    // A null or empty DN is acceptable.
    if (dnString == null)
    {
      return new PatternDN(new PatternRDN[0]);
    }

    int length = dnString.length();
    if (length == 0)
    {
      return new PatternDN(new PatternRDN[0]);
    }


    // Iterate through the DN string.  The first thing to do is to get
    // rid of any leading spaces.
    int pos = 0;
    char c = dnString.charAt(pos);
    while (c == ' ')
    {
      pos++;
      if (pos == length)
      {
        // This means that the DN was completely comprised of spaces
        // and therefore should be considered the same as a null or
        // empty DN.
        return new PatternDN(new PatternRDN[0]);
      }
      else
      {
        c = dnString.charAt(pos);
      }
    }

    // We know that it's not an empty DN, so we can do the real
    // processing.  Create a loop and iterate through all the RDN
    // components.
    rdnLoop:
    while (true)
    {
      int attributePos = pos;
      StringBuilder attributeName = new StringBuilder();
      pos = parseAttributePattern(dnString, pos, attributeName);
      String name            = attributeName.toString();


      // Make sure that we're not at the end of the DN string because
      // that would be invalid.
      if (pos >= length)
      {
        if (name.equals("*"))
        {
          rdnComponents.add(new PatternRDN(name, null, dnString));
          break;
        }
        else if (name.equals("**"))
        {
          doubleWildPos.add(rdnComponents.size());
          break;
        }
        else
        {
          pos = attributePos - 1;
          name = "*";
          c = '=';
        }
      }
      else
      {
        // Skip over any spaces between the attribute name and its
        // value.
        c = dnString.charAt(pos);
        while (c == ' ')
        {
          pos++;
          if (pos >= length)
          {
            if (name.equals("*"))
            {
              rdnComponents.add(new PatternRDN(name, null, dnString));
              break rdnLoop;
            }
            else if (name.equals("**"))
            {
              doubleWildPos.add(rdnComponents.size());
              break rdnLoop;
            }
            else
            {
              pos = attributePos - 1;
              name = "*";
              c = '=';
            }
          }
          else
          {
            c = dnString.charAt(pos);
          }
        }
      }


      if (c == '=')
      {
        pos++;
      }
      else if ((c == ',' || c == ';'))
      {
        if (name.equals("*"))
        {
          rdnComponents.add(new PatternRDN(name, null, dnString));
          pos++;
          continue;
        }
        else if (name.equals("**"))
        {
          doubleWildPos.add(rdnComponents.size());
          pos++;
          continue;
        }
        else
        {
          pos = attributePos;
          name = "*";
        }
      }
      else
      {
        int    msgID   = MSGID_ATTR_SYNTAX_DN_NO_EQUAL;
        String message = getMessage(msgID, dnString,
                                    attributeName.toString(), c);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
      }

      // Skip over any spaces after the equal sign.
      while ((pos < length) && (dnString.charAt(pos) == ' '))
      {
        pos++;
      }


      // If we are at the end of the DN string, then that must mean
      // that the attribute value was empty.  This will probably never
      // happen in a real-world environment, but technically isn't
      // illegal.  If it does happen, then go ahead and create the
      // RDN component and return the DN.
      if (pos >= length)
      {
        ArrayList<ByteString> arrayList = new ArrayList<ByteString>(1);
        arrayList.add(new ASN1OctetString());
        rdnComponents.add(new PatternRDN(name, arrayList, dnString));
        break;
      }


      // Parse the value for this RDN component.
      ArrayList<ByteString> parsedValue = new ArrayList<ByteString>();
      pos = parseValuePattern(dnString, pos, parsedValue);


      // Create the new RDN with the provided information.
      PatternRDN rdn = new PatternRDN(name, parsedValue, dnString);


      // Skip over any spaces that might be after the attribute value.
      while ((pos < length) && ((c = dnString.charAt(pos)) == ' '))
      {
        pos++;
      }


      // Most likely, we will be at either the end of the RDN
      // component or the end of the DN.  If so, then handle that
      // appropriately.
      if (pos >= length)
      {
        // We're at the end of the DN string and should have a valid
        // DN so return it.
        rdnComponents.add(rdn);
        break;
      }
      else if ((c == ',') || (c == ';'))
      {
        // We're at the end of the RDN component, so add it to the
        // list, skip over the comma/semicolon, and start on the next
        // component.
        rdnComponents.add(rdn);
        pos++;
        continue;
      }
      else if (c != '+')
      {
        // This should not happen.  At any rate, it's an illegal
        // character, so throw an exception.
        int    msgID   = MSGID_ATTR_SYNTAX_DN_INVALID_CHAR;
        String message = getMessage(msgID, dnString, c, pos);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
      }


      // If we have gotten here, then this must be a multi-valued RDN.
      // In that case, parse the remaining attribute/value pairs and
      // add them to the RDN that we've already created.
      while (true)
      {
        // Skip over the plus sign and any spaces that may follow it
        // before the next attribute name.
        pos++;
        while ((pos < length) && (dnString.charAt(pos) == ' '))
        {
          pos++;
        }


        // Parse the attribute name from the DN string.
        attributeName = new StringBuilder();
        pos = parseAttributePattern(dnString, pos, attributeName);


        // Make sure that we're not at the end of the DN string
        // because that would be invalid.
        if (pos >= length)
        {
          int    msgID   = MSGID_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME;
          String message = getMessage(msgID, dnString,
                                      attributeName.toString());
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
        }


        name = attributeName.toString();

        // Skip over any spaces between the attribute name and its
        // value.
        c = dnString.charAt(pos);
        while (c == ' ')
        {
          pos++;
          if (pos >= length)
          {
            // This means that we hit the end of the value before
            // finding a '='.  This is illegal because there is no
            // attribute-value separator.
            int    msgID   = MSGID_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME;
            String message = getMessage(msgID, dnString, name);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message, msgID);
          }
          else
          {
            c = dnString.charAt(pos);
          }
        }


        // The next character must be an equal sign.  If it is not,
        // then that's an error.
        if (c == '=')
        {
          pos++;
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_DN_NO_EQUAL;
          String message = getMessage(msgID, dnString, name, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
        }


        // Skip over any spaces after the equal sign.
        while ((pos < length) && ((c = dnString.charAt(pos)) == ' '))
        {
          pos++;
        }


        // If we are at the end of the DN string, then that must mean
        // that the attribute value was empty.  This will probably
        // never happen in a real-world environment, but technically
        // isn't illegal.  If it does happen, then go ahead and create
        // the RDN component and return the DN.
        if (pos >= length)
        {
          ArrayList<ByteString> arrayList = new ArrayList<ByteString>(1);
          arrayList.add(new ASN1OctetString());
          rdn.addValue(name, arrayList, dnString);
          rdnComponents.add(rdn);
          break;
        }


        // Parse the value for this RDN component.
        parsedValue = new ArrayList<ByteString>();
        pos = parseValuePattern(dnString, pos, parsedValue);


        // Create the new RDN with the provided information.
        rdn.addValue(name, parsedValue, dnString);


        // Skip over any spaces that might be after the attribute
        // value.
        while ((pos < length) && ((c = dnString.charAt(pos)) == ' '))
        {
          pos++;
        }


        // Most likely, we will be at either the end of the RDN
        // component or the end of the DN.  If so, then handle that
        // appropriately.
        if (pos >= length)
        {
          // We're at the end of the DN string and should have a valid
          // DN so return it.
          rdnComponents.add(rdn);
          break;
        }
        else if ((c == ',') || (c == ';'))
        {
          // We're at the end of the RDN component, so add it to the
          // list, skip over the comma/semicolon, and start on the
          // next component.
          rdnComponents.add(rdn);
          pos++;
          break;
        }
        else if (c != '+')
        {
          // This should not happen.  At any rate, it's an illegal
          // character, so throw an exception.
          int    msgID   = MSGID_ATTR_SYNTAX_DN_INVALID_CHAR;
          String message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
        }
      }
    }

    if (doubleWildPos.isEmpty())
    {
      PatternRDN[] patterns = new PatternRDN[rdnComponents.size()];
      patterns = rdnComponents.toArray(patterns);
      return new PatternDN(patterns);
    }
    else
    {
      PatternRDN[] subInitial = null;
      PatternRDN[] subFinal = null;
      List<PatternRDN[]> subAnyElements = new ArrayList<PatternRDN[]>();

      int i = 0;
      int numComponents = rdnComponents.size();

      int to = doubleWildPos.get(i);
      if (to != 0)
      {
        // Initial piece.
        subInitial = new PatternRDN[to];
        subInitial = rdnComponents.subList(0, to).toArray(subInitial);
      }

      int from;
      for (; i < doubleWildPos.size() - 1; i++)
      {
        from = doubleWildPos.get(i);
        to = doubleWildPos.get(i+1);
        PatternRDN[] subAny = new PatternRDN[to-from];
        subAny = rdnComponents.subList(from, to).toArray(subAny);
        subAnyElements.add(subAny);
      }

      if (i < doubleWildPos.size())
      {
        from = doubleWildPos.get(i);
        if (from != numComponents)
        {
          // Final piece.
          subFinal = new PatternRDN[numComponents-from];
          subFinal = rdnComponents.subList(from, numComponents).
               toArray(subFinal);
        }
      }

      return new PatternDN(subInitial, subAnyElements, subFinal);
    }
  }


  /**
   * Parses an attribute name pattern from the provided DN pattern string
   * starting at the specified location.
   *
   * @param  dnString         The DN pattern string to be parsed.
   * @param  pos              The position at which to start parsing
   *                          the attribute name pattern.
   * @param  attributeName    The buffer to which to append the parsed
   *                          attribute name pattern.
   *
   * @return  The position of the first character that is not part of
   *          the attribute name pattern.
   *
   * @throws  DirectoryException  If it was not possible to parse a
   *                              valid attribute name pattern from the
   *                              provided DN pattern string.
   */
  static int parseAttributePattern(String dnString, int pos,
                                   StringBuilder attributeName)
          throws DirectoryException
  {
    int length = dnString.length();


    // Skip over any leading spaces.
    if (pos < length)
    {
      while (dnString.charAt(pos) == ' ')
      {
        pos++;
        if (pos == length)
        {
          // This means that the remainder of the DN was completely
          // comprised of spaces.  If we have gotten here, then we
          // know that there is at least one RDN component, and
          // therefore the last non-space character of the DN must
          // have been a comma. This is not acceptable.
          int    msgID   = MSGID_ATTR_SYNTAX_DN_END_WITH_COMMA;
          String message = getMessage(msgID, dnString);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
        }
      }
    }

    // Next, we should find the attribute name for this RDN component.
    boolean       checkForOID   = false;
    boolean       endOfName     = false;
    while (pos < length)
    {
      // To make the switch more efficient, we'll include all ASCII
      // characters in the range of allowed values and then reject the
      // ones that aren't allowed.
      char c = dnString.charAt(pos);
      switch (c)
      {
        case ' ':
          // This should denote the end of the attribute name.
          endOfName = true;
          break;


        case '!':
        case '"':
        case '#':
        case '$':
        case '%':
        case '&':
        case '\'':
        case '(':
        case ')':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          int    msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          String message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case '*':
          // Wildcard character.
          attributeName.append(c);
          break;

        case '+':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case ',':
          // This should denote the end of the attribute name.
          endOfName = true;
          break;

        case '-':
          // This will be allowed as long as it isn't the first
          // character in the attribute name.
          if (attributeName.length() > 0)
          {
            attributeName.append(c);
          }
          else
          {
            msgID = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_INITIAL_DASH;
            message = getMessage(msgID, dnString, c);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message, msgID);
          }
          break;


        case '.':
          // The period could be allowed if the attribute name is
          // actually expressed as an OID.  We'll accept it for now,
          // but make sure to check it later.
          attributeName.append(c);
          checkForOID = true;
          break;


        case '/':
          // This is not allowed in an attribute name or any character
          // immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          // Digits are always allowed if they are not the first
          // character. However, they may be allowed if they are the
          // first character if the valid is an OID or if the
          // attribute name exceptions option is enabled.  Therefore,
          // we'll accept it now and check it later.
          attributeName.append(c);
          break;


        case ':':
          // Not allowed in an attribute name or any
          // character immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case ';': // NOTE:  attribute options are not allowed in a DN.
          // This should denote the end of the attribute name.
          endOfName = true;
          break;

        case '<':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case '=':
          // This should denote the end of the attribute name.
          endOfName = true;
          break;


        case '>':
        case '?':
        case '@':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case 'A':
        case 'B':
        case 'C':
        case 'D':
        case 'E':
        case 'F':
        case 'G':
        case 'H':
        case 'I':
        case 'J':
        case 'K':
        case 'L':
        case 'M':
        case 'N':
        case 'O':
        case 'P':
        case 'Q':
        case 'R':
        case 'S':
        case 'T':
        case 'U':
        case 'V':
        case 'W':
        case 'X':
        case 'Y':
        case 'Z':
          // These will always be allowed.
          attributeName.append(c);
          break;


        case '[':
        case '\\':
        case ']':
        case '^':
          // None of these are allowed in an attribute name or any
          // character immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case '_':
          attributeName.append(c);
          break;


        case '`':
          // This is not allowed in an attribute name or any character
          // immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);


        case 'a':
        case 'b':
        case 'c':
        case 'd':
        case 'e':
        case 'f':
        case 'g':
        case 'h':
        case 'i':
        case 'j':
        case 'k':
        case 'l':
        case 'm':
        case 'n':
        case 'o':
        case 'p':
        case 'q':
        case 'r':
        case 's':
        case 't':
        case 'u':
        case 'v':
        case 'w':
        case 'x':
        case 'y':
        case 'z':
          // These will always be allowed.
          attributeName.append(c);
          break;


        default:
          // This is not allowed in an attribute name or any character
          // immediately following it.
          msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR;
          message = getMessage(msgID, dnString, c, pos);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
      }


      if (endOfName)
      {
        break;
      }

      pos++;
    }


    // We should now have the full attribute name.  However, we may
    // still need to perform some validation, particularly if the
    // name contains a period or starts with a digit.  It must also
    // have at least one character.
    if (attributeName.length() == 0)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_NO_NAME;
      String message = getMessage(msgID, dnString);
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
    }
    else if (checkForOID)
    {
      boolean validOID = true;

      int namePos = 0;
      int nameLength = attributeName.length();
      char ch = attributeName.charAt(0);
      if ((ch == 'o') || (ch == 'O'))
      {
        if (nameLength <= 4)
        {
          validOID = false;
        }
        else
        {
          if ((((ch = attributeName.charAt(1)) == 'i') ||
               (ch == 'I')) &&
              (((ch = attributeName.charAt(2)) == 'd') ||
               (ch == 'D')) &&
              (attributeName.charAt(3) == '.'))
          {
            attributeName.delete(0, 4);
            nameLength -= 4;
          }
          else
          {
            validOID = false;
          }
        }
      }

      while (validOID && (namePos < nameLength))
      {
        ch = attributeName.charAt(namePos++);
        if (isDigit(ch))
        {
          while (validOID && (namePos < nameLength) &&
                 isDigit(attributeName.charAt(namePos)))
          {
            namePos++;
          }

          if ((namePos < nameLength) &&
              (attributeName.charAt(namePos) != '.'))
          {
            validOID = false;
          }
        }
        else if (ch == '.')
        {
          if ((namePos == 1) ||
              (attributeName.charAt(namePos-2) == '.'))
          {
            validOID = false;
          }
        }
        else
        {
          validOID = false;
        }
      }


      if (validOID && (attributeName.charAt(nameLength-1) == '.'))
      {
        validOID = false;
      }


      if (! validOID)
      {
        int    msgID   = MSGID_ATTR_SYNTAX_DN_ATTR_ILLEGAL_PERIOD;
        String message = getMessage(msgID, dnString,
                                    attributeName.toString());
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
      }
    }


    return pos;
  }


  /**
   * Parses the attribute value pattern from the provided DN pattern
   * string starting at the specified location.  The value is split up
   * according to the wildcard locations, and the fragments are inserted
   * into the provided list.
   *
   * @param  dnString        The DN pattern string to be parsed.
   * @param  pos             The position of the first character in
   *                         the attribute value pattern to parse.
   * @param  attributeValues The list whose elements should be set to
   *                         the parsed attribute value fragments when
   *                         this method completes successfully.
   *
   * @return  The position of the first character that is not part of
   *          the attribute value.
   *
   * @throws  DirectoryException  If it was not possible to parse a
   *                              valid attribute value pattern from the
   *                              provided DN string.
   */
  static private int parseValuePattern(String dnString, int pos,
                                       ArrayList<ByteString> attributeValues)
          throws DirectoryException
  {
    // All leading spaces have already been stripped so we can start
    // reading the value.  However, it may be empty so check for that.
    int length = dnString.length();
    if (pos >= length)
    {
      return pos;
    }


    // Look at the first character.  If it is an octothorpe (#), then
    // that means that the value should be a hex string.
    char c = dnString.charAt(pos++);
    if (c == '#')
    {
      // The first two characters must be hex characters.
      StringBuilder hexString = new StringBuilder();
      if ((pos+2) > length)
      {
        int    msgID   = MSGID_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT;
        String message = getMessage(msgID, dnString);
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
      }

      for (int i=0; i < 2; i++)
      {
        c = dnString.charAt(pos++);
        if (isHexDigit(c))
        {
          hexString.append(c);
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT;
          String message = getMessage(msgID, dnString, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
        }
      }


      // The rest of the value must be a multiple of two hex
      // characters.  The end of the value may be designated by the
      // end of the DN, a comma or semicolon, or a space.
      while (pos < length)
      {
        c = dnString.charAt(pos++);
        if (isHexDigit(c))
        {
          hexString.append(c);

          if (pos < length)
          {
            c = dnString.charAt(pos++);
            if (isHexDigit(c))
            {
              hexString.append(c);
            }
            else
            {
              int    msgID   = MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT;
              String message = getMessage(msgID, dnString, c);
              throw new DirectoryException(
                             ResultCode.INVALID_DN_SYNTAX, message,
                             msgID);
            }
          }
          else
          {
            int    msgID   = MSGID_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT;
            String message = getMessage(msgID, dnString);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message, msgID);
          }
        }
        else if ((c == ' ') || (c == ',') || (c == ';'))
        {
          // This denotes the end of the value.
          pos--;
          break;
        }
        else
        {
          int    msgID   = MSGID_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT;
          String message = getMessage(msgID, dnString, c);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
        }
      }


      // At this point, we should have a valid hex string.  Convert it
      // to a byte array and set that as the value of the provided
      // octet string.
      try
      {
        byte[] bytes = hexStringToByteArray(hexString.toString());
        attributeValues.add(new ASN1OctetString(bytes));
        return pos;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE;
        String message = getMessage(msgID, dnString,
                                    String.valueOf(e));
        throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                     message, msgID);
      }
    }


    // If the first character is a quotation mark, then the value
    // should continue until the corresponding closing quotation mark.
    else if (c == '"')
    {
      // Keep reading until we find an unescaped closing quotation
      // mark.
      boolean escaped = false;
      StringBuilder valueString = new StringBuilder();
      while (true)
      {
        if (pos >= length)
        {
          // We hit the end of the DN before the closing quote.
          // That's an error.
          int    msgID   = MSGID_ATTR_SYNTAX_DN_UNMATCHED_QUOTE;
          String message = getMessage(msgID, dnString);
          throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                       message, msgID);
        }

        c = dnString.charAt(pos++);
        if (escaped)
        {
          // The previous character was an escape, so we'll take this
          // one no matter what.
          valueString.append(c);
          escaped = false;
        }
        else if (c == '\\')
        {
          // The next character is escaped.  Set a flag to denote
          // this, but don't include the backslash.
          escaped = true;
        }
        else if (c == '"')
        {
          // This is the end of the value.
          break;
        }
        else
        {
          // This is just a regular character that should be in the
          // value.
          valueString.append(c);
        }
      }

      attributeValues.add(new ASN1OctetString(valueString.toString()));
      return pos;
    }


    // Otherwise, use general parsing to find the end of the value.
    else
    {
      boolean escaped;
      StringBuilder valueString = new StringBuilder();
      StringBuilder hexChars    = new StringBuilder();

      if (c == '\\')
      {
        escaped = true;
      }
      else if (c == '*')
      {
        escaped = false;
        attributeValues.add(new ASN1OctetString(valueString.toString()));
      }
      else
      {
        escaped = false;
        valueString.append(c);
      }


      // Keep reading until we find an unescaped comma or plus sign or
      // the end of the DN.
      while (true)
      {
        if (pos >= length)
        {
          // This is the end of the DN and therefore the end of the
          // value.  If there are any hex characters, then we need to
          // deal with them accordingly.
          appendHexChars(dnString, valueString, hexChars);
          break;
        }

        c = dnString.charAt(pos++);
        if (escaped)
        {
          // The previous character was an escape, so we'll take this
          // one.  However, this could be a hex digit, and if that's
          // the case then the escape would actually be in front of
          // two hex digits that should be treated as a special
          // character.
          if (isHexDigit(c))
          {
            // It is a hexadecimal digit, so the next digit must be
            // one too.  However, this could be just one in a series
            // of escaped hex pairs that is used in a string
            // containing one or more multi-byte UTF-8 characters so
            // we can't just treat this byte in isolation.  Collect
            // all the bytes together and make sure to take care of
            // these hex bytes before appending anything else to the
            // value.
            if (pos >= length)
            {
              int    msgID   =
                   MSGID_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID;
              String message = getMessage(msgID, dnString);
              throw new DirectoryException(
                             ResultCode.INVALID_DN_SYNTAX, message,
                             msgID);
            }
            else
            {
              char c2 = dnString.charAt(pos++);
              if (isHexDigit(c2))
              {
                hexChars.append(c);
                hexChars.append(c2);
              }
              else
              {
                int    msgID   =
                     MSGID_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID;
                String message = getMessage(msgID, dnString);
                throw new DirectoryException(
                               ResultCode.INVALID_DN_SYNTAX, message,
                               msgID);
              }
            }
          }
          else
          {
            appendHexChars(dnString, valueString, hexChars);
            valueString.append(c);
          }

          escaped = false;
        }
        else if (c == '\\')
        {
          escaped = true;
        }
        else if ((c == ',') || (c == ';'))
        {
          appendHexChars(dnString, valueString, hexChars);
          pos--;
          break;
        }
        else if (c == '+')
        {
          appendHexChars(dnString, valueString, hexChars);
          pos--;
          break;
        }
        else if (c == '*')
        {
          appendHexChars(dnString, valueString, hexChars);
          if (valueString.length() == 0)
          {
            int    msgID   = MSGID_PATTERN_DN_CONSECUTIVE_WILDCARDS_IN_VALUE;
            String message = getMessage(msgID, dnString);
            throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                         message, msgID);
          }
          attributeValues.add(new ASN1OctetString(valueString.toString()));
          valueString = new StringBuilder();
          hexChars = new StringBuilder();
        }
        else
        {
          appendHexChars(dnString, valueString, hexChars);
          valueString.append(c);
        }
      }


      // Strip off any unescaped spaces that may be at the end of the
      // value.
      if (pos > 2 && dnString.charAt(pos-1) == ' ' &&
           dnString.charAt(pos-2) != '\\')
      {
        int lastPos = valueString.length() - 1;
        while (lastPos > 0)
        {
          if (valueString.charAt(lastPos) == ' ')
          {
            valueString.delete(lastPos, lastPos+1);
            lastPos--;
          }
          else
          {
            break;
          }
        }
      }


      attributeValues.add(new ASN1OctetString(valueString.toString()));
      return pos;
    }
  }


  /**
   * Decodes a hexadecimal string from the provided
   * <CODE>hexChars</CODE> buffer, converts it to a byte array, and
   * then converts that to a UTF-8 string.  The resulting UTF-8 string
   * will be appended to the provided <CODE>valueString</CODE> buffer,
   * and the <CODE>hexChars</CODE> buffer will be cleared.
   *
   * @param  dnString     The DN string that is being decoded.
   * @param  valueString  The buffer containing the value to which the
   *                      decoded string should be appended.
   * @param  hexChars     The buffer containing the hexadecimal
   *                      characters to decode to a UTF-8 string.
   *
   * @throws  DirectoryException  If any problem occurs during the
   *                              decoding process.
   */
  private static void appendHexChars(String dnString,
                                     StringBuilder valueString,
                                     StringBuilder hexChars)
          throws DirectoryException
  {
    try
    {
      byte[] hexBytes = hexStringToByteArray(hexChars.toString());
      valueString.append(new String(hexBytes, "UTF-8"));
      hexChars.delete(0, hexChars.length());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE;
      String message = getMessage(msgID, dnString, String.valueOf(e));
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX,
                                   message, msgID);
    }
  }
}
