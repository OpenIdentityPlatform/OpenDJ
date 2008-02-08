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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.DN;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides utility functions for all the client side tools.
 */
public class LDAPToolUtils
{


  /**
   * Parse the specified command line argument to create the
   * appropriate LDAPControl. The argument string should be in the format
   * controloid[:criticality[:value|::b64value|:&lt;fileurl]]
   *
   * @param  argString  The argument string containing the encoded control
   *                    information.
   * @param  err        A print stream to which error messages should be
   *                    written if a problem occurs.
   *
   * @return  The control decoded from the provided string, or <CODE>null</CODE>
   *          if an error occurs while parsing the argument value.
   */
  public static LDAPControl getControl(String argString, PrintStream err)
  {
    LDAPControl control = null;
    String controlOID = null;
    boolean controlCriticality = false;
    ASN1OctetString controlValue = null;

    int idx = argString.indexOf(":");

    if(idx < 0)
    {
      controlOID = argString;
    }
    else
    {
      controlOID = argString.substring(0, idx);
    }

    String lowerOID = toLowerCase(controlOID);
    if (lowerOID.equals("accountusable") || lowerOID.equals("accountusability"))
    {
      controlOID = OID_ACCOUNT_USABLE_CONTROL;
    }
    else if (lowerOID.equals("authzid") ||
             lowerOID.equals("authorizationidentity"))
    {
      controlOID = OID_AUTHZID_REQUEST;
    }
    else if (lowerOID.equals("noop") || lowerOID.equals("no-op"))
    {
      controlOID = OID_LDAP_NOOP_OPENLDAP_ASSIGNED;
    }
    else if (lowerOID.equals("subentries"))
    {
      controlOID = OID_LDAP_SUBENTRIES;
    }
    else if (lowerOID.equals("managedsait"))
    {
      controlOID = OID_MANAGE_DSAIT_CONTROL;
    }
    else if (lowerOID.equals("pwpolicy") || lowerOID.equals("passwordpolicy"))
    {
      controlOID = OID_PASSWORD_POLICY_CONTROL;
    }
    else if (lowerOID.equals("subtreedelete") || lowerOID.equals("treedelete"))
    {
      controlOID = OID_SUBTREE_DELETE_CONTROL;
    }
    else if (lowerOID.equals("realattrsonly") ||
             lowerOID.equals("realattributesonly"))
    {
      controlOID = OID_REAL_ATTRS_ONLY;
    }
    else if (lowerOID.equals("virtualattrsonly") ||
             lowerOID.equals("virtualattributesonly"))
    {
      controlOID = OID_VIRTUAL_ATTRS_ONLY;
    }
    else if(lowerOID.equals("effectiverights") ||
              lowerOID.equals("geteffectiverights"))
    {
      controlOID = OID_GET_EFFECTIVE_RIGHTS;
    }

    if (idx < 0)
    {
      return new LDAPControl(controlOID);
    }

    String remainder = argString.substring(idx+1, argString.length());

    idx = remainder.indexOf(":");
    if(idx == -1)
    {
      if(remainder.equalsIgnoreCase("true"))
      {
        controlCriticality = true;
      } else if(remainder.equalsIgnoreCase("false"))
      {
        controlCriticality = false;
      } else
      {
        err.println("Invalid format for criticality value:" + remainder);
        return null;
      }
      control = new LDAPControl(controlOID, controlCriticality);
      return control;

    }

    String critical = remainder.substring(0, idx);
    if(critical.equalsIgnoreCase("true"))
    {
      controlCriticality = true;
    } else if(critical.equalsIgnoreCase("false"))
    {
      controlCriticality = false;
    } else
    {
      err.println("Invalid format for criticality value:" + critical);
      return null;
    }

    String valString = remainder.substring(idx+1, remainder.length());
    if(valString.charAt(0) == ':')
    {
      controlValue =
           new ASN1OctetString(valString.substring(1, valString.length()));
    } else if(valString.charAt(0) == '<')
    {
      // Read data from the file.
      String filePath = valString.substring(1, valString.length());
      try
      {
        byte[] val = readBytesFromFile(filePath, err);
        controlValue = new ASN1OctetString(val);
      }
      catch (Exception e)
      {
        return null;
      }
    } else
    {
      controlValue = new ASN1OctetString(valString);
    }

    control = new LDAPControl(controlOID, controlCriticality, controlValue);
    return control;

  }

  /**
   * Read the data from the specified file and return it in a byte array.
   *
   * @param  filePath   The path to the file that should be read.
   * @param  err        A print stream to which error messages should be
   *                    written if a problem occurs.
   *
   * @return  A byte array containing the contents of the requested file.
   *
   * @throws  IOException  If a problem occurs while trying to read the
   *                       specified file.
   */
  public static byte[] readBytesFromFile(String filePath, PrintStream err)
         throws IOException
  {
      byte[] val = null;
      FileInputStream fis = null;
      try
      {
        File file = new File(filePath);
        fis = new FileInputStream (file);
        long length = file.length();
        val = new byte[(int)length];
        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < val.length &&
               (numRead=fis.read(val, offset, val.length-offset)) >= 0) {
          offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < val.length)
        {
          err.println("Could not completely read file "+filePath);
          return null;
        }

        return val;
      } finally
      {
        if (fis != null)
        {
          fis.close();
        }
      }
  }

  /**
   * Prints a multi-line error message with the provided information to the
   * given print stream.
   *
   * @param  err           The print stream to use to write the error message.
   * @param  explanation   The general explanation to provide to the user, or
   *                       {@code null} if there is none.
   * @param  resultCode    The result code returned from the server, or -1 if
   *                       there is none.
   * @param  errorMessage  The additional information / error message returned
   *                       from the server, or {@code null} if there was none.
   * @param  matchedDN     The matched DN returned from the server, or
   *                       {@code null} if there was none.
   */
  public static void printErrorMessage(PrintStream err, Message explanation,
                                       int resultCode, Message errorMessage,
                                       DN matchedDN)
  {
    if ((explanation != null) && (explanation.length() > 0))
    {
      err.println(explanation);
    }

    if (resultCode >= 0)
    {
      err.println(ERR_TOOL_RESULT_CODE.get(resultCode,
                             LDAPResultCode.toString(resultCode)));
    }

    if ((errorMessage != null) && (errorMessage.length() > 0))
    {
      err.println(ERR_TOOL_ERROR_MESSAGE.get(errorMessage));
    }

    if (matchedDN != null)
    {
      err.println(ERR_TOOL_MATCHED_DN.get(matchedDN.toString()));
    }
  }
}

