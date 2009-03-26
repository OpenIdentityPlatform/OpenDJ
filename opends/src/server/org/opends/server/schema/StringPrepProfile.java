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
 *      Copyright 2009 Sun Microsystems, Inc.
 */


package org.opends.server.schema;

import java.util.HashMap;
import java.util.HashSet;


import org.opends.server.types.ByteSequence;
import static org.opends.server.util.Validator.*;
import org.opends.server.util.Platform;


/**
 * This class defines the  "stringprep" profile as defined in RFC 4518.
 * It must be used by all  the matching rules that support unicode
 * characters. For a complete list of such rules, refer to Section
 * 4.2, RFC 4517.
 */
public final class StringPrepProfile
{
  /**
   * Defines SPACE character.
   */
  private static final char SPACE_CHAR = '\u0020';


  /**
   * Indicates whether case should be folded during string preparation.
   */
  public static final boolean CASE_FOLD = true;


  /**
   * Indicates whether case should not be folded during string preparation.
   */
  public static final boolean NO_CASE_FOLD =false;


  /**
   * Indicates whether leading and trailing spaces should be trimmed during
   * string preparation.
   */
  public static final boolean TRIM = true;



  /**
   * Prepares an attribute or assertion value as per stringprep algorithm
   * defined in RFC 4518.
   *
   * @param buffer The buffer to which the prepared form of the string should
   *                 be appended.
   * @param sequence The {@link ByteSequence} that needs preparation.
   * @param trim Indicates whether leading and trailing spaces should be
   *                 omitted from the string representation.
   * @param foldCase Indicates whether the case will be folded during mapping.
   * @see <a href='http://www.rfc-editor.org/rfc/rfc4518.txt'>
   *                              Internationalized String Preparation</a>
   */
  public static void prepareUnicode(StringBuilder buffer,
          ByteSequence sequence,
          boolean trim,
          boolean foldCase)
  {
    ensureNotNull(buffer);
    ensureNotNull(sequence);
    //Optimize in the case of purely ascii characters which is the most common
    //case.
    int length = sequence.length();
    for (int i=0; i < length; i++)
    {
      if((sequence.byteAt(i) &  0x7F) != sequence.byteAt(i))
      {
        //Map the attribute value.
        map(buffer,sequence.subSequence(i, length),trim,foldCase);
        //Normalize the attribute value.
        normalize(buffer);
        break;
      }
      int buffLen = buffer.length();
      switch(sequence.byteAt(i))
      {
        case ' ':
          if ((trim && (buffLen == 0)) ||
          (buffLen > 0 && buffer.charAt(buffLen-1)==SPACE_CHAR))
          {
            break;
          }
          buffer.append(' ');
          break;
        default:
          byte b = sequence.byteAt(i);
          //Perform mapping.
          if(b >='\u0009' && b<'\u000E')
          {
            //These characters are mapped to a SPACE.
            buffLen = buffer.length();
            if((trim && ( (buffLen ==0) ))
              || (buffLen > 0 && buffer.charAt(buffLen-1) == ' '))
            {
              /** Do not map this character into a space if:
              * a . trimming is desired and this was the leading char.
              * b. The last character was a space. **/
              break;
            }
            else
            {
              buffer.append(SPACE_CHAR);
            }
          }
          else if((b>='\u0000' && b<='\u0008') || (b>='\u000E' && b<='\u001F')
          || b == '\u007F')
          {
            //These characters are mapped to nothing and hence not copied over..
            break;
          }
          else if (foldCase && b >=65 && b<=90)
          {
            //If case-folding is allowed then map to the lower case.
            buffer.append((char)(b+32));
          }
          else
          {
            buffer.append((char)b);
          }
        break;
      }
    }
    if (trim)
    {
      // Strip off any trailing spaces.
      for (int i=buffer.length()-1; i > 0; i--)
      {
        if (buffer.charAt(i) == SPACE_CHAR)
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



  //Checks each character and replaces it with its mapping.
  private static void map(StringBuilder buffer,
          ByteSequence value,
          boolean trim,
          boolean foldCase)
  {
     MappingTable.map(buffer,value,trim,foldCase);
  }



  //Normalizes the input string with NFKC Form.
  private static void normalize(StringBuilder buffer)
  {
    Platform.normalize(buffer);
  }



/**
 * A Table defining the mapped code-points as per RFC 3454.
 */
  private static class MappingTable
  {
    //Set of chars which are deleted from the incoming value.
    private final static HashSet<Character> map2null =
            new HashSet<Character>();
    //Set of chars which are replaced by a SPACE when found.
    private final static HashSet<Character> map2space =
            new HashSet<Character>();
    //Table for case-folding. Map of Character and String containing  uppercase
    //and lowercase value as the key-value pair.
    private final static HashMap<Character,String>caseMappingTable =
            new HashMap<Character,String>();

    static
    {
      //Appendix B.1 RFC 3454.
      char[][] mapped2null = new char[][]
      {
        {'\u0000','\u0008'},{'\u000E','\u001F'},{'\u007F','\u0084'},
        {'\u0086','\u009F'},{'\u00AD'}, {'\u034F'},{'\u06DD'},{'\u070F'},
        {'\u1806'},{'\u180B','\u180E'},{'\u200C', '\u200F'}, {'\u202A',
        '\u202E'},{'\u2060','\u2063'}, {'\u206A','\u206F'},{'\uFE00','\uFE0F'},
        {'\uFEFF'},{'\uFFF9','\uFFFC'}
      };

      for(int i=0;i<mapped2null.length;i++)
      {
        if(mapped2null[i].length == 1)
        {
          map2null.add(mapped2null[i][0]);
        }
        else
        {
          //Contains a range of values.
          for(char c = mapped2null[i][0]; c <= mapped2null[i][1]; c++)
          {
            map2null.add(c);
          }
        }
      }

      char[] mapped2Space = new char[] {
      '\u0009',0xA,'\u000B','\u000C',0xD,'\u0085',
      '\u00A0','\u1680','\u2000','\u2001','\u2002','\u2003','\u2004','\u2005',
      '\u2006','\u2007','\u2008','\u2009','\u200A','\u2028','\u2029','\u202F',
      '\u205F','\u3000'};

      for(char c:mapped2Space)
      {
        map2space.add(c);
      }

      //Appendix B.2 RFC 3454.
      //Build an uppercase array and a lowercase array and create a map of both
      //values.
      char[] upperCaseArr = new char[] {'\u0041', '\u0042', '\u0043', '\u0044',
      '\u0045', '\u0046', '\u0047', '\u0048', '\u0049', '\u004A', '\u004B',
      '\u004C', '\u004D', '\u004E', '\u004F', '\u0050', '\u0051', '\u0052',
      '\u0053', '\u0054', '\u0055', '\u0056', '\u0057', '\u0058', '\u0059',
      '\u005A', '\u00B5', '\u00C0', '\u00C1', '\u00C2', '\u00C3', '\u00C4',
      '\u00C5', '\u00C6', '\u00C7', '\u00C8', '\u00C9', '\u00CA', '\u00CB',
      '\u00CC', '\u00CD', '\u00CE', '\u00CF', '\u00D0', '\u00D1', '\u00D2',
      '\u00D3', '\u00D4', '\u00D5', '\u00D6', '\u00D8', '\u00D9', '\u00DA',
      '\u00DB', '\u00DC', '\u00DD', '\u00DE', '\u00DF', '\u0100', '\u0102',
      '\u0104', '\u0106', '\u0108', '\u010A', '\u010C', '\u010E', '\u0110',
      '\u0112', '\u0114', '\u0116', '\u0118', '\u011A', '\u011C', '\u011E',
      '\u0120', '\u0122', '\u0124', '\u0126', '\u0128', '\u012A', '\u012C',
      '\u012E', '\u0130', '\u0132', '\u0134', '\u0136', '\u0139', '\u013B',
      '\u013D', '\u013F', '\u0141', '\u0143', '\u0145', '\u0147', '\u0149',
      '\u014A', '\u014C', '\u014E', '\u0150', '\u0152', '\u0154', '\u0156',
      '\u0158', '\u015A', '\u015C', '\u015E', '\u0160', '\u0162', '\u0164',
      '\u0166', '\u0168', '\u016A', '\u016C', '\u016E', '\u0170', '\u0172',
      '\u0174', '\u0176', '\u0178', '\u0179', '\u017B', '\u017D', '\u017F',
      '\u0181', '\u0182', '\u0184', '\u0186', '\u0187', '\u0189', '\u018A',
      '\u018B', '\u018E', '\u018F', '\u0190', '\u0191', '\u0193', '\u0194',
      '\u0196', '\u0197', '\u0198', '\u019C', '\u019D', '\u019F', '\u01A0',
      '\u01A2', '\u01A4', '\u01A6', '\u01A7', '\u01A9', '\u01AC', '\u01AE',
      '\u01AF', '\u01B1', '\u01B2', '\u01B3', '\u01B5', '\u01B7', '\u01B8',
      '\u01BC', '\u01C4', '\u01C5', '\u01C7', '\u01C8', '\u01CA', '\u01CB',
      '\u01CD', '\u01CF', '\u01D1', '\u01D3', '\u01D5', '\u01D7', '\u01D9',
      '\u01DB', '\u01DE', '\u01E0', '\u01E2', '\u01E4', '\u01E6', '\u01E8',
      '\u01EA', '\u01EC', '\u01EE', '\u01F0', '\u01F1', '\u01F2', '\u01F4',
      '\u01F6', '\u01F7', '\u01F8', '\u01FA', '\u01FC', '\u01FE', '\u0200',
      '\u0202', '\u0204', '\u0206', '\u0208', '\u020A', '\u020C', '\u020E',
      '\u0210', '\u0212', '\u0214', '\u0216', '\u0218', '\u021A', '\u021C',
      '\u021E', '\u0220', '\u0222', '\u0224', '\u0226', '\u0228', '\u022A',
      '\u022C', '\u022E', '\u0230', '\u0232', '\u0345', '\u037A', '\u0386',
      '\u0388', '\u0389', '\u038A', '\u038C', '\u038E', '\u038F', '\u0390',
      '\u0391', '\u0392', '\u0393', '\u0394', '\u0395', '\u0396', '\u0397',
      '\u0398', '\u0399', '\u039A', '\u039B', '\u039C', '\u039D', '\u039E',
      '\u039F', '\u03A0', '\u03A1', '\u03A3', '\u03A4', '\u03A5', '\u03A6',
      '\u03A7', '\u03A8', '\u03A9', '\u03AA', '\u03AB', '\u03B0', '\u03C2',
      '\u03D0', '\u03D1', '\u03D2', '\u03D3', '\u03D4', '\u03D5', '\u03D6',
      '\u03D8', '\u03DA', '\u03DC', '\u03DE', '\u03E0', '\u03E2', '\u03E4',
      '\u03E6', '\u03E8', '\u03EA', '\u03EC', '\u03EE', '\u03F0', '\u03F1',
      '\u03F2', '\u03F4', '\u03F5', '\u0400', '\u0401', '\u0402', '\u0403',
      '\u0404', '\u0405', '\u0406', '\u0407', '\u0408', '\u0409', '\u040A',
      '\u040B', '\u040C', '\u040D', '\u040E', '\u040F', '\u0410', '\u0411',
      '\u0412', '\u0413', '\u0414', '\u0415', '\u0416', '\u0417', '\u0418',
      '\u0419', '\u041A', '\u041B', '\u041C', '\u041D', '\u041E', '\u041F',
      '\u0420', '\u0421', '\u0422', '\u0423', '\u0424', '\u0425', '\u0426',
      '\u0427', '\u0428', '\u0429', '\u042A', '\u042B', '\u042C', '\u042D',
      '\u042E', '\u042F', '\u0460', '\u0462', '\u0464', '\u0466', '\u0468',
      '\u046A', '\u046C', '\u046E', '\u0470', '\u0472', '\u0474', '\u0476',
      '\u0478', '\u047A', '\u047C', '\u047E', '\u0480', '\u048A', '\u048C',
      '\u048E', '\u0490', '\u0492', '\u0494', '\u0496', '\u0498', '\u049A',
      '\u049C', '\u049E', '\u04A0', '\u04A2', '\u04A4', '\u04A6', '\u04A8',
      '\u04AA', '\u04AC', '\u04AE', '\u04B0', '\u04B2', '\u04B4', '\u04B6',
      '\u04B8', '\u04BA', '\u04BC', '\u04BE', '\u04C1', '\u04C3', '\u04C5',
      '\u04C7', '\u04C9', '\u04CB', '\u04CD', '\u04D0', '\u04D2', '\u04D4',
      '\u04D6', '\u04D8', '\u04DA', '\u04DC', '\u04DE', '\u04E0', '\u04E2',
      '\u04E4', '\u04E6', '\u04E8', '\u04EA', '\u04EC', '\u04EE', '\u04F0',
      '\u04F2', '\u04F4', '\u04F8', '\u0500', '\u0502', '\u0504', '\u0506',
      '\u0508', '\u050A', '\u050C', '\u050E', '\u0531', '\u0532', '\u0533',
      '\u0534', '\u0535', '\u0536', '\u0537', '\u0538', '\u0539', '\u053A',
      '\u053B', '\u053C', '\u053D', '\u053E', '\u053F', '\u0540', '\u0541',
      '\u0542', '\u0543', '\u0544', '\u0545', '\u0546', '\u0547', '\u0548',
      '\u0549', '\u054A', '\u054B', '\u054C', '\u054D', '\u054E', '\u054F',
      '\u0550', '\u0551', '\u0552', '\u0553', '\u0554', '\u0555', '\u0556',
      '\u0587', '\u1E00', '\u1E02', '\u1E04', '\u1E06', '\u1E08', '\u1E0A',
      '\u1E0C', '\u1E0E', '\u1E10', '\u1E12', '\u1E14', '\u1E16', '\u1E18',
      '\u1E1A', '\u1E1C', '\u1E1E', '\u1E20', '\u1E22', '\u1E24', '\u1E26',
      '\u1E28', '\u1E2A', '\u1E2C', '\u1E2E', '\u1E30', '\u1E32', '\u1E34',
      '\u1E36', '\u1E38', '\u1E3A', '\u1E3C', '\u1E3E', '\u1E40', '\u1E42',
      '\u1E44', '\u1E46', '\u1E48', '\u1E4A', '\u1E4C', '\u1E4E', '\u1E50',
      '\u1E52', '\u1E54', '\u1E56', '\u1E58', '\u1E5A', '\u1E5C', '\u1E5E',
      '\u1E60', '\u1E62', '\u1E64', '\u1E66', '\u1E68', '\u1E6A', '\u1E6C',
      '\u1E6E', '\u1E70', '\u1E72', '\u1E74', '\u1E76', '\u1E78', '\u1E7A',
      '\u1E7C', '\u1E7E', '\u1E80', '\u1E82', '\u1E84', '\u1E86', '\u1E88',
      '\u1E8A', '\u1E8C', '\u1E8E', '\u1E90', '\u1E92', '\u1E94', '\u1E96',
      '\u1E97', '\u1E98', '\u1E99', '\u1E9A', '\u1E9B', '\u1EA0', '\u1EA2',
      '\u1EA4', '\u1EA6', '\u1EA8', '\u1EAA', '\u1EAC', '\u1EAE', '\u1EB0',
      '\u1EB2', '\u1EB4', '\u1EB6', '\u1EB8', '\u1EBA', '\u1EBC', '\u1EBE',
      '\u1EC0', '\u1EC2', '\u1EC4', '\u1EC6', '\u1EC8', '\u1ECA', '\u1ECC',
      '\u1ECE', '\u1ED0', '\u1ED2', '\u1ED4', '\u1ED6', '\u1ED8', '\u1EDA',
      '\u1EDC', '\u1EDE', '\u1EE0', '\u1EE2', '\u1EE4', '\u1EE6', '\u1EE8',
      '\u1EEA', '\u1EEC', '\u1EEE', '\u1EF0', '\u1EF2', '\u1EF4', '\u1EF6',
      '\u1EF8', '\u1F08', '\u1F09', '\u1F0A', '\u1F0B', '\u1F0C', '\u1F0D',
      '\u1F0E', '\u1F0F', '\u1F18', '\u1F19', '\u1F1A', '\u1F1B', '\u1F1C',
      '\u1F1D', '\u1F28', '\u1F29', '\u1F2A', '\u1F2B', '\u1F2C', '\u1F2D',
      '\u1F2E', '\u1F2F', '\u1F38', '\u1F39', '\u1F3A', '\u1F3B', '\u1F3C',
      '\u1F3D', '\u1F3E', '\u1F3F', '\u1F48', '\u1F49', '\u1F4A', '\u1F4B',
      '\u1F4C', '\u1F4D', '\u1F50', '\u1F52', '\u1F54', '\u1F56', '\u1F59',
      '\u1F5B', '\u1F5D', '\u1F5F', '\u1F68', '\u1F69', '\u1F6A', '\u1F6B',
      '\u1F6C', '\u1F6D', '\u1F6E', '\u1F6F', '\u1F80', '\u1F81', '\u1F82',
      '\u1F83', '\u1F84', '\u1F85', '\u1F86', '\u1F87', '\u1F88', '\u1F89',
      '\u1F8A', '\u1F8B', '\u1F8C', '\u1F8D', '\u1F8E', '\u1F8F', '\u1F90',
      '\u1F91', '\u1F92', '\u1F93', '\u1F94', '\u1F95', '\u1F96', '\u1F97',
      '\u1F98', '\u1F99', '\u1F9A', '\u1F9B', '\u1F9C', '\u1F9D', '\u1F9E',
      '\u1F9F', '\u1FA0', '\u1FA1', '\u1FA2', '\u1FA3', '\u1FA4', '\u1FA5',
      '\u1FA6', '\u1FA7', '\u1FA8', '\u1FA9', '\u1FAA', '\u1FAB', '\u1FAC',
      '\u1FAD', '\u1FAE', '\u1FAF', '\u1FB2', '\u1FB3', '\u1FB4', '\u1FB6',
      '\u1FB7', '\u1FB8', '\u1FB9', '\u1FBA', '\u1FBB', '\u1FBC', '\u1FBE',
      '\u1FC2', '\u1FC3', '\u1FC4', '\u1FC6', '\u1FC7', '\u1FC8', '\u1FC9',
      '\u1FCA', '\u1FCB', '\u1FCC', '\u1FD2', '\u1FD3', '\u1FD6', '\u1FD7',
      '\u1FD8', '\u1FD9', '\u1FDA', '\u1FDB', '\u1FE2', '\u1FE3', '\u1FE4',
      '\u1FE6', '\u1FE7', '\u1FE8', '\u1FE9', '\u1FEA', '\u1FEB', '\u1FEC',
      '\u1FF2', '\u1FF3', '\u1FF4', '\u1FF6', '\u1FF7', '\u1FF8', '\u1FF9',
      '\u1FFA', '\u1FFB', '\u1FFC', '\u20A8', '\u2102', '\u2103', '\u2107',
      '\u2109', '\u210B', '\u210C', '\u210D', '\u2110', '\u2111', '\u2112',
      '\u2115', '\u2116', '\u2119', '\u211A', '\u211B', '\u211C', '\u211D',
      '\u2120', '\u2121', '\u2122', '\u2124', '\u2126', '\u2128', '\u212A',
      '\u212B', '\u212C', '\u212D', '\u2130', '\u2131', '\u2133', '\u213E',
      '\u213F', '\u2145', '\u2160', '\u2161', '\u2162', '\u2163', '\u2164',
      '\u2165', '\u2166', '\u2167', '\u2168', '\u2169', '\u216A', '\u216B',
      '\u216C', '\u216D', '\u216E', '\u216F', '\u24B6', '\u24B7', '\u24B8',
      '\u24B9', '\u24BA', '\u24BB', '\u24BC', '\u24BD', '\u24BE', '\u24BF',
      '\u24C0', '\u24C1', '\u24C2', '\u24C3', '\u24C4', '\u24C5', '\u24C6',
      '\u24C7', '\u24C8', '\u24C9', '\u24CA', '\u24CB', '\u24CC', '\u24CD',
      '\u24CE', '\u24CF', '\u3371', '\u3373', '\u3375', '\u3380', '\u3381',
      '\u3382', '\u3383', '\u3384', '\u3385', '\u3386', '\u3387', '\u338A',
      '\u338B', '\u338C', '\u3390', '\u3391', '\u3392', '\u3393', '\u3394',
      '\u33A9', '\u33AA', '\u33AB', '\u33AC', '\u33B4', '\u33B5', '\u33B6',
      '\u33B7', '\u33B8', '\u33B9', '\u33BA', '\u33BB', '\u33BC', '\u33BD',
      '\u33BE', '\u33BF', '\u33C0', '\u33C1', '\u33C3', '\u33C6', '\u33C7',
      '\u33C8', '\u33C9', '\u33CB', '\u33CD', '\u33CE', '\u33D7', '\u33D9',
      '\u33DA', '\u33DC', '\u33DD', '\uFB00', '\uFB01', '\uFB02', '\uFB03',
      '\uFB04', '\uFB05', '\uFB06', '\uFB13', '\uFB14', '\uFB15', '\uFB16',
      '\uFB17', '\uFF21', '\uFF22', '\uFF23', '\uFF24', '\uFF25', '\uFF26',
      '\uFF27', '\uFF28', '\uFF29', '\uFF2A', '\uFF2B', '\uFF2C', '\uFF2D',
      '\uFF2E', '\uFF2F', '\uFF30', '\uFF31', '\uFF32', '\uFF33', '\uFF34',
      '\uFF35', '\uFF36', '\uFF37', '\uFF38', '\uFF39', '\uFF3A'};
      String[] lowerCaseFoldedArr = new String[] {
      "\u0061", "\u0062", "\u0063", "\u0064",
      "\u0065", "\u0066", "\u0067", "\u0068", "\u0069", "\u006A", "\u006B",
      "\u006C", "\u006D", "\u006E", "\u006F", "\u0070", "\u0071", "\u0072",
      "\u0073", "\u0074", "\u0075", "\u0076", "\u0077", "\u0078", "\u0079",
      "\u007A", "\u03BC", "\u00E0", "\u00E1", "\u00E2", "\u00E3", "\u00E4",
      "\u00E5", "\u00E6", "\u00E7", "\u00E8", "\u00E9", "\u00EA", "\u00EB",
      "\u00EC", "\u00ED", "\u00EE", "\u00EF", "\u00F0", "\u00F1", "\u00F2",
      "\u00F3", "\u00F4", "\u00F5", "\u00F6", "\u00F8", "\u00F9", "\u00FA",
      "\u00FB", "\u00FC", "\u00FD", "\u00FE", "\u0073\u0073", "\u0101",
      "\u0103", "\u0105", "\u0107", "\u0109", "\u010B", "\u010D", "\u010F",
      "\u0111", "\u0113", "\u0115", "\u0117", "\u0119", "\u011B", "\u011D",
      "\u011F", "\u0121", "\u0123", "\u0125", "\u0127", "\u0129", "\u012B",
      "\u012D", "\u012F", "\u0069\u0307", "\u0133", "\u0135", "\u0137",
      "\u013A", "\u013C", "\u013E", "\u0140", "\u0142", "\u0144", "\u0146",
      "\u0148", "\u02BC\u006E", "\u014B", "\u014D", "\u014F", "\u0151",
      "\u0153", "\u0155", "\u0157", "\u0159", "\u015B", "\u015D", "\u015F",
      "\u0161", "\u0163", "\u0165", "\u0167", "\u0169", "\u016B", "\u016D",
      "\u016F", "\u0171", "\u0173", "\u0175", "\u0177", "\u00FF", "\u017A",
      "\u017C", "\u017E", "\u0073", "\u0253", "\u0183", "\u0185", "\u0254",
      "\u0188", "\u0256", "\u0257", "\u018C", "\u01DD", "\u0259", "\u025B",
      "\u0192", "\u0260", "\u0263", "\u0269", "\u0268", "\u0199", "\u026F",
      "\u0272", "\u0275", "\u01A1", "\u01A3", "\u01A5", "\u0280", "\u01A8",
      "\u0283", "\u01AD", "\u0288", "\u01B0", "\u028A", "\u028B", "\u01B4",
      "\u01B6", "\u0292", "\u01B9", "\u01BD", "\u01C6", "\u01C6", "\u01C9",
      "\u01C9", "\u01CC", "\u01CC", "\u01CE", "\u01D0", "\u01D2", "\u01D4",
      "\u01D6", "\u01D8", "\u01DA", "\u01DC", "\u01DF", "\u01E1", "\u01E3",
      "\u01E5", "\u01E7", "\u01E9", "\u01EB", "\u01ED", "\u01EF",
      "\u006A\u030C"
      , "\u01F3", "\u01F3", "\u01F5", "\u0195", "\u01BF", "\u01F9", "\u01FB",
      "\u01FD", "\u01FF", "\u0201", "\u0203", "\u0205", "\u0207", "\u0209",
      "\u020B", "\u020D", "\u020F", "\u0211", "\u0213", "\u0215", "\u0217",
      "\u0219", "\u021B", "\u021D", "\u021F", "\u019E", "\u0223", "\u0225",
      "\u0227", "\u0229", "\u022B", "\u022D", "\u022F", "\u0231", "\u0233",
      "\u03B9", "\u0020\u03B9", "\u03AC", "\u03AD", "\u03AE", "\u03AF",
      "\u03CC",
      "\u03CD", "\u03CE", "\u03B9\u0308\u0301", "\u03B1", "\u03B2", "\u03B3",
      "\u03B4", "\u03B5", "\u03B6", "\u03B7", "\u03B8", "\u03B9", "\u03BA",
      "\u03BB", "\u03BC", "\u03BD", "\u03BE", "\u03BF", "\u03C0", "\u03C1",
      "\u03C3", "\u03C4", "\u03C5", "\u03C6", "\u03C7", "\u03C8", "\u03C9",
      "\u03CA", "\u03CB", "\u03C5\u0308\u0301", "\u03C3", "\u03B2", "\u03B8",
      "\u03C5", "\u03CD", "\u03CB", "\u03C6", "\u03C0", "\u03D9", "\u03DB",
      "\u03DD", "\u03DF", "\u03E1", "\u03E3", "\u03E5", "\u03E7", "\u03E9",
      "\u03EB", "\u03ED", "\u03EF", "\u03BA", "\u03C1", "\u03C3", "\u03B8",
      "\u03B5", "\u0450", "\u0451", "\u0452", "\u0453", "\u0454", "\u0455",
      "\u0456", "\u0457", "\u0458", "\u0459", "\u045A", "\u045B", "\u045C",
      "\u045D", "\u045E", "\u045F", "\u0430", "\u0431", "\u0432", "\u0433",
      "\u0434", "\u0435", "\u0436", "\u0437", "\u0438", "\u0439", "\u043A",
      "\u043B", "\u043C", "\u043D", "\u043E", "\u043F", "\u0440", "\u0441",
      "\u0442", "\u0443", "\u0444", "\u0445", "\u0446", "\u0447", "\u0448",
      "\u0449", "\u044A", "\u044B", "\u044C", "\u044D", "\u044E", "\u044F",
      "\u0461", "\u0463", "\u0465", "\u0467", "\u0469", "\u046B", "\u046D",
      "\u046F", "\u0471", "\u0473", "\u0475", "\u0477", "\u0479", "\u047B",
      "\u047D", "\u047F", "\u0481", "\u048B", "\u048D", "\u048F", "\u0491",
      "\u0493", "\u0495", "\u0497", "\u0499", "\u049B", "\u049D", "\u049F",
      "\u04A1", "\u04A3", "\u04A5", "\u04A7", "\u04A9", "\u04AB", "\u04AD",
      "\u04AF", "\u04B1", "\u04B3", "\u04B5", "\u04B7", "\u04B9", "\u04BB",
      "\u04BD", "\u04BF", "\u04C2", "\u04C4", "\u04C6", "\u04C8", "\u04CA",
      "\u04CC", "\u04CE", "\u04D1", "\u04D3", "\u04D5", "\u04D7", "\u04D9",
      "\u04DB", "\u04DD", "\u04DF", "\u04E1", "\u04E3", "\u04E5", "\u04E7",
      "\u04E9", "\u04EB", "\u04ED", "\u04EF", "\u04F1", "\u04F3", "\u04F5",
      "\u04F9", "\u0501", "\u0503", "\u0505", "\u0507", "\u0509", "\u050B",
      "\u050D", "\u050F", "\u0561", "\u0562", "\u0563", "\u0564", "\u0565",
      "\u0566", "\u0567", "\u0568", "\u0569", "\u056A", "\u056B", "\u056C",
      "\u056D", "\u056E", "\u056F", "\u0570", "\u0571", "\u0572", "\u0573",
      "\u0574", "\u0575", "\u0576", "\u0577", "\u0578", "\u0579", "\u057A",
      "\u057B", "\u057C", "\u057D", "\u057E", "\u057F", "\u0580", "\u0581",
      "\u0582", "\u0583", "\u0584", "\u0585", "\u0586", "\u0565\u0582",
      "\u1E01", "\u1E03", "\u1E05", "\u1E07", "\u1E09", "\u1E0B", "\u1E0D",
      "\u1E0F", "\u1E11", "\u1E13", "\u1E15", "\u1E17", "\u1E19", "\u1E1B",
      "\u1E1D", "\u1E1F", "\u1E21", "\u1E23", "\u1E25", "\u1E27", "\u1E29",
      "\u1E2B", "\u1E2D", "\u1E2F", "\u1E31", "\u1E33", "\u1E35", "\u1E37",
      "\u1E39", "\u1E3B", "\u1E3D", "\u1E3F", "\u1E41", "\u1E43", "\u1E45",
      "\u1E47", "\u1E49", "\u1E4B", "\u1E4D", "\u1E4F", "\u1E51", "\u1E53",
      "\u1E55", "\u1E57", "\u1E59", "\u1E5B", "\u1E5D", "\u1E5F", "\u1E61",
      "\u1E63", "\u1E65", "\u1E67", "\u1E69", "\u1E6B", "\u1E6D", "\u1E6F",
      "\u1E71", "\u1E73", "\u1E75", "\u1E77", "\u1E79", "\u1E7B", "\u1E7D",
      "\u1E7F", "\u1E81", "\u1E83", "\u1E85", "\u1E87", "\u1E89", "\u1E8B",
      "\u1E8D", "\u1E8F", "\u1E91", "\u1E93", "\u1E95", "\u0068\u0331",
      "\u0074\u0308", "\u0077\u030A", "\u0079\u030A", "\u0061\u02BE",
      "\u1E61", "\u1EA1", "\u1EA3", "\u1EA5", "\u1EA7", "\u1EA9", "\u1EAB",
      "\u1EAD", "\u1EAF", "\u1EB1", "\u1EB3", "\u1EB5", "\u1EB7", "\u1EB9",
      "\u1EBB", "\u1EBD", "\u1EBF", "\u1EC1", "\u1EC3", "\u1EC5", "\u1EC7",
      "\u1EC9", "\u1ECB", "\u1ECD", "\u1ECF", "\u1ED1", "\u1ED3", "\u1ED5",
      "\u1ED7", "\u1ED9", "\u1EDB", "\u1EDD", "\u1EDF", "\u1EE1", "\u1EE3",
      "\u1EE5", "\u1EE7", "\u1EE9", "\u1EEB", "\u1EED", "\u1EEF", "\u1EF1",
      "\u1EF3", "\u1EF5", "\u1EF7", "\u1EF9", "\u1F00", "\u1F01", "\u1F02",
      "\u1F03", "\u1F04", "\u1F05", "\u1F06", "\u1F07", "\u1F10", "\u1F11",
      "\u1F12", "\u1F13", "\u1F14", "\u1F15", "\u1F20", "\u1F21", "\u1F22",
      "\u1F23", "\u1F24", "\u1F25", "\u1F26", "\u1F27", "\u1F30", "\u1F31",
      "\u1F32", "\u1F33", "\u1F34", "\u1F35", "\u1F36", "\u1F37", "\u1F40",
      "\u1F41", "\u1F42", "\u1F43", "\u1F44", "\u1F45", "\u03C5\u0313",
      "\u03C5\u0313\u0300", "\u03C5\u0313\u0301", "\u03C5\u0313\u0342",
      "\u1F51"
      , "\u1F53", "\u1F55", "\u1F57", "\u1F60", "\u1F61", "\u1F62", "\u1F63",
      "\u1F64", "\u1F65", "\u1F66", "\u1F67", "\u1F00\u03B9", "\u1F01\u03B9",
      "\u1F02\u03B9", "\u1F03\u03B9", "\u1F04\u03B9", "\u1F05\u03B9",
      "\u1F06\u03B9", "\u1F07\u03B9", "\u1F00\u03B9", "\u1F01\u03B9",
      "\u1F02\u03B9", "\u1F03\u03B9", "\u1F04\u03B9", "\u1F05\u03B9",
      "\u1F06\u03B9", "\u1F07\u03B9", "\u1F20\u03B9", "\u1F21\u03B9",
      "\u1F22\u03B9", "\u1F23\u03B9", "\u1F24\u03B9", "\u1F25\u03B9",
      "\u1F26\u03B9", "\u1F27\u03B9", "\u1F20\u03B9", "\u1F21\u03B9",
      "\u1F22\u03B9", "\u1F23\u03B9", "\u1F24\u03B9", "\u1F25\u03B9",
      "\u1F26\u03B9", "\u1F27\u03B9", "\u1F60\u03B9", "\u1F61\u03B9",
      "\u1F62\u03B9", "\u1F63\u03B9", "\u1F64\u03B9", "\u1F65\u03B9",
      "\u1F66\u03B9", "\u1F67\u03B9", "\u1F60\u03B9", "\u1F61\u03B9",
      "\u1F62\u03B9", "\u1F63\u03B9", "\u1F64\u03B9", "\u1F65\u03B9",
      "\u1F66\u03B9", "\u1F67\u03B9", "\u1F70\u03B9", "\u03B1\u03B9",
      "\u03AC\u03B9", "\u03B1\u0342", "\u03B1\u0342\u03B9", "\u1FB0",
      "\u1FB1", "\u1F70", "\u1F71", "\u03B1\u03B9", "\u03B9", "\u1F74\u03B9",
      "\u03B7\u03B9", "\u03AE\u03B9", "\u03B7\u0342", "\u03B7\u0342\u03B9",
      "\u1F72", "\u1F73", "\u1F74", "\u1F75", "\u03B7\u03B9",
      "\u03B9\u0308\u0300", "\u03B9\u0308\u0301", "\u03B9\u0342",
      "\u03B9\u0308\u0342", "\u1FD0", "\u1FD1", "\u1F76", "\u1F77",
      "\u03C5\u0308\u0300", "\u03C5\u0308\u0301", "\u03C1\u0313",
      "\u03C5\u0342", "\u03C5\u0308\u0342", "\u1FE0", "\u1FE1", "\u1F7A",
      "\u1F7B", "\u1FE5", "\u1F7C\u03B9", "\u03C9\u03B9", "\u03CE\u03B9",
      "\u03C9\u0342", "\u03C9\u0342\u03B9", "\u1F78", "\u1F79", "\u1F7C",
      "\u1F7D", "\u03C9\u03B9", "\u0072\u0073", "\u0063", "\u00B0\u0063",
      "\u025B", "\u00B0\u0066", "\u0068", "\u0068", "\u0068", "\u0069",
      "\u0069",
      "\u006C", "\u006E", "\u006E\u006F", "\u0070", "\u0071", "\u0072",
      "\u0072",
      "\u0072", "\u0073\u006D", "\u0074\u0065\u006C", "\u0074\u006D", "\u007A",
      "\u03C9", "\u007A", "\u006B", "\u00E5", "\u0062", "\u0063", "\u0065",
      "\u0066", "\u006D", "\u03B3", "\u03C0", "\u0064", "\u2170", "\u2171",
      "\u2172", "\u2173", "\u2174", "\u2175", "\u2176", "\u2177", "\u2178",
      "\u2179", "\u217A", "\u217B", "\u217C", "\u217D", "\u217E", "\u217F",
      "\u24D0", "\u24D1", "\u24D2", "\u24D3", "\u24D4", "\u24D5", "\u24D6",
      "\u24D7", "\u24D8", "\u24D9", "\u24DA", "\u24DB", "\u24DC", "\u24DD",
      "\u24DE", "\u24DF", "\u24E0", "\u24E1", "\u24E2", "\u24E3", "\u24E4",
      "\u24E5", "\u24E6", "\u24E7", "\u24E8", "\u24E9", "\u0068\u0070\u0061",
      "\u0061\u0075", "\u006F\u0076", "\u0070\u0061", "\u006E\u0061",
      "\u03BC\u0061", "\u006D\u0061", "\u006B\u0061", "\u006B\u0062",
      "\u006D\u0062", "\u0067\u0062", "\u0070\u0066", "\u006E\u0066",
      "\u03BC\u0066", "\u0068\u007A", "\u006B\u0068\u007A",
      "\u006D\u0068\u007A",
      "\u0067\u0068\u007A", "\u0074\u0068\u007A", "\u0070\u0061",
      "\u006B\u0070\u0061", "\u006D\u0070\u0061", "\u0067\u0070\u0061",
      "\u0070\u0076", "\u006E\u0076", "\u03BC\u0076", "\u006D\u0076",
      "\u006B\u0076", "\u006D\u0076", "\u0070\u0077", "\u006E\u0077",
      "\u03BC\u0077", "\u006D\u0077", "\u006B\u0077", "\u006D\u0077",
      "\u006B\u03C9", "\u006D\u03C9", "\u0062\u0071",
      "\u0063\u2215\u006B\u0067"
      , "\u0063\u006F\u002E", "\u0064\u0062", "\u0067\u0079", "\u0068\u0070",
      "\u006B\u006B", "\u006B\u006D", "\u0070\u0068", "\u0070\u0070\u006D",
      "\u0070\u0072", "\u0073\u0076", "\u0077\u0062", "\u0066\u0066",
      "\u0066\u0069", "\u0066\u006C", "\u0066\u0066\u0069",
      "\u0066\u0066\u006C"
     , "\u0073\u0074", "\u0073\u0074", "\u0574\u0576", "\u0574\u0565",
      "\u0574\u056B", "\u057E\u0576", "\u0574\u056D", "\uFF41", "\uFF42",
      "\uFF43", "\uFF44", "\uFF45", "\uFF46", "\uFF47", "\uFF48", "\uFF49",
      "\uFF4A", "\uFF4B", "\uFF4C", "\uFF4D", "\uFF4E", "\uFF4F", "\uFF50",
      "\uFF51", "\uFF52", "\uFF53", "\uFF54", "\uFF55", "\uFF56", "\uFF57",
      "\uFF58", "\uFF59", "\uFF5A"};
      for(int count=0;count<upperCaseArr.length;count++)
      {
        caseMappingTable.put(upperCaseArr[count], lowerCaseFoldedArr[count]);
      }
    }



    //Gets the mapped String.
    private static void map(StringBuilder buffer,
            ByteSequence sequence,
            boolean trim,
            boolean foldCase)
    {
      String value = sequence.toString();
      for(int i=0;i<value.length(); i++)
      {
        char c = value.charAt(i);
        if(map2null.contains(c))
        {
          continue;
        }

        if(map2space.contains(c))
        {
          int buffLen = buffer.length();
          if((trim &&  (buffLen ==0))
                  || (buffLen > 0 && buffer.charAt(buffLen-1) == SPACE_CHAR))
          {
            /** Do not map this character into a space if:
             * a . trimming is wanted and this was the first char.
             * b. The last character was a space. **/
            continue;
          }
          buffer.append(SPACE_CHAR);
          continue;
        }

        if(foldCase)
        {
          String mapping = caseMappingTable.get(c);
          if(mapping !=null)
          {
            buffer.append(mapping);
            continue;
          }
        }
        //It came here so no match was found.
        buffer.append(c);
      }
    }
  }
}
