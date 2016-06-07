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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.util;

import static org.opends.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.util.Reject;
import org.opends.messages.ToolMessages;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Entry;
import org.opends.server.types.IdentifiedException;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;

/**
 * This class defines a number of static utility methods that may be used
 * throughout the server.  Note that because of the frequency with which these
 * methods are expected to be used, very little debug logging will be performed
 * to prevent the log from filling up with unimportant calls and to reduce the
 * impact that debugging may have on performance.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class StaticUtils
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The number of bytes of a Java int. A Java int is 32 bits, i.e. 4 bytes. */
  public static final int INT_SIZE = 4;
  /** The number of bytes of a Java long. A Java int is 64 bits, i.e. 8 bytes. */
  public static final int LONG_SIZE = 8;

  /**
   * Number of bytes in a Kibibyte.
   * <p>
   * Example usage:
   * <pre>
   * int _10KB = 10 * KB;
   * </pre>
   */
  public static final int KB = 1024;
  /**
   * Number of bytes in a Mebibyte.
   * <p>
   * Example usage:
   * <pre>
   * int _10MB = 10 * MB;
   * </pre>
   */
  public static final int MB = KB * KB;

  /** Private constructor to prevent instantiation. */
  private StaticUtils() {
    // No implementation required.
  }

  /**
   * Construct a byte array containing the UTF-8 encoding of the
   * provided string. This is significantly faster
   * than calling {@link String#getBytes(String)} for ASCII strings.
   *
   * @param s
   *          The string to convert to a UTF-8 byte array.
   * @return Returns a byte array containing the UTF-8 encoding of the
   *         provided string.
   */
  public static byte[] getBytes(String s)
  {
    return com.forgerock.opendj.util.StaticUtils.getBytes(s);
  }


  /**
   * Returns the provided byte array decoded as a UTF-8 string without throwing
   * an UnsupportedEncodingException. This method is equivalent to:
   *
   * <pre>
   * try
   * {
   *   return new String(bytes, &quot;UTF-8&quot;);
   * }
   * catch (UnsupportedEncodingException e)
   * {
   *   // Should never happen: UTF-8 is always supported.
   *   throw new RuntimeException(e);
   * }
   * </pre>
   *
   * @param bytes
   *          The byte array to be decoded as a UTF-8 string.
   * @return The decoded string.
   */
  public static String decodeUTF8(final byte[] bytes)
  {
    Reject.ifNull(bytes);

    if (bytes.length == 0)
    {
      return "".intern();
    }

    final StringBuilder builder = new StringBuilder(bytes.length);
    final int sz = bytes.length;

    for (int i = 0; i < sz; i++)
    {
      final byte b = bytes[i];
      if ((b & 0x7f) != b)
      {
        try
        {
          builder.append(new String(bytes, i, (sz - i), "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
          // Should never happen: UTF-8 is always supported.
          throw new RuntimeException(e);
        }
        break;
      }
      builder.append((char) b);
    }
    return builder.toString();
  }



  /**
   * Retrieves a string representation of the provided byte in hexadecimal.
   *
   * @param b   The byte for which to retrieve the hexadecimal string
   *            representation.
   * @return The string representation of the provided byte in hexadecimal.
   */

  public static String byteToHex(final byte b)
  {
    return com.forgerock.opendj.util.StaticUtils.byteToHex(b);
  }
  /**
   * Retrieves a string representation of the provided byte in hexadecimal.
   *
   * @param  b  The byte for which to retrieve the hexadecimal string
   *            representation.
   * @return The string representation of the provided byte in hexadecimal
   *         using lowercase characters.
   */
  public static String byteToLowerHex(final byte b)
  {
    return com.forgerock.opendj.util.StaticUtils.byteToLowerHex(b);
  }

  /**
   * Retrieves a string representation of the contents of the provided byte
   * array using hexadecimal characters with no space between each byte.
   *
   * @param  b  The byte array containing the data.
   *
   * @return  A string representation of the contents of the provided byte
   *          array using hexadecimal characters.
   */
  public static String bytesToHexNoSpace(byte[] b)
  {
    if (b == null || b.length == 0)
    {
      return "";
    }

    int arrayLength = b.length;
    StringBuilder buffer = new StringBuilder(arrayLength * 2);

    for (int i=0; i < arrayLength; i++)
    {
      buffer.append(byteToHex(b[i]));
    }

    return buffer.toString();
  }



  /**
   * Retrieves a string representation of the contents of the provided byte
   * array using hexadecimal characters and a space between each byte.
   *
   * @param  b  The byte array containing the data.
   * @return  A string representation of the contents of the provided byte
   *          array using hexadecimal characters.
   */
  public static String bytesToHex(byte[] b)
  {
    if (b == null || b.length == 0)
    {
      return "";
    }

    int arrayLength = b.length;
    StringBuilder buffer = new StringBuilder((arrayLength - 1) * 3 + 2);
    buffer.append(byteToHex(b[0]));

    for (int i=1; i < arrayLength; i++)
    {
      buffer.append(" ");
      buffer.append(byteToHex(b[i]));
    }

    return buffer.toString();
  }

  /**
   * Retrieves a string representation of the contents of the provided byte
   * sequence using hexadecimal characters and a space between each byte.
   *
   * @param b The byte sequence containing the data.
   * @return A string representation of the contents of the provided byte
   *         sequence using hexadecimal characters.
   */
  public static String bytesToHex(ByteSequence b)
  {
    if (b == null || b.length() == 0)
    {
      return "";
    }

    int arrayLength = b.length();
    StringBuilder buffer = new StringBuilder((arrayLength - 1) * 3 + 2);
    buffer.append(byteToHex(b.byteAt(0)));

    for (int i=1; i < arrayLength; i++)
    {
      buffer.append(" ");
      buffer.append(byteToHex(b.byteAt(i)));
    }

    return buffer.toString();
  }



  /**
   * Retrieves a string representation of the contents of the provided byte
   * array using hexadecimal characters and a colon between each byte.
   *
   * @param  b  The byte array containing the data.
   *
   * @return  A string representation of the contents of the provided byte
   *          array using hexadecimal characters.
   */
  public static String bytesToColonDelimitedHex(byte[] b)
  {
    if (b == null || b.length == 0)
    {
      return "";
    }

    int arrayLength = b.length;
    StringBuilder buffer = new StringBuilder((arrayLength - 1) * 3 + 2);
    buffer.append(byteToHex(b[0]));

    for (int i=1; i < arrayLength; i++)
    {
      buffer.append(":");
      buffer.append(byteToHex(b[i]));
    }

    return buffer.toString();
  }



  /**
   * Retrieves a string representation of the contents of the provided byte
   * buffer using hexadecimal characters and a space between each byte.
   *
   * @param  b  The byte buffer containing the data.
   *
   * @return  A string representation of the contents of the provided byte
   *          buffer using hexadecimal characters.
   */
  public static String bytesToHex(ByteBuffer b)
  {
    if (b == null)
    {
      return "";
    }

    int position = b.position();
    int limit    = b.limit();
    int length   = limit - position;

    if (length == 0)
    {
      return "";
    }

    StringBuilder buffer = new StringBuilder((length - 1) * 3 + 2);
    buffer.append(byteToHex(b.get()));

    for (int i=1; i < length; i++)
    {
      buffer.append(" ");
      buffer.append(byteToHex(b.get()));
    }

    b.position(position);
    b.limit(limit);

    return buffer.toString();
  }



  /**
   * Appends a string representation of the provided byte array to the given
   * buffer using the specified indent.  The data will be formatted with sixteen
   * hex bytes in a row followed by the ASCII representation, then wrapping to a
   * new line as necessary.
   *
   * @param  buffer  The buffer to which the information is to be appended.
   * @param  b       The byte array containing the data to write.
   * @param  indent  The number of spaces to indent the output.
   */
  public static void byteArrayToHexPlusAscii(StringBuilder buffer, byte[] b,
                                             int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }



    int length = b.length;
    int pos    = 0;
    while (length - pos >= 16)
    {
      StringBuilder asciiBuf = new StringBuilder(17);

      buffer.append(indentBuf);
      buffer.append(byteToHex(b[pos]));
      asciiBuf.append(byteToASCII(b[pos]));
      pos++;

      for (int i=1; i < 16; i++, pos++)
      {
        buffer.append(' ');
        buffer.append(byteToHex(b[pos]));
        asciiBuf.append(byteToASCII(b[pos]));

        if (i == 7)
        {
          buffer.append("  ");
          asciiBuf.append(' ');
        }
      }

      buffer.append("  ");
      buffer.append(asciiBuf);
      buffer.append(EOL);
    }


    int remaining = length - pos;
    if (remaining > 0)
    {
      StringBuilder asciiBuf = new StringBuilder(remaining+1);

      buffer.append(indentBuf);
      buffer.append(byteToHex(b[pos]));
      asciiBuf.append(byteToASCII(b[pos]));
      pos++;

      for (int i=1; i < 16; i++)
      {
        buffer.append(' ');

        if (i < remaining)
        {
          buffer.append(byteToHex(b[pos]));
          asciiBuf.append(byteToASCII(b[pos]));
          pos++;
        }
        else
        {
          buffer.append("  ");
        }

        if (i == 7)
        {
          buffer.append("  ");

          if (i < remaining)
          {
            asciiBuf.append(' ');
          }
        }
      }

      buffer.append("  ");
      buffer.append(asciiBuf);
      buffer.append(EOL);
    }
  }

  private static char byteToASCII(byte b)
  {
    return com.forgerock.opendj.util.StaticUtils.byteToASCII(b);
  }

  /**
   * Compare two byte arrays for order. Returns a negative integer,
   * zero, or a positive integer as the first argument is less than,
   * equal to, or greater than the second.
   *
   * @param a
   *          The first byte array to be compared.
   * @param a2
   *          The second byte array to be compared.
   * @return Returns a negative integer, zero, or a positive integer
   *         if the first byte array is less than, equal to, or greater
   *         than the second.
   */
  public static int compare(byte[] a, byte[] a2) {
    if (a == a2) {
      return 0;
    }

    if (a == null) {
      return -1;
    }

    if (a2 == null) {
      return 1;
    }

    int minLength = Math.min(a.length, a2.length);
    for (int i = 0; i < minLength; i++) {
      int firstByte = 0xFF & a[i];
      int secondByte = 0xFF & a2[i];
      if (firstByte != secondByte) {
        if (firstByte < secondByte) {
          return -1;
        } else if (firstByte > secondByte) {
          return 1;
        }
      }
    }

    return a.length - a2.length;
  }

  /**
   * Retrieves the best human-readable message for the provided exception.  For
   * exceptions defined in the OpenDJ project, it will attempt to use the
   * message (combining it with the message ID if available).  For some
   * exceptions that use encapsulation (e.g., InvocationTargetException), it
   * will be unwrapped and the cause will be treated.  For all others, the
   *
   *
   * @param  t  The {@code Throwable} object for which to retrieve the message.
   *
   * @return  The human-readable message generated for the provided exception.
   */
  public static LocalizableMessage getExceptionMessage(Throwable t)
  {
    if (t instanceof IdentifiedException)
    {
      IdentifiedException ie = (IdentifiedException) t;

      StringBuilder message = new StringBuilder();
      message.append(ie.getMessage());
      message.append(" (id=");
      LocalizableMessage ieMsg = ie.getMessageObject();
      if (ieMsg != null) {
        message.append(ieMsg.resourceName()).append("-").append(ieMsg.ordinal());
      } else {
        message.append("-1");
      }
      message.append(")");
      return LocalizableMessage.raw(message.toString());
    }
    else
    {
      return com.forgerock.opendj.util.StaticUtils.getExceptionMessage(t);
    }
  }



  /**
   * Retrieves a stack trace from the provided exception as a single-line
   * string.
   *
   * @param  t  The exception for which to retrieve the stack trace.
   *
   * @return  A stack trace from the provided exception as a single-line string.
   */
  public static String stackTraceToSingleLineString(Throwable t)
  {
    return com.forgerock.opendj.util.StaticUtils.stackTraceToSingleLineString(t, DynamicConstants.DEBUG_BUILD);
  }

  /**
   * Retrieves a string representation of the stack trace for the provided
   * exception.
   *
   * @param  t  The exception for which to retrieve the stack trace.
   *
   * @return  A string representation of the stack trace for the provided
   *          exception.
   */
  public static String stackTraceToString(Throwable t)
  {
    StringBuilder buffer = new StringBuilder();
    stackTraceToString(buffer, t);
    return buffer.toString();
  }

  /**
   * Check if the stack trace of provided exception contains a given cause.
   *
   * @param throwable
   *          exception that may contain the cause
   * @param searchedCause
   *          class of the cause to look for. Any subclass will match.
   * @return true if and only if the given cause is found as a cause of any
   *         level in the provided exception.
   */
  public static boolean stackTraceContainsCause(
      Throwable throwable, Class<? extends Throwable> searchedCause)
  {
    Throwable t = throwable;
    while ((t = t.getCause()) != null)
    {
      if (searchedCause.isAssignableFrom(t.getClass()))
      {
        return true;
      }

    }
    return false;
  }

  /**
   * Appends a string representation of the stack trace for the provided
   * exception to the given buffer.
   *
   * @param  buffer  The buffer to which the information is to be appended.
   * @param  t       The exception for which to retrieve the stack trace.
   */
  private static void stackTraceToString(StringBuilder buffer, Throwable t)
  {
    if (t == null)
    {
      return;
    }

    buffer.append(t);

    for (StackTraceElement e : t.getStackTrace())
    {
      buffer.append(EOL);
      buffer.append("  ");
      buffer.append(e.getClassName());
      buffer.append(".");
      buffer.append(e.getMethodName());
      buffer.append("(");
      buffer.append(e.getFileName());
      buffer.append(":");
      buffer.append(e.getLineNumber());
      buffer.append(")");
    }

    while (t.getCause() != null)
    {
      t = t.getCause();
      buffer.append(EOL);
      buffer.append("Caused by ");
      buffer.append(t);

      for (StackTraceElement e : t.getStackTrace())
      {
        buffer.append(EOL);
        buffer.append("  ");
        buffer.append(e.getClassName());
        buffer.append(".");
        buffer.append(e.getMethodName());
        buffer.append("(");
        buffer.append(e.getFileName());
        buffer.append(":");
        buffer.append(e.getLineNumber());
        buffer.append(")");
      }
    }

    buffer.append(EOL);
  }



  /**
   * Retrieves a backtrace for the current thread consisting only of filenames
   * and line numbers that may be useful in debugging the origin of problems
   * that should not have happened.  Note that this may be an expensive
   * operation to perform, so it should only be used for error conditions or
   * debugging.
   *
   * @return  A backtrace for the current thread.
   */
  public static String getBacktrace()
  {
    StringBuilder buffer = new StringBuilder();

    StackTraceElement[] elements = Thread.currentThread().getStackTrace();

    if (elements.length > 1)
    {
      buffer.append(elements[1].getFileName());
      buffer.append(":");
      buffer.append(elements[1].getLineNumber());

      for (int i=2; i < elements.length; i++)
      {
        buffer.append(" ");
        buffer.append(elements[i].getFileName());
        buffer.append(":");
        buffer.append(elements[i].getLineNumber());
      }
    }

    return buffer.toString();
  }



  /**
   * Retrieves a backtrace for the provided exception consisting of only
   * filenames and line numbers that may be useful in debugging the origin of
   * problems.  This is less expensive than the call to
   * {@code getBacktrace} without any arguments if an exception has already
   * been thrown.
   *
   * @param  t  The exception for which to obtain the backtrace.
   *
   * @return  A backtrace from the provided exception.
   */
  public static String getBacktrace(Throwable t)
  {
    StringBuilder buffer = new StringBuilder();

    StackTraceElement[] elements = t.getStackTrace();

    if (elements.length > 0)
    {
      buffer.append(elements[0].getFileName());
      buffer.append(":");
      buffer.append(elements[0].getLineNumber());

      for (int i=1; i < elements.length; i++)
      {
        buffer.append(" ");
        buffer.append(elements[i].getFileName());
        buffer.append(":");
        buffer.append(elements[i].getLineNumber());
      }
    }

    return buffer.toString();
  }



  /**
   * Indicates whether the provided character is a numeric digit.
   *
   * @param  c  The character for which to make the determination.
   *
   * @return  {@code true} if the provided character represents a numeric
   *          digit, or {@code false} if not.
   */
  public static boolean isDigit(final char c) {
    return com.forgerock.opendj.util.StaticUtils.isDigit(c);
  }



  /**
   * Indicates whether the provided character is an ASCII alphabetic character.
   *
   * @param  c  The character for which to make the determination.
   *
   * @return  {@code true} if the provided value is an uppercase or
   *          lowercase ASCII alphabetic character, or {@code false} if it
   *          is not.
   */
  public static boolean isAlpha(final char c) {
    return com.forgerock.opendj.util.StaticUtils.isAlpha(c);
  }

  /**
   * Indicates whether the provided character is a hexadecimal digit.
   *
   * @param  c  The character for which to make the determination.
   *
   * @return  {@code true} if the provided character represents a
   *          hexadecimal digit, or {@code false} if not.
   */
  public static boolean isHexDigit(final char c) {
    return com.forgerock.opendj.util.StaticUtils.isHexDigit(c);
  }

  /**
   * Indicates whether the provided byte represents a hexadecimal digit.
   *
   * @param  b  The byte for which to make the determination.
   *
   * @return  {@code true} if the provided byte represents a hexadecimal
   *          digit, or {@code false} if not.
   */
  public static boolean isHexDigit(byte b)
  {
    switch (b)
    {
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
      case 'A':
      case 'B':
      case 'C':
      case 'D':
      case 'E':
      case 'F':
      case 'a':
      case 'b':
      case 'c':
      case 'd':
      case 'e':
      case 'f':
        return true;
      default:
        return false;
    }
  }



  /**
   * Converts the provided hexadecimal string to a byte array.
   *
   * @param  hexString  The hexadecimal string to convert to a byte array.
   *
   * @return  The byte array containing the binary representation of the
   *          provided hex string.
   *
   * @throws  ParseException  If the provided string contains invalid
   *                          hexadecimal digits or does not contain an even
   *                          number of digits.
   */
  public static byte[] hexStringToByteArray(String hexString)
         throws ParseException
  {
    int length;
    if (hexString == null || ((length = hexString.length()) == 0))
    {
      return new byte[0];
    }


    if ((length % 2) == 1)
    {
      LocalizableMessage message = ERR_HEX_DECODE_INVALID_LENGTH.get(hexString);
      throw new ParseException(message.toString(), 0);
    }


    int pos = 0;
    int arrayLength = length / 2;
    byte[] returnArray = new byte[arrayLength];
    for (int i=0; i < arrayLength; i++)
    {
      switch (hexString.charAt(pos++))
      {
        case '0':
          returnArray[i] = 0x00;
          break;
        case '1':
          returnArray[i] = 0x10;
          break;
        case '2':
          returnArray[i] = 0x20;
          break;
        case '3':
          returnArray[i] = 0x30;
          break;
        case '4':
          returnArray[i] = 0x40;
          break;
        case '5':
          returnArray[i] = 0x50;
          break;
        case '6':
          returnArray[i] = 0x60;
          break;
        case '7':
          returnArray[i] = 0x70;
          break;
        case '8':
          returnArray[i] = (byte) 0x80;
          break;
        case '9':
          returnArray[i] = (byte) 0x90;
          break;
        case 'A':
        case 'a':
          returnArray[i] = (byte) 0xA0;
          break;
        case 'B':
        case 'b':
          returnArray[i] = (byte) 0xB0;
          break;
        case 'C':
        case 'c':
          returnArray[i] = (byte) 0xC0;
          break;
        case 'D':
        case 'd':
          returnArray[i] = (byte) 0xD0;
          break;
        case 'E':
        case 'e':
          returnArray[i] = (byte) 0xE0;
          break;
        case 'F':
        case 'f':
          returnArray[i] = (byte) 0xF0;
          break;
        default:
          LocalizableMessage message = ERR_HEX_DECODE_INVALID_CHARACTER.get(
              hexString, hexString.charAt(pos-1));
          throw new ParseException(message.toString(), 0);
      }

      switch (hexString.charAt(pos++))
      {
        case '0':
          // No action required.
          break;
        case '1':
          returnArray[i] |= 0x01;
          break;
        case '2':
          returnArray[i] |= 0x02;
          break;
        case '3':
          returnArray[i] |= 0x03;
          break;
        case '4':
          returnArray[i] |= 0x04;
          break;
        case '5':
          returnArray[i] |= 0x05;
          break;
        case '6':
          returnArray[i] |= 0x06;
          break;
        case '7':
          returnArray[i] |= 0x07;
          break;
        case '8':
          returnArray[i] |= 0x08;
          break;
        case '9':
          returnArray[i] |= 0x09;
          break;
        case 'A':
        case 'a':
          returnArray[i] |= 0x0A;
          break;
        case 'B':
        case 'b':
          returnArray[i] |= 0x0B;
          break;
        case 'C':
        case 'c':
          returnArray[i] |= 0x0C;
          break;
        case 'D':
        case 'd':
          returnArray[i] |= 0x0D;
          break;
        case 'E':
        case 'e':
          returnArray[i] |= 0x0E;
          break;
        case 'F':
        case 'f':
          returnArray[i] |= 0x0F;
          break;
        default:
          LocalizableMessage message = ERR_HEX_DECODE_INVALID_CHARACTER.get(
              hexString, hexString.charAt(pos-1));
          throw new ParseException(message.toString(), 0);
      }
    }

    return returnArray;
  }



  /**
   * Indicates whether the provided value needs to be base64-encoded if it is
   * represented in LDIF form.
   *
   * @param  valueBytes  The binary representation of the attribute value for
   *                     which to make the determination.
   *
   * @return  {@code true} if the value needs to be base64-encoded if it is
   *          represented in LDIF form, or {@code false} if not.
   */
  public static boolean needsBase64Encoding(ByteSequence valueBytes)
  {
    int length;
    if (valueBytes == null || ((length = valueBytes.length()) == 0))
    {
      return false;
    }


    // If the value starts with a space, colon, or less than, then it needs to
    // be base64-encoded.
    switch (valueBytes.byteAt(0))
    {
      case 0x20: // Space
      case 0x3A: // Colon
      case 0x3C: // Less-than
        return true;
    }


    // If the value ends with a space, then it needs to be base64-encoded.
    if (length > 1 && valueBytes.byteAt(length - 1) == 0x20)
    {
      return true;
    }


    // If the value contains a null, newline, or return character, then it needs
    // to be base64-encoded.
    byte b;
    for (int i = 0; i < valueBytes.length(); i++)
    {
      b = valueBytes.byteAt(i);
      if (b < 0 || 127 < b)
      {
        return true;
      }

      switch (b)
      {
        case 0x00: // Null
        case 0x0A: // New line
        case 0x0D: // Carriage return
          return true;
      }
    }


    // If we've made it here, then there's no reason to base64-encode.
    return false;
  }



  /**
   * Indicates whether the provided value needs to be base64-encoded if it is
   * represented in LDIF form.
   *
   * @param  valueString  The string representation of the attribute value for
   *                      which to make the determination.
   *
   * @return  {@code true} if the value needs to be base64-encoded if it is
   *          represented in LDIF form, or {@code false} if not.
   */
  public static boolean needsBase64Encoding(String valueString)
  {
    int length;
    if (valueString == null || ((length = valueString.length()) == 0))
    {
      return false;
    }


    // If the value starts with a space, colon, or less than, then it needs to
    // be base64-encoded.
    switch (valueString.charAt(0))
    {
      case ' ':
      case ':':
      case '<':
        return true;
    }


    // If the value ends with a space, then it needs to be base64-encoded.
    if (length > 1 && valueString.charAt(length - 1) == ' ')
    {
      return true;
    }


    // If the value contains a null, newline, or return character, then it needs
    // to be base64-encoded.
    for (int i=0; i < length; i++)
    {
      char c = valueString.charAt(i);
      if (c <= 0 || c == 0x0A || c == 0x0D || c > 127)
      {
        return true;
      }
    }


    // If we've made it here, then there's no reason to base64-encode.
    return false;
  }



  /**
   * Indicates whether the use of the exec method will be allowed on this
   * system.  It will be allowed by default, but that capability will be removed
   * if the org.opends.server.DisableExec system property is set and has any
   * value other than "false", "off", "no", or "0".
   *
   * @return  {@code true} if the use of the exec method should be allowed,
   *          or {@code false} if it should not be allowed.
   */
  private static boolean mayUseExec()
  {
    return !DirectoryServer.getEnvironmentConfig().disableExec();
  }

  /**
   * Indicates whether the provided string contains a name or OID for a schema
   * element like an attribute type or objectclass.
   *
   * @param  element        The string containing the substring for which to
   *                        make the determination.
   * @param  startPos       The position of the first character that is to be
   *                        checked.
   * @param  endPos         The position of the first character after the start
   *                        position that is not to be checked.
   * @param  invalidReason  The buffer to which the invalid reason is to be
   *                        appended if a problem is found.
   *
   * @return  {@code true} if the provided string contains a valid name or
   *          OID for a schema element, or {@code false} if it does not.
   */
  public static boolean isValidSchemaElement(String element, int startPos,
                                             int endPos,
                                             LocalizableMessageBuilder invalidReason)
  {
    if (element == null || startPos >= endPos)
    {
      invalidReason.append(ERR_SCHEMANAME_EMPTY_VALUE.get());
      return false;
    }


    char c = element.charAt(startPos);
    if (isAlpha(c))
    {
      // This can only be a name and not an OID.  The only remaining characters
      // must be letters, digits, dashes, and possibly the underscore.
      for (int i=startPos+1; i < endPos; i++)
      {
        c = element.charAt(i);
        if (!isAlpha(c)
            && !isDigit(c)
            && c != '-'
            && (c != '_' || !DirectoryServer.allowAttributeNameExceptions()))
        {
          // This is an illegal character for an attribute name.
          invalidReason.append(ERR_SCHEMANAME_ILLEGAL_CHAR.get(element, c, i));
          return false;
        }
      }
    }
    else if (isDigit(c))
    {
      // This should indicate an OID, but it may also be a name if name
      // exceptions are enabled.  Since we don't know for sure, we'll just
      // hold off until we know for sure.
      boolean isKnown    = !DirectoryServer.allowAttributeNameExceptions();
      boolean isNumeric  = true;
      boolean lastWasDot = false;

      for (int i=startPos+1; i < endPos; i++)
      {
        c = element.charAt(i);
        if (c == '.')
        {
          if (isKnown)
          {
            if (isNumeric)
            {
              // This is probably legal unless the last character was also a
              // period.
              if (lastWasDot)
              {
                invalidReason.append(ERR_SCHEMANAME_CONSECUTIVE_PERIODS.get(
                        element, i));
                return false;
              }
              lastWasDot = true;
            }
            else
            {
              // This is an illegal character.
              invalidReason.append(ERR_SCHEMANAME_ILLEGAL_CHAR.get(
                      element, c, i));
              return false;
            }
          }
          else
          {
            // Now we know that this must be a numeric OID and not an attribute
            // name with exceptions allowed.
            lastWasDot = true;
            isKnown    = true;
            isNumeric  = true;
          }
        }
        else
        {
          lastWasDot = false;

          if (isAlpha(c) || c == '-' || c == '_')
          {
            if (isKnown)
            {
              if (isNumeric)
              {
                // This is an illegal character for a numeric OID.
                invalidReason.append(ERR_SCHEMANAME_ILLEGAL_CHAR.get(
                        element, c, i));
                return false;
              }
            }
            else
            {
              // Now we know that this must be an attribute name with exceptions
              // allowed and not a numeric OID.
              isKnown   = true;
              isNumeric = false;
            }
          }
          else if (! isDigit(c))
          {
            // This is an illegal character.
            invalidReason.append(ERR_SCHEMANAME_ILLEGAL_CHAR.get(
                    element, c, i));
            return false;
          }
        }
      }
    }
    else
    {
      // This is an illegal character.
      invalidReason.append(ERR_SCHEMANAME_ILLEGAL_CHAR.get(
              element, c, startPos));
      return false;
    }


    // If we've gotten here, then the value is fine.
    return true;
  }



  /**
   * Indicates whether the provided TCP address is already in use.
   *
   * @param  address        IP address of the TCP address for which to make
   *                        the determination.
   * @param  port           TCP port number of the TCP address for which to
   *                        make the determination.
   * @param  allowReuse     Whether TCP address reuse is allowed when
   *                        making the determination.
   *
   * @return  {@code true} if the provided TCP address is already in
   *          use, or {@code false} otherwise.
   */
  public static boolean isAddressInUse(
    InetAddress address, int port,
    boolean allowReuse)
  {
    try {
      // HACK:
      // With dual stacks we can have a situation when INADDR_ANY/PORT
      // is bound in TCP4 space but available in TCP6 space and since
      // JavaServerSocket implementation will always use TCP46 on dual
      // stacks the bind below will always succeed in such cases thus
      // shadowing anything that is already bound to INADDR_ANY/PORT.
      // While technically correct, with IPv4 and IPv6 being separate
      // address spaces, it presents a problem to end users because a
      // common case scenario is to have a single service serving both
      // address spaces ie listening to the same port in both spaces
      // on wildcard addresses 0 and ::. ServerSocket implementation
      // does not provide any means of working with each address space
      // separately such as doing TCP4 or TCP6 only binds thus we have
      // to do a dummy connect to INADDR_ANY/PORT to check if it is
      // bound to something already. This is only needed for wildcard
      // addresses as specific IPv4 or IPv6 addresses will always be
      // handled in their respective address space.
      if (address.isAnyLocalAddress()) {
        try (Socket clientSocket = new Socket()) {
          // This might fail on some stacks but this is the best we
          // can do. No need for explicit timeout since it is local
          // address and we have to know for sure unless it fails.
          clientSocket.connect(new InetSocketAddress(address, port));
          if (clientSocket.isConnected()) {
            return true;
          }
        } catch (IOException ignore) {
          // ignore.
        }
      }
      try (ServerSocket serverSocket = new ServerSocket()) {
        serverSocket.setReuseAddress(allowReuse);
        serverSocket.bind(new InetSocketAddress(address, port));
        return false;
      }
    } catch (IOException ignore) {
      // no-op
    }
    return true;
  }



  /**
   * Returns a lower-case string representation of a given string, verifying for null input string.
   *
   * @param s the mixed case string
   * @return a lower-case string
   * @see com.forgerock.opendj.util.StaticUtils#toLowerCase(String)
   */
  public static String toLowerCase(String s)
  {
    return (s == null ? null : com.forgerock.opendj.util.StaticUtils.toLowerCase(s));
  }

  /**
   * Appends a lower-case string representation of a given ByteSequence to a StringBuilder,
   * verifying for null input.
   *
   * @param  b       The byte array for which to obtain the lowercase string
   *                 representation.
   * @param  buffer  The buffer to which the lowercase form of the string should
   *                 be appended.
   * @param  trim    Indicates whether leading and trailing spaces should be
   *                 omitted from the string representation.
   * @see com.forgerock.opendj.util.StaticUtils#toLowerCase(ByteSequence, StringBuilder)}
   */
  public static void toLowerCase(ByteSequence b, StringBuilder buffer, boolean trim)
  {
    if (b == null)
    {
      return;
    }

    if (trim)
    {
      int begin = 0;
      int end = b.length() - 1;
      while (begin <= end)
      {
        if (b.byteAt(begin) == ' ')
        {
          begin++;
        }
        else if (b.byteAt(end) == ' ')
        {
          end--;
        }
        else
        {
          break;
        }
      }
      if (begin > 0 || end < b.length() - 1)
      {
        b = b.subSequence(begin, end + 1);
      }
    }

    com.forgerock.opendj.util.StaticUtils.toLowerCase(b, buffer);
  }



  /**
   * Retrieves an uppercase representation of the given string.  This
   * implementation presumes that the provided string will contain only ASCII
   * characters and is optimized for that case.  However, if a non-ASCII
   * character is encountered it will fall back on a more expensive algorithm
   * that will work properly for non-ASCII characters.
   *
   * @param  s  The string for which to obtain the uppercase representation.
   *
   * @return  The uppercase representation of the given string.
   */
  public static String toUpperCase(String s)
  {
    if (s == null)
    {
      return null;
    }

    StringBuilder buffer = new StringBuilder(s.length());
    toUpperCase(s, buffer);
    return buffer.toString();
  }



  /**
   * Appends an uppercase representation of the given string to the provided
   * buffer.  This implementation presumes that the provided string will contain
   * only ASCII characters and is optimized for that case.  However, if a
   * non-ASCII character is encountered it will fall back on a more expensive
   * algorithm that will work properly for non-ASCII characters.
   *
   * @param  s       The string for which to obtain the uppercase
   *                 representation.
   * @param  buffer  The buffer to which the uppercase form of the string should
   *                 be appended.
   */
  private static void toUpperCase(String s, StringBuilder buffer)
  {
    if (s == null)
    {
      return;
    }

    int length = s.length();
    for (int i=0; i < length; i++)
    {
      char c = s.charAt(i);

      if ((c & 0x7F) != c)
      {
        buffer.append(s.substring(i).toUpperCase());
        return;
      }

      switch (c)
      {
        case 'a':
          buffer.append('A');
          break;
        case 'b':
          buffer.append('B');
          break;
        case 'c':
          buffer.append('C');
          break;
        case 'd':
          buffer.append('D');
          break;
        case 'e':
          buffer.append('E');
          break;
        case 'f':
          buffer.append('F');
          break;
        case 'g':
          buffer.append('G');
          break;
        case 'h':
          buffer.append('H');
          break;
        case 'i':
          buffer.append('I');
          break;
        case 'j':
          buffer.append('J');
          break;
        case 'k':
          buffer.append('K');
          break;
        case 'l':
          buffer.append('L');
          break;
        case 'm':
          buffer.append('M');
          break;
        case 'n':
          buffer.append('N');
          break;
        case 'o':
          buffer.append('O');
          break;
        case 'p':
          buffer.append('P');
          break;
        case 'q':
          buffer.append('Q');
          break;
        case 'r':
          buffer.append('R');
          break;
        case 's':
          buffer.append('S');
          break;
        case 't':
          buffer.append('T');
          break;
        case 'u':
          buffer.append('U');
          break;
        case 'v':
          buffer.append('V');
          break;
        case 'w':
          buffer.append('W');
          break;
        case 'x':
          buffer.append('X');
          break;
        case 'y':
          buffer.append('Y');
          break;
        case 'z':
          buffer.append('Z');
          break;
        default:
          buffer.append(c);
      }
    }
  }



  /**
   * Appends an uppercase string representation of the contents of the given
   * byte array to the provided buffer, optionally trimming leading and trailing
   * spaces.  This implementation presumes that the provided string will contain
   * only ASCII characters and is optimized for that case.  However, if a
   * non-ASCII character is encountered it will fall back on a more expensive
   * algorithm that will work properly for non-ASCII characters.
   *
   * @param  b       The byte array for which to obtain the uppercase string
   *                 representation.
   * @param  buffer  The buffer to which the uppercase form of the string should
   *                 be appended.
   * @param  trim    Indicates whether leading and trailing spaces should be
   *                 omitted from the string representation.
   */
  public static void toUpperCase(byte[] b, StringBuilder buffer, boolean trim)
  {
    if (b == null)
    {
      return;
    }

    int length = b.length;
    for (int i=0; i < length; i++)
    {
      if ((b[i] & 0x7F) != b[i])
      {
        try
        {
          buffer.append(new String(b, i, (length-i), "UTF-8").toUpperCase());
        }
        catch (Exception e)
        {
          logger.traceException(e);
          buffer.append(new String(b, i, (length - i)).toUpperCase());
        }
        break;
      }

      int bufferLength = buffer.length();
      switch (b[i])
      {
        case ' ':
          // If we don't care about trimming, then we can always append the
          // space.  Otherwise, only do so if there are other characters in the value.
          if (trim && bufferLength == 0)
          {
            break;
          }

          buffer.append(' ');
          break;
        case 'a':
          buffer.append('A');
          break;
        case 'b':
          buffer.append('B');
          break;
        case 'c':
          buffer.append('C');
          break;
        case 'd':
          buffer.append('D');
          break;
        case 'e':
          buffer.append('E');
          break;
        case 'f':
          buffer.append('F');
          break;
        case 'g':
          buffer.append('G');
          break;
        case 'h':
          buffer.append('H');
          break;
        case 'i':
          buffer.append('I');
          break;
        case 'j':
          buffer.append('J');
          break;
        case 'k':
          buffer.append('K');
          break;
        case 'l':
          buffer.append('L');
          break;
        case 'm':
          buffer.append('M');
          break;
        case 'n':
          buffer.append('N');
          break;
        case 'o':
          buffer.append('O');
          break;
        case 'p':
          buffer.append('P');
          break;
        case 'q':
          buffer.append('Q');
          break;
        case 'r':
          buffer.append('R');
          break;
        case 's':
          buffer.append('S');
          break;
        case 't':
          buffer.append('T');
          break;
        case 'u':
          buffer.append('U');
          break;
        case 'v':
          buffer.append('V');
          break;
        case 'w':
          buffer.append('W');
          break;
        case 'x':
          buffer.append('X');
          break;
        case 'y':
          buffer.append('Y');
          break;
        case 'z':
          buffer.append('Z');
          break;
        default:
          buffer.append((char) b[i]);
      }
    }

    if (trim)
    {
      // Strip off any trailing spaces.
      for (int i=buffer.length()-1; i > 0; i--)
      {
        if (buffer.charAt(i) == ' ')
        {
          buffer.delete(i, i+1);
        }
        else
        {
          break;
        }
      }
    }
  }



  /**
   * Append a string to a string builder, escaping any double quotes
   * according to the StringValue production in RFC 3641.
   * <p>
   * In RFC 3641 the StringValue production looks like this:
   *
   * <pre>
   *    StringValue       = dquote *SafeUTF8Character dquote
   *    dquote            = %x22 ; &quot; (double quote)
   *    SafeUTF8Character = %x00-21 / %x23-7F /   ; ASCII minus dquote
   *                        dquote dquote /       ; escaped double quote
   *                        %xC0-DF %x80-BF /     ; 2 byte UTF-8 character
   *                        %xE0-EF 2(%x80-BF) /  ; 3 byte UTF-8 character
   *                        %xF0-F7 3(%x80-BF)    ; 4 byte UTF-8 character
   * </pre>
   *
   * <p>
   * That is, strings are surrounded by double-quotes and any internal
   * double-quotes are doubled up.
   *
   * @param builder
   *          The string builder.
   * @param string
   *          The string to escape and append.
   * @return Returns the string builder.
   */
  public static StringBuilder toRFC3641StringValue(StringBuilder builder,
      String string)
  {
    // Initial double-quote.
    builder.append('"');

    for (char c : string.toCharArray())
    {
      if (c == '"')
      {
        // Internal double-quotes are escaped using a double-quote.
        builder.append('"');
      }
      builder.append(c);
    }

    // Trailing double-quote.
    builder.append('"');

    return builder;
  }

  /**
   * Retrieves an array list containing the contents of the provided array.
   *
   * @param  stringArray  The string array to convert to an array list.
   *
   * @return  An array list containing the contents of the provided array.
   */
  public static ArrayList<String> arrayToList(String... stringArray)
  {
    if (stringArray == null)
    {
      return null;
    }

    ArrayList<String> stringList = new ArrayList<>(stringArray.length);
    Collections.addAll(stringList, stringArray);
    return stringList;
  }


  /**
   * Attempts to delete the specified file or directory. If it is a directory,
   * then any files or subdirectories that it contains will be recursively
   * deleted as well.
   *
   * @param file
   *          The file or directory to be removed.
   * @return {@code true} if the specified file and any subordinates are all
   *         successfully removed, or {@code false} if at least one element in
   *         the subtree could not be removed or file does not exists.
   */
  public static boolean recursiveDelete(File file)
  {
    if (file.exists())
    {
      boolean successful = true;
      if (file.isDirectory())
      {
        File[] childList = file.listFiles();
        if (childList != null)
        {
          for (File f : childList)
          {
            successful &= recursiveDelete(f);
          }
        }
      }

      return successful & file.delete();
    }
    return false;
  }



  /**
   * Moves the indicated file to the specified directory by creating a new file
   * in the target directory, copying the contents of the existing file, and
   * removing the existing file.  The file to move must exist and must be a
   * file.  The target directory must exist, must be a directory, and must not
   * be the directory in which the file currently resides.
   *
   * @param  fileToMove       The file to move to the target directory.
   * @param  targetDirectory  The directory into which the file should be moved.
   *
   * @throws  IOException  If a problem occurs while attempting to move the
   *                       file.
   */
  public static void moveFile(File fileToMove, File targetDirectory)
         throws IOException
  {
    if (! fileToMove.exists())
    {
      LocalizableMessage message = ERR_MOVEFILE_NO_SUCH_FILE.get(fileToMove.getPath());
      throw new IOException(message.toString());
    }

    if (! fileToMove.isFile())
    {
      LocalizableMessage message = ERR_MOVEFILE_NOT_FILE.get(fileToMove.getPath());
      throw new IOException(message.toString());
    }

    if (! targetDirectory.exists())
    {
      LocalizableMessage message =
          ERR_MOVEFILE_NO_SUCH_DIRECTORY.get(targetDirectory.getPath());
      throw new IOException(message.toString());
    }

    if (! targetDirectory.isDirectory())
    {
      LocalizableMessage message =
          ERR_MOVEFILE_NOT_DIRECTORY.get(targetDirectory.getPath());
      throw new IOException(message.toString());
    }

    String newFilePath = targetDirectory.getPath() + File.separator +
                         fileToMove.getName();
    FileInputStream  inputStream  = new FileInputStream(fileToMove);
    FileOutputStream outputStream = new FileOutputStream(newFilePath, false);
    byte[] buffer = new byte[8192];
    while (true)
    {
      int bytesRead = inputStream.read(buffer);
      if (bytesRead < 0)
      {
        break;
      }

      outputStream.write(buffer, 0, bytesRead);
    }

    outputStream.flush();
    outputStream.close();
    inputStream.close();
    fileToMove.delete();
  }

  /**
   * Renames the source file to the target file.  If the target file exists
   * it is first deleted.  The rename and delete operation return values
   * are checked for success and if unsuccessful, this method throws an
   * exception.
   *
   * @param fileToRename The file to rename.
   * @param target       The file to which {@code fileToRename} will be
   *                     moved.
   * @throws IOException If a problem occurs while attempting to rename the
   *                     file.  On the Windows platform, this typically
   *                     indicates that the file is in use by this or another
   *                     application.
   */
  public static void renameFile(File fileToRename, File target)
          throws IOException {
    if (fileToRename != null && target != null)
    {
      synchronized(target)
      {
        if (target.exists() && !target.delete())
        {
          LocalizableMessage message =
              ERR_RENAMEFILE_CANNOT_DELETE_TARGET.get(target.getPath());
          throw new IOException(message.toString());
        }
      }
      if (!fileToRename.renameTo(target))
      {
        LocalizableMessage message = ERR_RENAMEFILE_CANNOT_RENAME.get(
            fileToRename.getPath(), target.getPath());
        throw new IOException(message.toString());

      }
    }
  }

  /**
   * Retrieves a {@code File} object corresponding to the specified path.
   * If the given path is an absolute path, then it will be used.  If the path
   * is relative, then it will be interpreted as if it were relative to the
   * Directory Server root.
   *
   * @param  path  The path string to be retrieved as a {@code File}
   *
   * @return  A {@code File} object that corresponds to the specified path.
   */
  public static File getFileForPath(String path)
  {
    File f = new File (path);
    return f.isAbsolute() ? f : new File(DirectoryServer.getInstanceRoot(), path);
  }

  /**
   * Retrieves a {@code File} object corresponding to the specified path.
   * If the given path is an absolute path, then it will be used.  If the path
   * is relative, then it will be interpreted as if it were relative to the
   * Directory Server root.
   *
   * @param path
   *           The path string to be retrieved as a {@code File}.
   * @param serverContext
   *           The server context.
   *
   * @return  A {@code File} object that corresponds to the specified path.
   */
  public static File getFileForPath(String path, ServerContext serverContext)
  {
    File f = new File (path);
    return f.isAbsolute() ? f : new File(serverContext.getInstanceRoot(), path);
  }



  /**
   * Creates a new, blank entry with the given DN.  It will contain only the
   * attribute(s) contained in the RDN.  The choice of objectclasses will be
   * based on the RDN attribute.  If there is a single RDN attribute, then the
   * following mapping will be used:
   * <BR>
   * <UL>
   *   <LI>c attribute :: country objectclass</LI>
   *   <LI>dc attribute :: domain objectclass</LI>
   *   <LI>o attribute :: organization objectclass</LI>
   *   <LI>ou attribute :: organizationalUnit objectclass</LI>
   * </UL>
   * <BR>
   * Any other single RDN attribute types, or any case in which there are
   * multiple RDN attributes, will use the untypedObject objectclass.  If the
   * RDN includes one or more attributes that are not allowed in the
   * untypedObject objectclass, then the extensibleObject class will also be
   * added.  Note that this method cannot be used to generate an entry
   * with an empty or null DN.
   *
   * @param  dn  The DN to use for the entry.
   *
   * @return  The entry created with the provided DN.
   */
  public static Entry createEntry(DN dn)
  {
    // If the provided DN was null or empty, then return null because we don't
    // support it.
    if (dn == null || dn.isRootDN())
    {
      return null;
    }


    // Get the information about the RDN attributes.
    RDN rdn = dn.rdn();

    // If there is only one RDN attribute, then see which objectclass we should use.
    ObjectClass structuralClass = DirectoryServer.getSchema().getObjectClass(getObjectClassName(rdn));

    // Get the top and untypedObject classes to include in the entry.
    LinkedHashMap<ObjectClass,String> objectClasses = new LinkedHashMap<>(3);
    objectClasses.put(CoreSchema.getTopObjectClass(), OC_TOP);
    objectClasses.put(structuralClass, structuralClass.getNameOrOID());


    // Iterate through the RDN attributes and add them to the set of user or
    // operational attributes.
    LinkedHashMap<AttributeType,List<Attribute>> userAttributes = new LinkedHashMap<>();
    LinkedHashMap<AttributeType,List<Attribute>> operationalAttributes = new LinkedHashMap<>();

    boolean extensibleObjectAdded = false;
    for (AVA ava : rdn)
    {
      AttributeType attrType = ava.getAttributeType();

      // First, see if this type is allowed by the untypedObject class.  If not,
      // then we'll need to include the extensibleObject class.
      if (!structuralClass.isRequiredOrOptional(attrType) && !extensibleObjectAdded)
      {
        objectClasses.put(CoreSchema.getTopObjectClass(), OC_EXTENSIBLE_OBJECT);
        extensibleObjectAdded = true;
      }


      // Create the attribute and add it to the appropriate map.
      addAttributeValue(attrType.isOperational() ? operationalAttributes : userAttributes, ava);
    }


    // Create and return the entry.
    return new Entry(dn, objectClasses, userAttributes, operationalAttributes);
  }

  private static String getObjectClassName(RDN rdn)
  {
    if (rdn.size() == 1)
    {
      final AttributeType attrType = rdn.getFirstAVA().getAttributeType();
      if (attrType.hasName(ATTR_C))
      {
        return OC_COUNTRY;
      }
      else if (attrType.hasName(ATTR_DC))
      {
        return OC_DOMAIN;
      }
      else if (attrType.hasName(ATTR_O))
      {
        return OC_ORGANIZATION;
      }
      else if (attrType.hasName(ATTR_OU))
      {
        return OC_ORGANIZATIONAL_UNIT_LC;
      }
    }
    return OC_UNTYPED_OBJECT_LC;
  }

  private static void addAttributeValue(LinkedHashMap<AttributeType, List<Attribute>> attrs, AVA ava)
  {
    AttributeType attrType = ava.getAttributeType();
    ByteString attrValue = ava.getAttributeValue();
    List<Attribute> attrList = attrs.get(attrType);
    if (attrList != null && !attrList.isEmpty())
    {
      AttributeBuilder builder = new AttributeBuilder(attrList.get(0));
      builder.add(attrValue);
      attrList.set(0, builder.toAttribute());
    }
    else
    {
      AttributeBuilder builder = new AttributeBuilder(attrType, ava.getAttributeName());
      builder.add(attrValue);
      attrs.put(attrType, builder.toAttributeList());
    }
  }

  /**
   * Retrieves a user-friendly string that indicates the length of time (in
   * days, hours, minutes, and seconds) in the specified number of seconds.
   *
   * @param  numSeconds  The number of seconds to be converted to a more
   *                     user-friendly value.
   *
   * @return  The user-friendly representation of the specified number of
   *          seconds.
   */
  public static LocalizableMessage secondsToTimeString(long numSeconds)
  {
    if (numSeconds < 60)
    {
      // We can express it in seconds.
      return INFO_TIME_IN_SECONDS.get(numSeconds);
    }
    else if (numSeconds < 3600)
    {
      // We can express it in minutes and seconds.
      long m = numSeconds / 60;
      long s = numSeconds % 60;
      return INFO_TIME_IN_MINUTES_SECONDS.get(m, s);
    }
    else if (numSeconds < 86400)
    {
      // We can express it in hours, minutes, and seconds.
      long h = numSeconds / 3600;
      long m = (numSeconds % 3600) / 60;
      long s = numSeconds % 3600 % 60;
      return INFO_TIME_IN_HOURS_MINUTES_SECONDS.get(h, m, s);
    }
    else
    {
      // We can express it in days, hours, minutes, and seconds.
      long d = numSeconds / 86400;
      long h = (numSeconds % 86400) / 3600;
      long m = (numSeconds % 86400 % 3600) / 60;
      long s = numSeconds % 86400 % 3600 % 60;
      return INFO_TIME_IN_DAYS_HOURS_MINUTES_SECONDS.get(d, h, m, s);
    }
  }

  /**
   * Checks that no more that one of a set of arguments is present.  This
   * utility should be used after argument parser has parsed a set of
   * arguments.
   *
   * @param  args to test for the presence of more than one
   * @throws ArgumentException if more than one of {@code args} is
   *         present and containing an error message identifying the
   *         arguments in violation
   */
  public static void checkOnlyOneArgPresent(Argument... args)
    throws ArgumentException
  {
    if (args != null) {
      for (Argument arg : args) {
        for (Argument otherArg : args) {
          if (arg != otherArg && arg.isPresent() && otherArg.isPresent()) {
            throw new ArgumentException(
                    ToolMessages.ERR_INCOMPATIBLE_ARGUMENTS.get(arg.getLongIdentifier(), otherArg.getLongIdentifier()));
          }
        }
      }
    }
  }

  /**
   * Converts a string representing a time in "yyyyMMddHHmmss.SSS'Z'" or
   * "yyyyMMddHHmmss" to a {@code Date}.
   *
   * @param timeStr string formatted appropriately
   * @return Date object; null if {@code timeStr} is null
   * @throws ParseException if there was a problem converting the string to
   *         a {@code Date}.
   */
  public static Date parseDateTimeString(String timeStr) throws ParseException
  {
    Date dateTime = null;
    if (timeStr != null)
    {
      if (timeStr.endsWith("Z"))
      {
        try
        {
          SimpleDateFormat dateFormat =
            new SimpleDateFormat(DATE_FORMAT_GENERALIZED_TIME);
          dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
          dateFormat.setLenient(true);
          dateTime = dateFormat.parse(timeStr);
        }
        catch (ParseException pe)
        {
          // Best effort: try with GMT time.
          SimpleDateFormat dateFormat =
            new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
          dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
          dateFormat.setLenient(true);
          dateTime = dateFormat.parse(timeStr);
        }
      }
      else
      {
        SimpleDateFormat dateFormat =
            new SimpleDateFormat(DATE_FORMAT_COMPACT_LOCAL_TIME);
        dateFormat.setLenient(true);
        dateTime = dateFormat.parse(timeStr);
      }
    }
    return dateTime;
  }

  /**
   * Formats a Date to String representation in "yyyyMMddHHmmss'Z'".
   *
   * @param date to format; null if {@code date} is null
   * @return string representation of the date
   */
  public static String formatDateTimeString(Date date)
  {
    String timeStr = null;
    if (date != null)
    {
      SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      timeStr = dateFormat.format(date);
    }
    return timeStr;
  }

  /**
   * Indicates whether a string represents a syntactically correct email address.
   *
   * @param addr to validate
   * @return boolean where {@code true} indicates that the string is a
   *         syntactically correct email address
   */
  public static boolean isEmailAddress(String addr) {

    // This just does basic syntax checking.  Perhaps we
    // might want to be stricter about this.
    return addr != null && addr.contains("@") && addr.contains(".");

  }

  /**
   * Add all of the superior objectclasses to the specified objectclass
   * map if they don't already exist. Used by add and import-ldif to
   * add missing superior objectclasses to entries that don't have them.
   *
   * @param objectClasses A Map of objectclasses.
   */
  public static void addSuperiorObjectClasses(Map<ObjectClass,
      String> objectClasses) {
      HashSet<ObjectClass> additionalClasses = null;
      for (ObjectClass oc : objectClasses.keySet())
      {
        for(ObjectClass superiorClass : oc.getSuperiorClasses())
        {
          if (! objectClasses.containsKey(superiorClass))
          {
            if (additionalClasses == null)
            {
              additionalClasses = new HashSet<>();
            }

            additionalClasses.add(superiorClass);
          }
        }
      }

      if (additionalClasses != null)
      {
        for (ObjectClass oc : additionalClasses)
        {
          addObjectClassChain(oc, objectClasses);
        }
      }
  }

  private static void addObjectClassChain(ObjectClass objectClass,
      Map<ObjectClass, String> objectClasses)
  {
    if (objectClasses != null){
      if (! objectClasses.containsKey(objectClass))
      {
        objectClasses.put(objectClass, objectClass.getNameOrOID());
      }

      for(ObjectClass superiorClass : objectClass.getSuperiorClasses())
      {
        if (! objectClasses.containsKey(superiorClass))
        {
          addObjectClassChain(superiorClass, objectClasses);
        }
      }
    }
  }


  /**
   * Closes the provided {@link Closeable}'s ignoring any errors which
   * occurred.
   *
   * @param closeables The closeables to be closed, which may be
   *        {@code null}.
   */
  public static void close(Closeable... closeables)
  {
    if (closeables == null)
    {
      return;
    }
    close(Arrays.asList(closeables));
  }

  /**
   * Closes the provided {@link Closeable}'s ignoring any errors which occurred.
   *
   * @param closeables
   *          The closeables to be closed, which may be {@code null}.
   */
  public static void close(Collection<? extends Closeable> closeables)
  {
    if (closeables == null)
    {
      return;
    }
    for (Closeable closeable : closeables)
    {
      if (closeable != null)
      {
        try
        {
          closeable.close();
        }
        catch (IOException ignored)
        {
          logger.traceException(ignored);
        }
      }
    }
  }

  /**
   * Closes the provided {@link InitialContext}'s ignoring any errors which occurred.
   *
   * @param ctxs
   *          The contexts to be closed, which may be {@code null}.
   */
  public static void close(InitialContext... ctxs)
  {
    if (ctxs == null)
    {
      return;
    }
    for (InitialContext ctx : ctxs)
    {
      if (ctx != null)
      {
        try
        {
          ctx.close();
        }
        catch (NamingException ignored)
        {
          // ignore
        }
      }
    }
  }

  /**
   * Calls {@link Thread#sleep(long)}, surrounding it with the mandatory
   * {@code try} / {@code catch(InterruptedException)} block.
   *
   * @param millis
   *          the length of time to sleep in milliseconds
   */
  public static void sleep(long millis)
  {
    try
    {
      Thread.sleep(millis);
    }
    catch (InterruptedException wokenUp)
    {
      // ignore
    }
  }

  /**
   * Test if the provided message corresponds to the provided descriptor.
   *
   * @param msg
   *          The i18n message.
   * @param desc
   *          The message descriptor.
   * @return {@code true} if message corresponds to descriptor
   */
  public static boolean hasDescriptor(LocalizableMessage msg,
      LocalizableMessageDescriptor.Arg0 desc)
  {
    return msg.ordinal() == desc.ordinal()
        && msg.resourceName().equals(desc.resourceName());
  }

  /**
   * Test if the provided message corresponds to the provided descriptor.
   *
   * @param msg
   *          The i18n message.
   * @param desc
   *          The message descriptor.
   * @return {@code true} if message corresponds to descriptor
   */
  public static boolean hasDescriptor(LocalizableMessage msg,
      LocalizableMessageDescriptor.Arg1<?> desc)
  {
    return msg.ordinal() == desc.ordinal()
        && msg.resourceName().equals(desc.resourceName());
  }

  /**
   * Test if the provided message corresponds to the provided descriptor.
   *
   * @param msg
   *          The i18n message.
   * @param desc
   *          The message descriptor.
   * @return {@code true} if message corresponds to descriptor
   */
  public static boolean hasDescriptor(LocalizableMessage msg,
      LocalizableMessageDescriptor.Arg3<?, ?, ?> desc)
  {
    return msg.ordinal() == desc.ordinal()
        && msg.resourceName().equals(desc.resourceName());
  }

  /**
   * Test if the provided message corresponds to the provided descriptor.
   *
   * @param msg
   *          The i18n message.
   * @param desc
   *          The message descriptor.
   * @return {@code true} if message corresponds to descriptor
   */
  public static boolean hasDescriptor(LocalizableMessage msg,
      LocalizableMessageDescriptor.Arg7<?, ?, ?, ?, ?, ?, ?> desc)
  {
    return msg.ordinal() == desc.ordinal()
        && msg.resourceName().equals(desc.resourceName());
  }

  /**
   * Returns an {@link Iterable} returning the passed in {@link Iterator}. THis
   * allows using methods returning Iterators with foreach statements.
   * <p>
   * For example, consider a method with this signature:
   * <p>
   * <code>public Iterator&lt;String&gt; myIteratorMethod();</code>
   * <p>
   * Classical use with for or while loop:
   *
   * <pre>
   * for (Iterator&lt;String&gt; it = myIteratorMethod(); it.hasNext();)
   * {
   *   String s = it.next();
   *   // use it
   * }
   *
   * Iterator&lt;String&gt; it = myIteratorMethod();
   * while(it.hasNext();)
   * {
   *   String s = it.next();
   *   // use it
   * }
   * </pre>
   *
   * Improved use with foreach:
   *
   * <pre>
   * for (String s : StaticUtils.toIterable(myIteratorMethod()))
   * {
   * }
   * </pre>
   *
   * </p>
   *
   * @param <T>
   *          the generic type of the passed in Iterator and for the returned
   *          Iterable.
   * @param iterator
   *          the Iterator that will be returned by the Iterable.
   * @return an Iterable returning the passed in Iterator
   */
  public static <T> Iterable<T> toIterable(final Iterator<T> iterator)
  {
    return new Iterable<T>()
    {
      @Override
      public Iterator<T> iterator()
      {
        return iterator;
      }
    };
  }

  /**
   * Returns true if the version of the server is an OEM one, and therefore doesn't support the JE backend.
   * @return {@code true} if the version of the server is an OEM version and {@code false} otherwise.
   */
  public static boolean isOEMVersion()
  {
    return !isClassAvailable("org.opends.server.backends.jeb.JEBackend");
  }

  /**
   * Returns true if the class is available in the classpath.
   * @param className the string representing the class to check.
   * @return {@code true} if the class is available in the classpath and {@code false} otherwise.
   */
  public static boolean isClassAvailable(final String className)
  {
    try
    {
      Class.forName(className);
      return true;
    }
    catch (Exception e)
    {
      return false;
    }
  }
}

