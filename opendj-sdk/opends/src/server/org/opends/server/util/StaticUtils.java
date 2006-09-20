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
package org.opends.server.util;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.StringTokenizer;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a number of static utility methods that may be used
 * throughout the server.  Note that because of the frequency with which these
 * methods are expected to be used, very little debug logging will be performed
 * to prevent the log from filling up with unimportant calls and to reduce the
 * impact that debugging may have on performance.
 */
public final class StaticUtils
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.util.StaticUtils";



  /**
   * Private constructor to prevent instantiation.
   */
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
    try
    {
      if (s == null)
      {
        return null;
      }


      char c;
      int length = s.length();
      byte[] returnArray = new byte[length];
      for (int i=0; i < length; i++)
      {
        c = s.charAt(i);
        returnArray[i] = (byte) (c & 0x0000007F);
        if (c != returnArray[i])
        {
          return s.getBytes("UTF-8");
        }
      }

      return returnArray;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getBytes", e);

      try
      {
        return s.getBytes("UTF-8");
      }
      catch (Exception e2)
      {
        assert debugException(CLASS_NAME, "getBytes", e2);

        return s.getBytes();
      }
    }
  }



  /**
   * Construct a byte array containing the UTF-8 encoding of the
   * provided <code>char</code> array.
   *
   * @param chars
   *          The character array to convert to a UTF-8 byte array.
   * @return Returns a byte array containing the UTF-8 encoding of the
   *         provided <code>char</code> array.
   */
  public static byte[] getBytes(char[] chars)
  {
    return getBytes(new String(chars));
  }



  /**
   * Retrieves a string representation of the provided byte in hexadecimal.
   *
   * @param  b  The byte for which to retrieve the hexadecimal string
   *            representation.
   *
   * @return  The string representation of the provided byte in hexadecimal.
   */
  public static String byteToHex(byte b)
  {
    switch (b & 0xFF)
    {
      case 0x00:  return "00";
      case 0x01:  return "01";
      case 0x02:  return "02";
      case 0x03:  return "03";
      case 0x04:  return "04";
      case 0x05:  return "05";
      case 0x06:  return "06";
      case 0x07:  return "07";
      case 0x08:  return "08";
      case 0x09:  return "09";
      case 0x0A:  return "0A";
      case 0x0B:  return "0B";
      case 0x0C:  return "0C";
      case 0x0D:  return "0D";
      case 0x0E:  return "0E";
      case 0x0F:  return "0F";
      case 0x10:  return "10";
      case 0x11:  return "11";
      case 0x12:  return "12";
      case 0x13:  return "13";
      case 0x14:  return "14";
      case 0x15:  return "15";
      case 0x16:  return "16";
      case 0x17:  return "17";
      case 0x18:  return "18";
      case 0x19:  return "19";
      case 0x1A:  return "1A";
      case 0x1B:  return "1B";
      case 0x1C:  return "1C";
      case 0x1D:  return "1D";
      case 0x1E:  return "1E";
      case 0x1F:  return "1F";
      case 0x20:  return "20";
      case 0x21:  return "21";
      case 0x22:  return "22";
      case 0x23:  return "23";
      case 0x24:  return "24";
      case 0x25:  return "25";
      case 0x26:  return "26";
      case 0x27:  return "27";
      case 0x28:  return "28";
      case 0x29:  return "29";
      case 0x2A:  return "2A";
      case 0x2B:  return "2B";
      case 0x2C:  return "2C";
      case 0x2D:  return "2D";
      case 0x2E:  return "2E";
      case 0x2F:  return "2F";
      case 0x30:  return "30";
      case 0x31:  return "31";
      case 0x32:  return "32";
      case 0x33:  return "33";
      case 0x34:  return "34";
      case 0x35:  return "35";
      case 0x36:  return "36";
      case 0x37:  return "37";
      case 0x38:  return "38";
      case 0x39:  return "39";
      case 0x3A:  return "3A";
      case 0x3B:  return "3B";
      case 0x3C:  return "3C";
      case 0x3D:  return "3D";
      case 0x3E:  return "3E";
      case 0x3F:  return "3F";
      case 0x40:  return "40";
      case 0x41:  return "41";
      case 0x42:  return "42";
      case 0x43:  return "43";
      case 0x44:  return "44";
      case 0x45:  return "45";
      case 0x46:  return "46";
      case 0x47:  return "47";
      case 0x48:  return "48";
      case 0x49:  return "49";
      case 0x4A:  return "4A";
      case 0x4B:  return "4B";
      case 0x4C:  return "4C";
      case 0x4D:  return "4D";
      case 0x4E:  return "4E";
      case 0x4F:  return "4F";
      case 0x50:  return "50";
      case 0x51:  return "51";
      case 0x52:  return "52";
      case 0x53:  return "53";
      case 0x54:  return "54";
      case 0x55:  return "55";
      case 0x56:  return "56";
      case 0x57:  return "57";
      case 0x58:  return "58";
      case 0x59:  return "59";
      case 0x5A:  return "5A";
      case 0x5B:  return "5B";
      case 0x5C:  return "5C";
      case 0x5D:  return "5D";
      case 0x5E:  return "5E";
      case 0x5F:  return "5F";
      case 0x60:  return "60";
      case 0x61:  return "61";
      case 0x62:  return "62";
      case 0x63:  return "63";
      case 0x64:  return "64";
      case 0x65:  return "65";
      case 0x66:  return "66";
      case 0x67:  return "67";
      case 0x68:  return "68";
      case 0x69:  return "69";
      case 0x6A:  return "6A";
      case 0x6B:  return "6B";
      case 0x6C:  return "6C";
      case 0x6D:  return "6D";
      case 0x6E:  return "6E";
      case 0x6F:  return "6F";
      case 0x70:  return "70";
      case 0x71:  return "71";
      case 0x72:  return "72";
      case 0x73:  return "73";
      case 0x74:  return "74";
      case 0x75:  return "75";
      case 0x76:  return "76";
      case 0x77:  return "77";
      case 0x78:  return "78";
      case 0x79:  return "79";
      case 0x7A:  return "7A";
      case 0x7B:  return "7B";
      case 0x7C:  return "7C";
      case 0x7D:  return "7D";
      case 0x7E:  return "7E";
      case 0x7F:  return "7F";
      case 0x80:  return "80";
      case 0x81:  return "81";
      case 0x82:  return "82";
      case 0x83:  return "83";
      case 0x84:  return "84";
      case 0x85:  return "85";
      case 0x86:  return "86";
      case 0x87:  return "87";
      case 0x88:  return "88";
      case 0x89:  return "89";
      case 0x8A:  return "8A";
      case 0x8B:  return "8B";
      case 0x8C:  return "8C";
      case 0x8D:  return "8D";
      case 0x8E:  return "8E";
      case 0x8F:  return "8F";
      case 0x90:  return "90";
      case 0x91:  return "91";
      case 0x92:  return "92";
      case 0x93:  return "93";
      case 0x94:  return "94";
      case 0x95:  return "95";
      case 0x96:  return "96";
      case 0x97:  return "97";
      case 0x98:  return "98";
      case 0x99:  return "99";
      case 0x9A:  return "9A";
      case 0x9B:  return "9B";
      case 0x9C:  return "9C";
      case 0x9D:  return "9D";
      case 0x9E:  return "9E";
      case 0x9F:  return "9F";
      case 0xA0:  return "A0";
      case 0xA1:  return "A1";
      case 0xA2:  return "A2";
      case 0xA3:  return "A3";
      case 0xA4:  return "A4";
      case 0xA5:  return "A5";
      case 0xA6:  return "A6";
      case 0xA7:  return "A7";
      case 0xA8:  return "A8";
      case 0xA9:  return "A9";
      case 0xAA:  return "AA";
      case 0xAB:  return "AB";
      case 0xAC:  return "AC";
      case 0xAD:  return "AD";
      case 0xAE:  return "AE";
      case 0xAF:  return "AF";
      case 0xB0:  return "B0";
      case 0xB1:  return "B1";
      case 0xB2:  return "B2";
      case 0xB3:  return "B3";
      case 0xB4:  return "B4";
      case 0xB5:  return "B5";
      case 0xB6:  return "B6";
      case 0xB7:  return "B7";
      case 0xB8:  return "B8";
      case 0xB9:  return "B9";
      case 0xBA:  return "BA";
      case 0xBB:  return "BB";
      case 0xBC:  return "BC";
      case 0xBD:  return "BD";
      case 0xBE:  return "BE";
      case 0xBF:  return "BF";
      case 0xC0:  return "C0";
      case 0xC1:  return "C1";
      case 0xC2:  return "C2";
      case 0xC3:  return "C3";
      case 0xC4:  return "C4";
      case 0xC5:  return "C5";
      case 0xC6:  return "C6";
      case 0xC7:  return "C7";
      case 0xC8:  return "C8";
      case 0xC9:  return "C9";
      case 0xCA:  return "CA";
      case 0xCB:  return "CB";
      case 0xCC:  return "CC";
      case 0xCD:  return "CD";
      case 0xCE:  return "CE";
      case 0xCF:  return "CF";
      case 0xD0:  return "D0";
      case 0xD1:  return "D1";
      case 0xD2:  return "D2";
      case 0xD3:  return "D3";
      case 0xD4:  return "D4";
      case 0xD5:  return "D5";
      case 0xD6:  return "D6";
      case 0xD7:  return "D7";
      case 0xD8:  return "D8";
      case 0xD9:  return "D9";
      case 0xDA:  return "DA";
      case 0xDB:  return "DB";
      case 0xDC:  return "DC";
      case 0xDD:  return "DD";
      case 0xDE:  return "DE";
      case 0xDF:  return "DF";
      case 0xE0:  return "E0";
      case 0xE1:  return "E1";
      case 0xE2:  return "E2";
      case 0xE3:  return "E3";
      case 0xE4:  return "E4";
      case 0xE5:  return "E5";
      case 0xE6:  return "E6";
      case 0xE7:  return "E7";
      case 0xE8:  return "E8";
      case 0xE9:  return "E9";
      case 0xEA:  return "EA";
      case 0xEB:  return "EB";
      case 0xEC:  return "EC";
      case 0xED:  return "ED";
      case 0xEE:  return "EE";
      case 0xEF:  return "EF";
      case 0xF0:  return "F0";
      case 0xF1:  return "F1";
      case 0xF2:  return "F2";
      case 0xF3:  return "F3";
      case 0xF4:  return "F4";
      case 0xF5:  return "F5";
      case 0xF6:  return "F6";
      case 0xF7:  return "F7";
      case 0xF8:  return "F8";
      case 0xF9:  return "F9";
      case 0xFA:  return "FA";
      case 0xFB:  return "FB";
      case 0xFC:  return "FC";
      case 0xFD:  return "FD";
      case 0xFE:  return "FE";
      case 0xFF:  return "FF";
      default:    return "??";
    }
  }



  /**
   * Retrieves a string representation of the provided byte in hexadecimal.
   *
   * @param  b  The byte for which to retrieve the hexadecimal string
   *            representation.
   *
   * @return  The string representation of the provided byte in hexadecimal
   *          using lowercase characters.
   */
  public static String byteToLowerHex(byte b)
  {
    switch (b & 0xFF)
    {
      case 0x00:  return "00";
      case 0x01:  return "01";
      case 0x02:  return "02";
      case 0x03:  return "03";
      case 0x04:  return "04";
      case 0x05:  return "05";
      case 0x06:  return "06";
      case 0x07:  return "07";
      case 0x08:  return "08";
      case 0x09:  return "09";
      case 0x0A:  return "0a";
      case 0x0B:  return "0b";
      case 0x0C:  return "0c";
      case 0x0D:  return "0d";
      case 0x0E:  return "0e";
      case 0x0F:  return "0f";
      case 0x10:  return "10";
      case 0x11:  return "11";
      case 0x12:  return "12";
      case 0x13:  return "13";
      case 0x14:  return "14";
      case 0x15:  return "15";
      case 0x16:  return "16";
      case 0x17:  return "17";
      case 0x18:  return "18";
      case 0x19:  return "19";
      case 0x1A:  return "1a";
      case 0x1B:  return "1b";
      case 0x1C:  return "1c";
      case 0x1D:  return "1d";
      case 0x1E:  return "1e";
      case 0x1F:  return "1f";
      case 0x20:  return "20";
      case 0x21:  return "21";
      case 0x22:  return "22";
      case 0x23:  return "23";
      case 0x24:  return "24";
      case 0x25:  return "25";
      case 0x26:  return "26";
      case 0x27:  return "27";
      case 0x28:  return "28";
      case 0x29:  return "29";
      case 0x2A:  return "2a";
      case 0x2B:  return "2b";
      case 0x2C:  return "2c";
      case 0x2D:  return "2d";
      case 0x2E:  return "2e";
      case 0x2F:  return "2f";
      case 0x30:  return "30";
      case 0x31:  return "31";
      case 0x32:  return "32";
      case 0x33:  return "33";
      case 0x34:  return "34";
      case 0x35:  return "35";
      case 0x36:  return "36";
      case 0x37:  return "37";
      case 0x38:  return "38";
      case 0x39:  return "39";
      case 0x3A:  return "3a";
      case 0x3B:  return "3b";
      case 0x3C:  return "3c";
      case 0x3D:  return "3d";
      case 0x3E:  return "3e";
      case 0x3F:  return "3f";
      case 0x40:  return "40";
      case 0x41:  return "41";
      case 0x42:  return "42";
      case 0x43:  return "43";
      case 0x44:  return "44";
      case 0x45:  return "45";
      case 0x46:  return "46";
      case 0x47:  return "47";
      case 0x48:  return "48";
      case 0x49:  return "49";
      case 0x4A:  return "4a";
      case 0x4B:  return "4b";
      case 0x4C:  return "4c";
      case 0x4D:  return "4d";
      case 0x4E:  return "4e";
      case 0x4F:  return "4f";
      case 0x50:  return "50";
      case 0x51:  return "51";
      case 0x52:  return "52";
      case 0x53:  return "53";
      case 0x54:  return "54";
      case 0x55:  return "55";
      case 0x56:  return "56";
      case 0x57:  return "57";
      case 0x58:  return "58";
      case 0x59:  return "59";
      case 0x5A:  return "5a";
      case 0x5B:  return "5b";
      case 0x5C:  return "5c";
      case 0x5D:  return "5d";
      case 0x5E:  return "5e";
      case 0x5F:  return "5f";
      case 0x60:  return "60";
      case 0x61:  return "61";
      case 0x62:  return "62";
      case 0x63:  return "63";
      case 0x64:  return "64";
      case 0x65:  return "65";
      case 0x66:  return "66";
      case 0x67:  return "67";
      case 0x68:  return "68";
      case 0x69:  return "69";
      case 0x6A:  return "6a";
      case 0x6B:  return "6b";
      case 0x6C:  return "6c";
      case 0x6D:  return "6d";
      case 0x6E:  return "6e";
      case 0x6F:  return "6f";
      case 0x70:  return "70";
      case 0x71:  return "71";
      case 0x72:  return "72";
      case 0x73:  return "73";
      case 0x74:  return "74";
      case 0x75:  return "75";
      case 0x76:  return "76";
      case 0x77:  return "77";
      case 0x78:  return "78";
      case 0x79:  return "79";
      case 0x7A:  return "7a";
      case 0x7B:  return "7b";
      case 0x7C:  return "7c";
      case 0x7D:  return "7d";
      case 0x7E:  return "7e";
      case 0x7F:  return "7f";
      case 0x80:  return "80";
      case 0x81:  return "81";
      case 0x82:  return "82";
      case 0x83:  return "83";
      case 0x84:  return "84";
      case 0x85:  return "85";
      case 0x86:  return "86";
      case 0x87:  return "87";
      case 0x88:  return "88";
      case 0x89:  return "89";
      case 0x8A:  return "8a";
      case 0x8B:  return "8b";
      case 0x8C:  return "8c";
      case 0x8D:  return "8d";
      case 0x8E:  return "8e";
      case 0x8F:  return "8f";
      case 0x90:  return "90";
      case 0x91:  return "91";
      case 0x92:  return "92";
      case 0x93:  return "93";
      case 0x94:  return "94";
      case 0x95:  return "95";
      case 0x96:  return "96";
      case 0x97:  return "97";
      case 0x98:  return "98";
      case 0x99:  return "99";
      case 0x9A:  return "9a";
      case 0x9B:  return "9b";
      case 0x9C:  return "9c";
      case 0x9D:  return "9d";
      case 0x9E:  return "9e";
      case 0x9F:  return "9f";
      case 0xA0:  return "a0";
      case 0xA1:  return "a1";
      case 0xA2:  return "a2";
      case 0xA3:  return "a3";
      case 0xA4:  return "a4";
      case 0xA5:  return "a5";
      case 0xA6:  return "a6";
      case 0xA7:  return "a7";
      case 0xA8:  return "a8";
      case 0xA9:  return "a9";
      case 0xAA:  return "aa";
      case 0xAB:  return "ab";
      case 0xAC:  return "ac";
      case 0xAD:  return "ad";
      case 0xAE:  return "ae";
      case 0xAF:  return "af";
      case 0xB0:  return "b0";
      case 0xB1:  return "b1";
      case 0xB2:  return "b2";
      case 0xB3:  return "b3";
      case 0xB4:  return "b4";
      case 0xB5:  return "b5";
      case 0xB6:  return "b6";
      case 0xB7:  return "b7";
      case 0xB8:  return "b8";
      case 0xB9:  return "b9";
      case 0xBA:  return "ba";
      case 0xBB:  return "bb";
      case 0xBC:  return "bc";
      case 0xBD:  return "bd";
      case 0xBE:  return "be";
      case 0xBF:  return "bf";
      case 0xC0:  return "c0";
      case 0xC1:  return "c1";
      case 0xC2:  return "c2";
      case 0xC3:  return "c3";
      case 0xC4:  return "c4";
      case 0xC5:  return "c5";
      case 0xC6:  return "c6";
      case 0xC7:  return "c7";
      case 0xC8:  return "c8";
      case 0xC9:  return "c9";
      case 0xCA:  return "ca";
      case 0xCB:  return "cb";
      case 0xCC:  return "cc";
      case 0xCD:  return "cd";
      case 0xCE:  return "ce";
      case 0xCF:  return "cf";
      case 0xD0:  return "d0";
      case 0xD1:  return "d1";
      case 0xD2:  return "d2";
      case 0xD3:  return "d3";
      case 0xD4:  return "d4";
      case 0xD5:  return "d5";
      case 0xD6:  return "d6";
      case 0xD7:  return "d7";
      case 0xD8:  return "d8";
      case 0xD9:  return "d9";
      case 0xDA:  return "da";
      case 0xDB:  return "db";
      case 0xDC:  return "dc";
      case 0xDD:  return "dd";
      case 0xDE:  return "de";
      case 0xDF:  return "df";
      case 0xE0:  return "e0";
      case 0xE1:  return "e1";
      case 0xE2:  return "e2";
      case 0xE3:  return "e3";
      case 0xE4:  return "e4";
      case 0xE5:  return "e5";
      case 0xE6:  return "e6";
      case 0xE7:  return "e7";
      case 0xE8:  return "e8";
      case 0xE9:  return "e9";
      case 0xEA:  return "ea";
      case 0xEB:  return "eb";
      case 0xEC:  return "ec";
      case 0xED:  return "ed";
      case 0xEE:  return "ee";
      case 0xEF:  return "ef";
      case 0xF0:  return "f0";
      case 0xF1:  return "f1";
      case 0xF2:  return "f2";
      case 0xF3:  return "f3";
      case 0xF4:  return "f4";
      case 0xF5:  return "f5";
      case 0xF6:  return "f6";
      case 0xF7:  return "f7";
      case 0xF8:  return "f8";
      case 0xF9:  return "f9";
      case 0xFA:  return "fa";
      case 0xFB:  return "fb";
      case 0xFC:  return "fc";
      case 0xFD:  return "fd";
      case 0xFE:  return "fe";
      case 0xFF:  return "ff";
      default:    return "??";
    }
  }



  /**
   * Retrieves the printable ASCII representation of the provided byte.
   *
   * @param  b  The byte for which to retrieve the printable ASCII
   *            representation.
   *
   * @return  The printable ASCII representation of the provided byte, or a
   *          space if the provided byte does not have  printable ASCII
   *          representation.
   */
  public static char byteToASCII(byte b)
  {
    if ((b >= 32) && (b <= 126))
    {
      return (char) b;
    }

    return ' ';
  }



  /**
   * Retrieves a string representation of the contents of the provided byte
   * array using hexadecimal characters and a space between each byte.
   *
   * @param  b  The byte array containing the data.
   *
   * @return  A string representation of the contents of the provided byte
   *          array using hexadecimal characters.
   */
  public static String bytesToHex(byte[] b)
  {
    if ((b == null) || (b.length == 0))
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
    while ((length - pos) >= 16)
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


    int remaining = (length - pos);
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



  /**
   * Appends a string representation of the remaining unread data in the
   * provided byte buffer to the given buffer using the specified indent.
   * The data will be formatted with sixteen hex bytes in a row followed by
   * the ASCII representation, then wrapping to a new line as necessary.
   * The state of the byte buffer is not changed.
   *
   * @param  buffer  The buffer to which the information is to be appended.
   * @param  b       The byte buffer containing the data to write.
   *                 The data from the position to the limit is written.
   * @param  indent  The number of spaces to indent the output.
   */
  public static void byteArrayToHexPlusAscii(StringBuilder buffer, ByteBuffer b,
                                             int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }


    int position = b.position();
    int limit    = b.limit();
    int length   = limit - position;
    int pos      = 0;
    while ((length - pos) >= 16)
    {
      StringBuilder asciiBuf = new StringBuilder(17);

      byte currentByte = b.get();
      buffer.append(indentBuf);
      buffer.append(byteToHex(currentByte));
      asciiBuf.append(byteToASCII(currentByte));
      pos++;

      for (int i=1; i < 16; i++, pos++)
      {
        currentByte = b.get();
        buffer.append(' ');
        buffer.append(byteToHex(currentByte));
        asciiBuf.append(byteToASCII(currentByte));

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


    int remaining = (length - pos);
    if (remaining > 0)
    {
      StringBuilder asciiBuf = new StringBuilder(remaining+1);

      byte currentByte = b.get();
      buffer.append(indentBuf);
      buffer.append(byteToHex(currentByte));
      asciiBuf.append(byteToASCII(currentByte));
      pos++;

      for (int i=1; i < 16; i++)
      {
        buffer.append(' ');

        if (i < remaining)
        {
          currentByte = b.get();
          buffer.append(byteToHex(currentByte));
          asciiBuf.append(byteToASCII(currentByte));
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

    b.position(position);
    b.limit(limit);
  }



  /**
   * Retrieves a binary representation of the provided byte.  It will always be
   * a sequence of eight zeros and/or ones.
   *
   * @param  b  The byte for which to retrieve the binary representation.
   *
   * @return  The binary representation for the provided byte.
   */
  public static String byteToBinary(byte b)
  {
    switch (b & 0xFF)
    {
      case 0x00:  return "00000000";
      case 0x01:  return "00000001";
      case 0x02:  return "00000010";
      case 0x03:  return "00000011";
      case 0x04:  return "00000100";
      case 0x05:  return "00000101";
      case 0x06:  return "00000110";
      case 0x07:  return "00000111";
      case 0x08:  return "00001000";
      case 0x09:  return "00001001";
      case 0x0A:  return "00001010";
      case 0x0B:  return "00001011";
      case 0x0C:  return "00001100";
      case 0x0D:  return "00001101";
      case 0x0E:  return "00001110";
      case 0x0F:  return "00001111";
      case 0x10:  return "00010000";
      case 0x11:  return "00010001";
      case 0x12:  return "00010010";
      case 0x13:  return "00010011";
      case 0x14:  return "00010100";
      case 0x15:  return "00010101";
      case 0x16:  return "00010110";
      case 0x17:  return "00010111";
      case 0x18:  return "00011000";
      case 0x19:  return "00011001";
      case 0x1A:  return "00011010";
      case 0x1B:  return "00011011";
      case 0x1C:  return "00011100";
      case 0x1D:  return "00011101";
      case 0x1E:  return "00011110";
      case 0x1F:  return "00011111";
      case 0x20:  return "00100000";
      case 0x21:  return "00100001";
      case 0x22:  return "00100010";
      case 0x23:  return "00100011";
      case 0x24:  return "00100100";
      case 0x25:  return "00100101";
      case 0x26:  return "00100110";
      case 0x27:  return "00100111";
      case 0x28:  return "00101000";
      case 0x29:  return "00101001";
      case 0x2A:  return "00101010";
      case 0x2B:  return "00101011";
      case 0x2C:  return "00101100";
      case 0x2D:  return "00101101";
      case 0x2E:  return "00101110";
      case 0x2F:  return "00101111";
      case 0x30:  return "00110000";
      case 0x31:  return "00110001";
      case 0x32:  return "00110010";
      case 0x33:  return "00110011";
      case 0x34:  return "00110100";
      case 0x35:  return "00110101";
      case 0x36:  return "00110110";
      case 0x37:  return "00110111";
      case 0x38:  return "00111000";
      case 0x39:  return "00111001";
      case 0x3A:  return "00111010";
      case 0x3B:  return "00111011";
      case 0x3C:  return "00111100";
      case 0x3D:  return "00111101";
      case 0x3E:  return "00111110";
      case 0x3F:  return "00111111";
      case 0x40:  return "01000000";
      case 0x41:  return "01000001";
      case 0x42:  return "01000010";
      case 0x43:  return "01000011";
      case 0x44:  return "01000100";
      case 0x45:  return "01000101";
      case 0x46:  return "01000110";
      case 0x47:  return "01000111";
      case 0x48:  return "01001000";
      case 0x49:  return "01001001";
      case 0x4A:  return "01001010";
      case 0x4B:  return "01001011";
      case 0x4C:  return "01001100";
      case 0x4D:  return "01001101";
      case 0x4E:  return "01001110";
      case 0x4F:  return "01001111";
      case 0x50:  return "01010000";
      case 0x51:  return "01010001";
      case 0x52:  return "01010010";
      case 0x53:  return "01010011";
      case 0x54:  return "01010100";
      case 0x55:  return "01010101";
      case 0x56:  return "01010110";
      case 0x57:  return "01010111";
      case 0x58:  return "01011000";
      case 0x59:  return "01011001";
      case 0x5A:  return "01011010";
      case 0x5B:  return "01011011";
      case 0x5C:  return "01011100";
      case 0x5D:  return "01011101";
      case 0x5E:  return "01011110";
      case 0x5F:  return "01011111";
      case 0x60:  return "01100000";
      case 0x61:  return "01100001";
      case 0x62:  return "01100010";
      case 0x63:  return "01100011";
      case 0x64:  return "01100100";
      case 0x65:  return "01100101";
      case 0x66:  return "01100110";
      case 0x67:  return "01100111";
      case 0x68:  return "01101000";
      case 0x69:  return "01101001";
      case 0x6A:  return "01101010";
      case 0x6B:  return "01101011";
      case 0x6C:  return "01101100";
      case 0x6D:  return "01101101";
      case 0x6E:  return "01101110";
      case 0x6F:  return "01101111";
      case 0x70:  return "01110000";
      case 0x71:  return "01110001";
      case 0x72:  return "01110010";
      case 0x73:  return "01110011";
      case 0x74:  return "01110100";
      case 0x75:  return "01110101";
      case 0x76:  return "01110110";
      case 0x77:  return "01110111";
      case 0x78:  return "01111000";
      case 0x79:  return "01111001";
      case 0x7A:  return "01111010";
      case 0x7B:  return "01111011";
      case 0x7C:  return "01111100";
      case 0x7D:  return "01111101";
      case 0x7E:  return "01111110";
      case 0x7F:  return "01111111";
      case 0x80:  return "10000000";
      case 0x81:  return "10000001";
      case 0x82:  return "10000010";
      case 0x83:  return "10000011";
      case 0x84:  return "10000100";
      case 0x85:  return "10000101";
      case 0x86:  return "10000110";
      case 0x87:  return "10000111";
      case 0x88:  return "10001000";
      case 0x89:  return "10001001";
      case 0x8A:  return "10001010";
      case 0x8B:  return "10001011";
      case 0x8C:  return "10001100";
      case 0x8D:  return "10001101";
      case 0x8E:  return "10001110";
      case 0x8F:  return "10001111";
      case 0x90:  return "10010000";
      case 0x91:  return "10010001";
      case 0x92:  return "10010010";
      case 0x93:  return "10010011";
      case 0x94:  return "10010100";
      case 0x95:  return "10010101";
      case 0x96:  return "10010110";
      case 0x97:  return "10010111";
      case 0x98:  return "10011000";
      case 0x99:  return "10011001";
      case 0x9A:  return "10011010";
      case 0x9B:  return "10011011";
      case 0x9C:  return "10011100";
      case 0x9D:  return "10011101";
      case 0x9E:  return "10011110";
      case 0x9F:  return "10011111";
      case 0xA0:  return "10100000";
      case 0xA1:  return "10100001";
      case 0xA2:  return "10100010";
      case 0xA3:  return "10100011";
      case 0xA4:  return "10100100";
      case 0xA5:  return "10100101";
      case 0xA6:  return "10100110";
      case 0xA7:  return "10100111";
      case 0xA8:  return "10101000";
      case 0xA9:  return "10101001";
      case 0xAA:  return "10101010";
      case 0xAB:  return "10101011";
      case 0xAC:  return "10101100";
      case 0xAD:  return "10101101";
      case 0xAE:  return "10101110";
      case 0xAF:  return "10101111";
      case 0xB0:  return "10110000";
      case 0xB1:  return "10110001";
      case 0xB2:  return "10110010";
      case 0xB3:  return "10110011";
      case 0xB4:  return "10110100";
      case 0xB5:  return "10110101";
      case 0xB6:  return "10110110";
      case 0xB7:  return "10110111";
      case 0xB8:  return "10111000";
      case 0xB9:  return "10111001";
      case 0xBA:  return "10111010";
      case 0xBB:  return "10111011";
      case 0xBC:  return "10111100";
      case 0xBD:  return "10111101";
      case 0xBE:  return "10111110";
      case 0xBF:  return "10111111";
      case 0xC0:  return "11000000";
      case 0xC1:  return "11000001";
      case 0xC2:  return "11000010";
      case 0xC3:  return "11000011";
      case 0xC4:  return "11000100";
      case 0xC5:  return "11000101";
      case 0xC6:  return "11000110";
      case 0xC7:  return "11000111";
      case 0xC8:  return "11001000";
      case 0xC9:  return "11001001";
      case 0xCA:  return "11001010";
      case 0xCB:  return "11001011";
      case 0xCC:  return "11001100";
      case 0xCD:  return "11001101";
      case 0xCE:  return "11001110";
      case 0xCF:  return "11001111";
      case 0xD0:  return "11010000";
      case 0xD1:  return "11010001";
      case 0xD2:  return "11010010";
      case 0xD3:  return "11010011";
      case 0xD4:  return "11010100";
      case 0xD5:  return "11010101";
      case 0xD6:  return "11010110";
      case 0xD7:  return "11010111";
      case 0xD8:  return "11011000";
      case 0xD9:  return "11011001";
      case 0xDA:  return "11011010";
      case 0xDB:  return "11011011";
      case 0xDC:  return "11011100";
      case 0xDD:  return "11011101";
      case 0xDE:  return "11011110";
      case 0xDF:  return "11011111";
      case 0xE0:  return "11100000";
      case 0xE1:  return "11100001";
      case 0xE2:  return "11100010";
      case 0xE3:  return "11100011";
      case 0xE4:  return "11100100";
      case 0xE5:  return "11100101";
      case 0xE6:  return "11100110";
      case 0xE7:  return "11100111";
      case 0xE8:  return "11101000";
      case 0xE9:  return "11101001";
      case 0xEA:  return "11101010";
      case 0xEB:  return "11101011";
      case 0xEC:  return "11101100";
      case 0xED:  return "11101101";
      case 0xEE:  return "11101110";
      case 0xEF:  return "11101111";
      case 0xF0:  return "11110000";
      case 0xF1:  return "11110001";
      case 0xF2:  return "11110010";
      case 0xF3:  return "11110011";
      case 0xF4:  return "11110100";
      case 0xF5:  return "11110101";
      case 0xF6:  return "11110110";
      case 0xF7:  return "11110111";
      case 0xF8:  return "11111000";
      case 0xF9:  return "11111001";
      case 0xFA:  return "11111010";
      case 0xFB:  return "11111011";
      case 0xFC:  return "11111100";
      case 0xFD:  return "11111101";
      case 0xFE:  return "11111110";
      case 0xFF:  return "11111111";
      default:    return "????????";
    }
  }



  /**
   * Indicates whether the two array lists are equal.  They will be considered
   * equal if they have the same number of elements, and the corresponding
   * elements between them are equal (in the same order).
   *
   * @param  list1  The first list for which to make the determination.
   * @param  list2  The second list for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the two array lists are equal, or
   *          <CODE>false</CODE> if they are not.
   */
  public static boolean listsAreEqual(List list1, List list2)
  {
    if (list1 == null)
    {
      return (list2 == null);
    }
    else if (list2 == null)
    {
      return false;
    }

    int numElements = list1.size();
    if (numElements != list2.size())
    {
      return false;
    }

    // If either of the lists doesn't support random access, then fall back
    // on their equals methods and go ahead and create some garbage with the
    // iterators.
    if (!(list1 instanceof RandomAccess) ||
        !(list2 instanceof RandomAccess))
    {
      return list1.equals(list2);
    }

    // Otherwise we can just retrieve the elements efficiently via their index.
    for (int i=0; i < numElements; i++)
    {
      Object o1 = list1.get(i);
      Object o2 = list2.get(i);

      if (o1 == null)
      {
        if (o2 != null)
        {
          return false;
        }
      }
      else if (! o1.equals(o2))
      {
        return false;
      }
    }

    return true;
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
    StringBuilder buffer = new StringBuilder();
    stackTraceToSingleLineString(buffer, t);
    return buffer.toString();
  }



  /**
   * Appends a single-line string representation of the provided exception to
   * the given buffer.
   *
   * @param  buffer  The buffer to which the information is to be appended.
   * @param  t       The exception for which to retrieve the stack trace.
   */
  public static void stackTraceToSingleLineString(StringBuilder buffer,
                                                  Throwable t)
  {
    if (t == null)
    {
      return;
    }

    buffer.append(t);

    for (StackTraceElement e : t.getStackTrace())
    {
      buffer.append(" / ");
      buffer.append(e.getFileName());
      buffer.append(":");
      buffer.append(e.getLineNumber());
    }

    while (t.getCause() != null)
    {
      t = t.getCause();

      buffer.append("; caused by ");
      buffer.append(t);

      for (StackTraceElement e : t.getStackTrace())
      {
        buffer.append(" / ");
        buffer.append(e.getFileName());
        buffer.append(":");
        buffer.append(e.getLineNumber());
      }
    }
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
   * Appends a string representation of the stack trace for the provided
   * exception to the given buffer.
   *
   * @param  buffer  The buffer to which the information is to be appended.
   * @param  t       The exception for which to retrieve the stack trace.
   */
  public static void stackTraceToString(StringBuilder buffer, Throwable t)
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
   * <CODE>getBacktrace</CODE> without any arguments if an exception has already
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
   * @return  <CODE>true</CODE> if the provided character represents a numeric
   *          digit, or <CODE>false</CODE> if not.
   */
  public static boolean isDigit(char c)
  {
    switch (c)
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
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether the provided character is an ASCII alphabetic character.
   *
   * @param  c  The character for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided value is an uppercase or
   *          lowercase ASCII alphabetic character, or <CODE>false</CODE> if it
   *          is not.
   */
  public static boolean isAlpha(char c)
  {
    switch (c)
    {
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
        return true;

      case '[':
      case '\\':
      case ']':
      case '^':
      case '_':
      case '`':
        // Making sure all possible cases are present in one contiguous range
        // can result in a performance improvement.
        return false;

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
        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether the provided character is a hexadecimal digit.
   *
   * @param  c  The character for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided character represents a
   *          hexadecimal digit, or <CODE>false</CODE> if not.
   */
  public static boolean isHexDigit(char c)
  {
    switch (c)
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
   * Indicates whether the provided byte represents a hexadecimal digit.
   *
   * @param  b  The byte for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided byte represents a hexadecimal
   *          digit, or <CODE>false</CODE> if not.
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
    if ((hexString == null) || ((length = hexString.length()) == 0))
    {
      return new byte[0];
    }


    if ((length % 2) == 1)
    {
      int    msgID   = MSGID_HEX_DECODE_INVALID_LENGTH;
      String message = getMessage(msgID, hexString);
      throw new ParseException(message, 0);
    }


    int pos = 0;
    int arrayLength = (length / 2);
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
          int    msgID   = MSGID_HEX_DECODE_INVALID_CHARACTER;
          String message = getMessage(msgID, hexString,
                                      hexString.charAt(pos-1));
          throw new ParseException(message, 0);
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
          int    msgID   = MSGID_HEX_DECODE_INVALID_CHARACTER;
          String message = getMessage(msgID, hexString,
                                      hexString.charAt(pos-1));
          throw new ParseException(message, 0);
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
   * @return  <CODE>true</CODE> if the value needs to be base64-encoded if it is
   *          represented in LDIF form, or <CODE>false</CODE> if not.
   */
  public static boolean needsBase64Encoding(byte[] valueBytes)
  {
    assert debugEnter(CLASS_NAME, "needsBase64Encoding",
                      String.valueOf(valueBytes));

    int length;
    if ((valueBytes == null) || ((length = valueBytes.length) == 0))
    {
      return false;
    }


    // If the value starts with a space, colon, or less than, then it needs to
    // be base64-encoded.
    switch (valueBytes[0])
    {
      case 0x20: // Space
      case 0x3A: // Colon
      case 0x3C: // Less-than
        return true;
    }


    // If the value ends with a space, then it needs to be base64-encoded.
    if ((length > 1) && (valueBytes[length-1] == 0x20))
    {
      return true;
    }


    // If the value contains a null, newline, or return character, then it needs
    // to be base64-encoded.
    for (byte b : valueBytes)
    {
      if ((b > 127) || (b < 0))
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
   * @return  <CODE>true</CODE> if the value needs to be base64-encoded if it is
   *          represented in LDIF form, or <CODE>false</CODE> if not.
   */
  public static boolean needsBase64Encoding(String valueString)
  {
    assert debugEnter(CLASS_NAME, "needsBase64Encoding",
                      String.valueOf(valueString));

    int length;
    if ((valueString == null) || ((length = valueString.length()) == 0))
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
    if ((length > 1) && (valueString.charAt(length-1) == ' '))
    {
      return true;
    }


    // If the value contains a null, newline, or return character, then it needs
    // to be base64-encoded.
    for (int i=0; i < length; i++)
    {
      char c = valueString.charAt(i);
      if ((c <= 0) || (c == 0x0A) || (c == 0x0D) || (c > 127))
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
   * @return  <CODE>true</CODE> if the use of the exec method should be allowed,
   *          or <CODE>false</CODE> if it should not be allowed.
   */
  public static boolean mayUseExec()
  {
    assert debugEnter(CLASS_NAME, "mayUseExec");

    String s = System.getProperty(PROPERTY_DISABLE_EXEC);
    if (s == null)
    {
      return true;
    }

    s = toLowerCase(s);
    return (s.equals("false") || s.equals("off") || s.equals("no") ||
            s.equals("0"));
  }



  /**
   * Executes the specified command on the system and captures its output.  This
   * will not return until the specified process has completed.
   *
   * @param  command           The command to execute.
   * @param  args              The set of arguments to provide to the command.
   * @param  workingDirectory  The working directory to use for the command, or
   *                           <CODE>null</CODE> if the default directory
   *                           should be used.
   * @param  environment       The set of environment variables that should be
   *                           set when executing the command, or
   *                           <CODE>null</CODE> if none are needed.
   * @param  output            The output generated by the command while it was
   *                           running.  This will include both standard
   *                           output and standard error.  It may be
   *                           <CODE>null</CODE> if the output does not need to
   *                           be captured.
   *
   * @return  The exit code for the command.
   *
   * @throws  IOException  If an I/O problem occurs while trying to execute the
   *                       command.
   *
   * @throws  SecurityException  If the security policy will not allow the
   *                             command to be executed.
   */
  public static int exec(String command, String[] args, File workingDirectory,
                         Map<String,String> environment, List<String> output)
         throws IOException, SecurityException
  {
    assert debugEnter(CLASS_NAME, "exec", String.valueOf(command),
                      String.valueOf(args));


    // See whether we'll allow the use of exec on this system.  If not, then
    // throw an exception.
    if (! mayUseExec())
    {
      int    msgID   = MSGID_EXEC_DISABLED;
      String message = getMessage(msgID, String.valueOf(command));
      throw new SecurityException(message);
    }


    ArrayList<String> commandAndArgs = new ArrayList<String>();
    commandAndArgs.add(command);
    if ((args != null) && (args.length > 0))
    {
      for (String arg : args)
      {
        commandAndArgs.add(arg);
      }
    }

    ProcessBuilder processBuilder = new ProcessBuilder(commandAndArgs);
    processBuilder.redirectErrorStream(true);

    if ((workingDirectory != null) && workingDirectory.isDirectory())
    {
      processBuilder.directory(workingDirectory);
    }

    if ((environment != null) && (! environment.isEmpty()))
    {
      processBuilder.environment().putAll(environment);
    }

    Process process = processBuilder.start();

    if (output == null)
    {
      try
      {
        return process.waitFor();
      }
      catch (InterruptedException ie)
      {
        assert debugException(CLASS_NAME, "exec", ie);

        // If this happens, then we have no choice but to forcefully terminate
        // the process.
        try
        {
          process.destroy();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "exec", e);
        }

        return process.exitValue();
      }
    }
    else
    {
      BufferedReader reader =
           new BufferedReader(new InputStreamReader(
                                       process.getInputStream()));

      try
      {
        while (true)
        {
          String line = reader.readLine();
          if (line == null)
          {
            break;
          }
          else
          {
            output.add(line);
          }
        }
      }
      finally
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "exec", e);
        }
      }

      try
      {
        return process.waitFor();
      }
      catch (InterruptedException ie)
      {
        assert debugException(CLASS_NAME, "exec", ie);

        // If this happens, then we have no choice but to forcefully terminate
        // the process.
        try
        {
          process.destroy();
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "exec", e);
        }

        return process.exitValue();
      }
    }
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
   * @return  <CODE>true</CODE> if the provided string contains a valid name or
   *          OID for a schema element, or <CODE>false</CODE> if it does not.
   */
  public static boolean isValidSchemaElement(String element, int startPos,
                                             int endPos,
                                             StringBuilder invalidReason)
  {
    assert debugEnter(CLASS_NAME, "isValidSchemaElement",
                      String.valueOf(element), String.valueOf(startPos),
                      String.valueOf(endPos), "java.lang.StringBuilder");


    if ((element == null) || (startPos >= endPos))
    {
      int msgID = MSGID_SCHEMANAME_EMPTY_VALUE;
      invalidReason.append(getMessage(msgID));
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
        if (! (isAlpha(c) || isDigit(c) || (c == '-') ||
               ((c == '_') && DirectoryServer.allowAttributeNameExceptions())))
        {
          // This is an illegal character for an attribute name.
          int msgID = MSGID_SCHEMANAME_ILLEGAL_CHAR;
          invalidReason.append(getMessage(msgID, element, c, i));
          return false;
        }
      }
    }
    else if (isDigit(c))
    {
      // This should indicate an OID, but it may also be a name if name
      // exceptions are enabled.  Since we don't know for sure, we'll just
      // hold off until we know for sure.
      boolean isKnown    = (! DirectoryServer.allowAttributeNameExceptions());
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
                int msgID = MSGID_SCHEMANAME_CONSECUTIVE_PERIODS;
                invalidReason.append(getMessage(msgID, element, i));
                return false;
              }
              else
              {
                lastWasDot = true;
              }
            }
            else
            {
              // This is an illegal character.
              int msgID = MSGID_SCHEMANAME_ILLEGAL_CHAR;
              invalidReason.append(getMessage(msgID, element, c, i));
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

          if (isAlpha(c) || (c == '-') || (c == '_'))
          {
            if (isKnown)
            {
              if (isNumeric)
              {
                // This is an illegal character for a numeric OID.
                int msgID = MSGID_SCHEMANAME_ILLEGAL_CHAR;
                invalidReason.append(getMessage(msgID, element, c, i));
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
            int msgID = MSGID_SCHEMANAME_ILLEGAL_CHAR;
            invalidReason.append(getMessage(msgID, element, c, i));
            return false;
          }
        }
      }
    }
    else
    {
      // This is an illegal character.
      int msgID = MSGID_SCHEMANAME_ILLEGAL_CHAR;
      invalidReason.append(getMessage(msgID, element, c, startPos));
      return false;
    }


    // If we've gotten here, then the value is fine.
    return true;
  }



  /**
   * Retrieves a lowercase representation of the given string.  This
   * implementation presumes that the provided string will contain only ASCII
   * characters and is optimized for that case.  However, if a non-ASCII
   * character is encountered it will fall back on a more expensive algorithm
   * that will work properly for non-ASCII characters.
   *
   * @param  s  The string for which to obtain the lowercase representation.
   *
   * @return  The lowercase representation of the given string.
   */
  public static String toLowerCase(String s)
  {
    assert debugEnter(CLASS_NAME, "toLowerCase", String.valueOf(s));

    if (s == null)
    {
      return null;
    }

    StringBuilder buffer = new StringBuilder(s.length());
    toLowerCase(s, buffer);
    return buffer.toString();
  }



  /**
   * Appends a lowercase representation of the given string to the provided
   * buffer.  This implementation presumes that the provided string will contain
   * only ASCII characters and is optimized for that case.  However, if a
   * non-ASCII character is encountered it will fall back on a more expensive
   * algorithm that will work properly for non-ASCII characters.
   *
   * @param  s       The string for which to obtain the lowercase
   *                 representation.
   * @param  buffer  The buffer to which the lowercase form of the string should
   *                 be appended.
   */
  public static void toLowerCase(String s, StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toLowerCase", String.valueOf(s),
                      "java.lang.StringBuilder");

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
        buffer.append(s.substring(i).toLowerCase());
        return;
      }

      switch (c)
      {
        case 'A':
          buffer.append('a');
          break;
        case 'B':
          buffer.append('b');
          break;
        case 'C':
          buffer.append('c');
          break;
        case 'D':
          buffer.append('d');
          break;
        case 'E':
          buffer.append('e');
          break;
        case 'F':
          buffer.append('f');
          break;
        case 'G':
          buffer.append('g');
          break;
        case 'H':
          buffer.append('h');
          break;
        case 'I':
          buffer.append('i');
          break;
        case 'J':
          buffer.append('j');
          break;
        case 'K':
          buffer.append('k');
          break;
        case 'L':
          buffer.append('l');
          break;
        case 'M':
          buffer.append('m');
          break;
        case 'N':
          buffer.append('n');
          break;
        case 'O':
          buffer.append('o');
          break;
        case 'P':
          buffer.append('p');
          break;
        case 'Q':
          buffer.append('q');
          break;
        case 'R':
          buffer.append('r');
          break;
        case 'S':
          buffer.append('s');
          break;
        case 'T':
          buffer.append('t');
          break;
        case 'U':
          buffer.append('u');
          break;
        case 'V':
          buffer.append('v');
          break;
        case 'W':
          buffer.append('w');
          break;
        case 'X':
          buffer.append('x');
          break;
        case 'Y':
          buffer.append('y');
          break;
        case 'Z':
          buffer.append('z');
          break;
        default:
          buffer.append(c);
      }
    }
  }



  /**
   * Appends a lowercase string representation of the contents of the given byte
   * array to the provided buffer, optionally trimming leading and trailing
   * spaces.  This implementation presumes that the provided string will contain
   * only ASCII characters and is optimized for that case.  However, if a
   * non-ASCII character is encountered it will fall back on a more expensive
   * algorithm that will work properly for non-ASCII characters.
   *
   * @param  b       The byte array for which to obtain the lowercase string
   *                 representation.
   * @param  buffer  The buffer to which the lowercase form of the string should
   *                 be appended.
   * @param  trim    Indicates whether leading and trailing spaces should be
   *                 omitted from the string representation.
   */
  public static void toLowerCase(byte[] b, StringBuilder buffer, boolean trim)
  {
    assert debugEnter(CLASS_NAME, "toLowerCase", String.valueOf(b),
                      "java.lang.StringBuilder", String.valueOf(trim));

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
          buffer.append(new String(b, i, (length-i), "UTF-8").toLowerCase());
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "toLowerCase", e);
          buffer.append(new String(b, i, (length-i)).toLowerCase());
        }
        break;
      }

      int bufferLength = buffer.length();
      switch (b[i])
      {
        case ' ':
          // If we don't care about trimming, then we can always append the
          // space.  Otherwise, only do so if there are other characters in the
          // value.
          if (trim && (bufferLength == 0))
          {
            break;
          }

          buffer.append(' ');
          break;
        case 'A':
          buffer.append('a');
          break;
        case 'B':
          buffer.append('b');
          break;
        case 'C':
          buffer.append('c');
          break;
        case 'D':
          buffer.append('d');
          break;
        case 'E':
          buffer.append('e');
          break;
        case 'F':
          buffer.append('f');
          break;
        case 'G':
          buffer.append('g');
          break;
        case 'H':
          buffer.append('h');
          break;
        case 'I':
          buffer.append('i');
          break;
        case 'J':
          buffer.append('j');
          break;
        case 'K':
          buffer.append('k');
          break;
        case 'L':
          buffer.append('l');
          break;
        case 'M':
          buffer.append('m');
          break;
        case 'N':
          buffer.append('n');
          break;
        case 'O':
          buffer.append('o');
          break;
        case 'P':
          buffer.append('p');
          break;
        case 'Q':
          buffer.append('q');
          break;
        case 'R':
          buffer.append('r');
          break;
        case 'S':
          buffer.append('s');
          break;
        case 'T':
          buffer.append('t');
          break;
        case 'U':
          buffer.append('u');
          break;
        case 'V':
          buffer.append('v');
          break;
        case 'W':
          buffer.append('w');
          break;
        case 'X':
          buffer.append('x');
          break;
        case 'Y':
          buffer.append('y');
          break;
        case 'Z':
          buffer.append('z');
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
    assert debugEnter(CLASS_NAME, "toUpperCase", String.valueOf(s));

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
  public static void toUpperCase(String s, StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toUpperCase", String.valueOf(s),
                      "java.lang.StringBuilder");

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
    assert debugEnter(CLASS_NAME, "toUpperCase", String.valueOf(b),
                      "java.lang.StringBuilder", String.valueOf(trim));

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
          assert debugException(CLASS_NAME, "toUpperCase", e);
          buffer.append(new String(b, i, (length-i)).toUpperCase());
        }
        break;
      }

      int bufferLength = buffer.length();
      switch (b[i])
      {
        case ' ':
          // If we don't care about trimming, then we can always append the
          // space.  Otherwise, only do so if there are other characters in the
          // value.
          if (trim && (bufferLength == 0))
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
    assert debugEnter(CLASS_NAME, "toRFC3641StringValue");

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
   * Retrieves a string array containing the contents of the provided
   * list of strings.
   *
   * @param stringList
   *          The string list to convert to an array.
   * @return A string array containing the contents of the provided list
   *         of strings.
   */
  public static String[] listToArray(List<String> stringList)
  {
    assert debugEnter(CLASS_NAME, "listToArray", "java.util.List<String>");

    if (stringList == null)
    {
      return null;
    }

    String[] stringArray = new String[stringList.size()];
    stringList.toArray(stringArray);
    return stringArray;
  }



  /**
   * Retrieves an array list containing the contents of the provided array.
   *
   * @param  stringArray  The string array to convert to an array list.
   *
   * @return  An array list containing the contents of the provided array.
   */
  public static ArrayList<String> arrayToList(String[] stringArray)
  {
    assert debugEnter(CLASS_NAME, "arrayToList", String.valueOf(stringArray));

    if (stringArray == null)
    {
      return null;
    }

    ArrayList<String> stringList = new ArrayList<String>(stringArray.length);
    for (String s : stringArray)
    {
      stringList.add(s);
    }

    return stringList;
  }



  /**
   * Attempts to delete the specified file or directory.  If it is a directory,
   * then any files or subdirectories that it contains will be recursively
   * deleted as well.
   *
   * @param  file  The file or directory to be removed.
   *
   * @return  <CODE>true</CODE> if the specified file and any subordinates are
   *          all successfully removed, or <CODE>false</CODE> if at least one
   *          element in the subtree could not be removed.
   */
  public static boolean recursiveDelete(File file)
  {
    assert debugEnter(CLASS_NAME, "recursiveDelete", String.valueOf(file));

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

    return (successful & file.delete());
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
      int    msgID   = MSGID_MOVEFILE_NO_SUCH_FILE;
      String message = getMessage(msgID, fileToMove.getPath());
      throw new IOException(message);
    }

    if (! fileToMove.isFile())
    {
      int    msgID   = MSGID_MOVEFILE_NOT_FILE;
      String message = getMessage(msgID, fileToMove.getPath());
      throw new IOException(message);
    }

    if (! targetDirectory.exists())
    {
      int    msgID   = MSGID_MOVEFILE_NO_SUCH_DIRECTORY;
      String message = getMessage(msgID, targetDirectory.getPath());
      throw new IOException(message);
    }

    if (! targetDirectory.isDirectory())
    {
      int    msgID   = MSGID_MOVEFILE_NOT_DIRECTORY;
      String message = getMessage(msgID, targetDirectory.getPath());
      throw new IOException(message);
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
   * Indicates whether the provided path refers to a relative path rather than
   * an absolute path.
   *
   * @param  path  The path string for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided path is relative, or
   *          <CODE>false</CODE> if it is absolute.
   */
  public static boolean isRelativePath(String path)
  {
    assert debugEnter(CLASS_NAME, "isRelativePath", String.valueOf(path));

    File f = new File(path);
    return (! f.isAbsolute());
  }



  /**
   * Retrieves a <CODE>File</CODE> object corresponding to the specified path.
   * If the given path is an absolute path, then it will be used.  If the path
   * is relative, then it will be interpreted as if it were relative to the
   * Directory Server root.
   *
   * @param  path  The path string to be retrieved as a <CODE>File</CODE>
   *
   * @return  A <CODE>File</CODE> object that corresponds to the specified path.
   */
  public static File getFileForPath(String path)
  {
    assert debugEnter(CLASS_NAME, "getFileForPath", String.valueOf(path));

    File f = new File (path);

    if (f.isAbsolute())
    {
      return f;
    }
    else
    {
      return new File(DirectoryServer.getServerRoot() + File.separator + path);
    }
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
    assert debugEnter(CLASS_NAME, "createEntry", String.valueOf(dn));


    // If the provided DN was null or empty, then return null because we don't
    // support it.
    if ((dn == null) || dn.isNullDN())
    {
      return null;
    }


    // Get the information about the RDN attributes.
    RDN rdn = dn.getRDN();
    AttributeType[]  rdnTypes  = rdn.getAttributeTypes();
    String[]         rdnNames  = rdn.getAttributeNames();
    AttributeValue[] rdnValues = rdn.getAttributeValues();


    // If there is only one RDN attribute, then see which objectclass we should
    // use.
    ObjectClass structuralClass;
    if (rdnTypes.length == 1)
    {
      if (rdnTypes[0].hasName(ATTR_C))
      {
        structuralClass = DirectoryServer.getObjectClass(OC_COUNTRY, true);
      }
      else if (rdnTypes[0].hasName(ATTR_DC))
      {
        structuralClass = DirectoryServer.getObjectClass(OC_DOMAIN, true);
      }
      else if (rdnTypes[0].hasName(ATTR_O))
      {
        structuralClass = DirectoryServer.getObjectClass(OC_ORGANIZATION, true);
      }
      else if (rdnTypes[0].hasName(ATTR_OU))
      {
        structuralClass =
             DirectoryServer.getObjectClass(OC_ORGANIZATIONAL_UNIT_LC, true);
      }
      else
      {
        structuralClass =
             DirectoryServer.getObjectClass(OC_UNTYPED_OBJECT_LC, true);
      }
    }
    else
    {
      structuralClass =
           DirectoryServer.getObjectClass(OC_UNTYPED_OBJECT_LC, true);
    }


    // Get the top and untypedObject classes to include in the entry.
    LinkedHashMap<ObjectClass,String> objectClasses =
         new LinkedHashMap<ObjectClass,String>(3);

    objectClasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);
    objectClasses.put(structuralClass, structuralClass.getNameOrOID());


    // Iterate through the RDN attributes and add them to the set of user or
    // operational attributes.
    LinkedHashMap<AttributeType,List<Attribute>> userAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();
    LinkedHashMap<AttributeType,List<Attribute>> operationalAttributes =
         new LinkedHashMap<AttributeType,List<Attribute>>();

    boolean extensibleObjectAdded = false;
    for (int i=0; i < rdnTypes.length; i++)
    {
      // First, see if this type is allowed by the untypedObject class.  If not,
      // then we'll need to include the extensibleObject class.
      if ((! structuralClass.isRequiredOrOptional(rdnTypes[i])) &&
          (! extensibleObjectAdded))
      {
        ObjectClass extensibleObjectOC =
             DirectoryServer.getObjectClass(OC_EXTENSIBLE_OBJECT_LC);
        if (extensibleObjectOC == null)
        {
          extensibleObjectOC =
               DirectoryServer.getDefaultObjectClass(OC_EXTENSIBLE_OBJECT);
        }
        objectClasses.put(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);
        extensibleObjectAdded = true;
      }


      // Create the attribute and add it to the appropriate map.
      LinkedHashSet<AttributeValue> valueSet =
           new LinkedHashSet<AttributeValue>(1);
      valueSet.add(rdnValues[i]);

      if (rdnTypes[i].isOperational())
      {
        List<Attribute> attrList = operationalAttributes.get(rdnTypes[i]);
        if ((attrList == null) || attrList.isEmpty())
        {
          attrList = new ArrayList<Attribute>(1);
          attrList.add(new Attribute(rdnTypes[i], rdnNames[i], valueSet));
          operationalAttributes.put(rdnTypes[i], attrList);
        }
        else
        {
          Attribute attr = attrList.get(0);
          attr.getValues().add(rdnValues[i]);
        }
      }
      else
      {
        List<Attribute> attrList = userAttributes.get(rdnTypes[i]);
        if ((attrList == null) || attrList.isEmpty())
        {
          attrList = new ArrayList<Attribute>(1);
          attrList.add(new Attribute(rdnTypes[i], rdnNames[i], valueSet));
          userAttributes.put(rdnTypes[i], attrList);
        }
        else
        {
          Attribute attr = attrList.get(0);
          attr.getValues().add(rdnValues[i]);
        }
      }
    }


    // Create and return the entry.
    return new Entry(dn, objectClasses, userAttributes, operationalAttributes);
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
  public static String secondsToTimeString(int numSeconds)
  {
    if (numSeconds < 60)
    {
      // We can express it in seconds.
      return getMessage(MSGID_TIME_IN_SECONDS, numSeconds);
    }
    else if (numSeconds < 3600)
    {
      // We can express it in minutes and seconds.
      int m = numSeconds / 60;
      int s = numSeconds % 60;
      return getMessage(MSGID_TIME_IN_MINUTES_SECONDS, m, s);
    }
    else if (numSeconds < 86400)
    {
      // We can express it in hours, minutes, and seconds.
      int h = numSeconds / 3600;
      int m = (numSeconds % 3600) / 60;
      int s = numSeconds % 3600 % 60;
      return getMessage(MSGID_TIME_IN_HOURS_MINUTES_SECONDS, h, m, s);
    }
    else
    {
      // We can express it in days, hours, minutes, and seconds.
      int d = numSeconds / 86400;
      int h = (numSeconds % 86400) / 3600;
      int m = (numSeconds % 86400 % 3600) / 60;
      int s = numSeconds % 86400 % 3600 % 60;
      return getMessage(MSGID_TIME_IN_DAYS_HOURS_MINUTES_SECONDS, d, h, m, s);
    }
  }



  /**
   * Inserts line breaks into the provided buffer to wrap text at no more than
   * the specified column width.  Wrapping will only be done at space boundaries
   * and if there are no spaces within the specified width, then wrapping will
   * be performed at the first space after the specified column.
   *
   * @param  text   The text to be wrapped.
   * @param  width  The maximum number of characters to allow on a line if there
   *                is a suitable breaking point.
   *
   * @return  The wrapped text.
   */
  public static String wrapText(String text, int width)
  {
    StringBuilder   buffer        = new StringBuilder();
    StringTokenizer lineTokenizer = new StringTokenizer(text, "\r\n", true);
    while (lineTokenizer.hasMoreTokens())
    {
      String line = lineTokenizer.nextToken();
      if (line.equals("\r") || line.equals("\n"))
      {
        // It's an end-of-line character, so append it as-is.
        buffer.append(line);
      }
      else if (line.length() < width)
      {
        // The line fits in the specified width, so append it as-is.
        buffer.append(line);
      }
      else
      {
        // The line doesn't fit in the specified width, so it needs to be
        // wrapped.  Do so at space boundaries.
        StringBuilder   lineBuffer    = new StringBuilder();
        StringBuilder   delimBuffer   = new StringBuilder();
        StringTokenizer wordTokenizer = new StringTokenizer(line, " ", true);
        while (wordTokenizer.hasMoreTokens())
        {
          String word = wordTokenizer.nextToken();
          if (word.equals(" "))
          {
            // It's a space, so add it to the delim buffer only if the line
            // buffer is not empty.
            if (lineBuffer.length() > 0)
            {
              delimBuffer.append(word);
            }
          }
          else if (word.length() > width)
          {
            // This is a long word that can't be wrapped, so we'll just have to
            // make do.
            if (lineBuffer.length() > 0)
            {
              buffer.append(lineBuffer);
              buffer.append(EOL);
              lineBuffer = new StringBuilder();
            }
            buffer.append(word);

            if (wordTokenizer.hasMoreTokens())
            {
              // The next token must be a space, so remove it.  If there are
              // still more tokens after that, then append an EOL.
              wordTokenizer.nextToken();
              if (wordTokenizer.hasMoreTokens())
              {
                buffer.append(EOL);
              }
            }

            if (delimBuffer.length() > 0)
            {
              delimBuffer = new StringBuilder();
            }
          }
          else
          {
            // It's not a space, so see if we can fit it on the curent line.
            int newLineLength = lineBuffer.length() + delimBuffer.length() +
                                word.length();
            if (newLineLength < width)
            {
              // It does fit on the line, so add it.
              lineBuffer.append(delimBuffer).append(word);

              if (delimBuffer.length() > 0)
              {
                delimBuffer = new StringBuilder();
              }
            }
            else
            {
              // It doesn't fit on the line, so end the current line and start
              // a new one.
              buffer.append(lineBuffer);
              buffer.append(EOL);

              lineBuffer = new StringBuilder();
              lineBuffer.append(word);

              if (delimBuffer.length() > 0)
              {
                delimBuffer = new StringBuilder();
              }
            }
          }
        }

        // If there's anything left in the line buffer, then add it to the
        // final buffer.
        buffer.append(lineBuffer);
      }
    }

    return buffer.toString();
  }
}

