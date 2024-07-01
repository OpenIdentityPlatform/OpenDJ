/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.schema;






/**
 * This class defines utility methods that can be used to determine whether a
 * character string is printable as defined in X.520 and referenced in RFC 2252.
 * Printable characters consist of the set of uppercase and lowercase alphabetic
 * characters, numeric digits, quotation mark, open and close parentheses, plus,
 * minus, comma, period, slash, colon, question mark, and space.
 */
public class PrintableString
{



  /**
   * Indicates whether the provided character is a valid printable character.
   *
   * @param  c  The character for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided character is a printable
   *          character, or <CODE>false</CODE> if not.
   */
  public static boolean isPrintableCharacter(char c)
  {
    switch (c)
    {
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
      case '\'':
      case '(':
      case ')':
      case '+':
      case ',':
      case '-':
      case '.':
      case '=':
      case '/':
      case ':':
      case '?':
      case ' ':
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether the provided string is a valid printable string.
   *
   * @param  s  The string for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided string is a printable string, or
   *          <CODE>false</CODE> if not.
   */
  public static boolean isPrintableString(String s)
  {
    if (s == null)
    {
      return false;
    }

    int length = s.length();
    for (int i=0; i < length; i++)
    {
      if (! isPrintableCharacter(s.charAt(i)))
      {
        return false;
      }
    }

    return true;
  }
}

