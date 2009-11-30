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
package org.opends.sdk.tools;



import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.UtilityMessages.INFO_TIME_IN_DAYS_HOURS_MINUTES_SECONDS;
import static org.opends.messages.UtilityMessages.INFO_TIME_IN_HOURS_MINUTES_SECONDS;
import static org.opends.messages.UtilityMessages.INFO_TIME_IN_MINUTES_SECONDS;
import static org.opends.messages.UtilityMessages.INFO_TIME_IN_SECONDS;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.opends.messages.Message;
import org.opends.sdk.Connection;
import org.opends.sdk.DecodeException;
import org.opends.sdk.ErrorResultException;
import org.opends.sdk.AuthenticatedConnectionFactory.AuthenticatedConnection;
import org.opends.sdk.controls.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.StaticUtils;
import org.opends.server.util.cli.ConsoleApplication;



/**
 * This class provides utility functions for all the client side tools.
 */
final class Utils
{
  // Prevent instantiation.
  private Utils()
  {
    // Do nothing.
  }



  /**
   * Parse the specified command line argument to create the appropriate
   * LDAPControl. The argument string should be in the format
   * controloid[:criticality[:value|::b64value|:&lt;fileurl]]
   *
   * @param argString
   *          The argument string containing the encoded control
   *          information.
   * @return The control decoded from the provided string, or
   *         <CODE>null</CODE> if an error occurs while parsing the
   *         argument value.
   * @throws org.opends.sdk.DecodeException
   *           If an error occurs.
   */
  public static GenericControl getControl(String argString)
      throws DecodeException
  {
    String controlOID = null;
    boolean controlCriticality = false;
    ByteString controlValue = null;

    int idx = argString.indexOf(":");

    if (idx < 0)
    {
      controlOID = argString;
    }
    else
    {
      controlOID = argString.substring(0, idx);
    }

    String lowerOID = StaticUtils.toLowerCase(controlOID);
    if (lowerOID.equals("accountusable")
        || lowerOID.equals("accountusability"))
    {
      controlOID = AccountUsabilityControl.OID_ACCOUNT_USABLE_CONTROL;
    }
    else if (lowerOID.equals("authzid")
        || lowerOID.equals("authorizationidentity"))
    {
      controlOID = AuthorizationIdentityControl.OID_AUTHZID_REQUEST;
    }
    else if (lowerOID.equals("noop") || lowerOID.equals("no-op"))
    {
      // controlOID = OID_LDAP_NOOP_OPENLDAP_ASSIGNED;
    }
    else if (lowerOID.equals("subentries"))
    {
      // controlOID = OID_LDAP_SUBENTRIES;
    }
    else if (lowerOID.equals("managedsait"))
    {
      // controlOID = OID_MANAGE_DSAIT_CONTROL;
    }
    else if (lowerOID.equals("pwpolicy")
        || lowerOID.equals("passwordpolicy"))
    {
      controlOID = PasswordPolicyControl.OID_PASSWORD_POLICY_CONTROL;
    }
    else if (lowerOID.equals("subtreedelete")
        || lowerOID.equals("treedelete"))
    {
      controlOID = SubtreeDeleteControl.OID_SUBTREE_DELETE_CONTROL;
    }
    else if (lowerOID.equals("realattrsonly")
        || lowerOID.equals("realattributesonly"))
    {
      // controlOID = OID_REAL_ATTRS_ONLY;
    }
    else if (lowerOID.equals("virtualattrsonly")
        || lowerOID.equals("virtualattributesonly"))
    {
      // controlOID = OID_VIRTUAL_ATTRS_ONLY;
    }
    else if (lowerOID.equals("effectiverights")
        || lowerOID.equals("geteffectiverights"))
    {
      controlOID = GetEffectiveRightsRequestControl.OID_GET_EFFECTIVE_RIGHTS;
    }

    if (idx < 0)
    {
      return new GenericControl(controlOID);
    }

    String remainder = argString.substring(idx + 1, argString.length());

    idx = remainder.indexOf(":");
    if (idx == -1)
    {
      if (remainder.equalsIgnoreCase("true"))
      {
        controlCriticality = true;
      }
      else if (remainder.equalsIgnoreCase("false"))
      {
        controlCriticality = false;
      }
      else
      {
        // TODO: I18N
        throw DecodeException.error(Message
            .raw("Invalid format for criticality value:" + remainder));
      }
      return new GenericControl(controlOID, controlCriticality);

    }

    String critical = remainder.substring(0, idx);
    if (critical.equalsIgnoreCase("true"))
    {
      controlCriticality = true;
    }
    else if (critical.equalsIgnoreCase("false"))
    {
      controlCriticality = false;
    }
    else
    {
      // TODO: I18N
      throw DecodeException.error(Message
          .raw("Invalid format for criticality value:" + critical));
    }

    String valString = remainder.substring(idx + 1, remainder.length());
    if (valString.charAt(0) == ':')
    {
      controlValue = ByteString.valueOf(valString.substring(1,
          valString.length()));
    }
    else if (valString.charAt(0) == '<')
    {
      // Read data from the file.
      String filePath = valString.substring(1, valString.length());
      try
      {
        byte[] val = readBytesFromFile(filePath);
        controlValue = ByteString.wrap(val);
      }
      catch (Exception e)
      {
        return null;
      }
    }
    else
    {
      controlValue = ByteString.valueOf(valString);
    }

    return new GenericControl(controlOID, controlCriticality,
        controlValue);
  }



  /**
   * Read the data from the specified file and return it in a byte
   * array.
   *
   * @param filePath
   *          The path to the file that should be read.
   * @return A byte array containing the contents of the requested file.
   * @throws IOException
   *           If a problem occurs while trying to read the specified
   *           file.
   */
  public static byte[] readBytesFromFile(String filePath)
      throws IOException
  {
    byte[] val = null;
    FileInputStream fis = null;
    try
    {
      File file = new File(filePath);
      fis = new FileInputStream(file);
      long length = file.length();
      val = new byte[(int) length];
      // Read in the bytes
      int offset = 0;
      int numRead = 0;
      while (offset < val.length
          && (numRead = fis.read(val, offset, val.length - offset)) >= 0)
      {
        offset += numRead;
      }

      // Ensure all the bytes have been read in
      if (offset < val.length)
      {
        throw new IOException("Could not completely read file "
            + filePath);
      }

      return val;
    }
    finally
    {
      if (fis != null)
      {
        fis.close();
      }
    }
  }



  /**
   * Prints a multi-line error message with the provided information to
   * the given print stream.
   *
   * @param app
   *          The console app to use to write the error message.
   * @param ere
   *          The error result.
   * @return The error code.
   */
  public static int printErrorMessage(ConsoleApplication app,
      ErrorResultException ere)
  {
    // if ((ere.getMessage() != null) && (ere.getMessage().length() >
    // 0))
    // {
    // app.println(Message.raw(ere.getMessage()));
    // }

    if (ere.getResult().getResultCode().intValue() >= 0)
    {
      app.println(ERR_TOOL_RESULT_CODE.get(ere.getResult()
          .getResultCode().intValue(), ere.getResult().getResultCode()
          .toString()));
    }

    if ((ere.getResult().getDiagnosticMessage() != null)
        && (ere.getResult().getDiagnosticMessage().length() > 0))
    {
      app.println(ERR_TOOL_ERROR_MESSAGE.get(ere.getResult()
          .getDiagnosticMessage()));
    }

    if (ere.getResult().getMatchedDN() != null
        && ere.getResult().getMatchedDN().length() > 0)
    {
      app.println(ERR_TOOL_MATCHED_DN.get(ere.getResult()
          .getMatchedDN()));
    }

    if (app.isVerbose() && ere.getResult().getCause() != null)
    {
      ere.getResult().getCause().printStackTrace(app.getErrorStream());
    }

    return ere.getResult().getResultCode().intValue();
  }



  /**
   * Retrieves a user-friendly string that indicates the length of time
   * (in days, hours, minutes, and seconds) in the specified number of
   * seconds.
   *
   * @param numSeconds
   *          The number of seconds to be converted to a more
   *          user-friendly value.
   * @return The user-friendly representation of the specified number of
   *         seconds.
   */
  public static Message secondsToTimeString(int numSeconds)
  {
    if (numSeconds < 60)
    {
      // We can express it in seconds.
      return INFO_TIME_IN_SECONDS.get(numSeconds);
    }
    else if (numSeconds < 3600)
    {
      // We can express it in minutes and seconds.
      int m = numSeconds / 60;
      int s = numSeconds % 60;
      return INFO_TIME_IN_MINUTES_SECONDS.get(m, s);
    }
    else if (numSeconds < 86400)
    {
      // We can express it in hours, minutes, and seconds.
      int h = numSeconds / 3600;
      int m = (numSeconds % 3600) / 60;
      int s = numSeconds % 3600 % 60;
      return INFO_TIME_IN_HOURS_MINUTES_SECONDS.get(h, m, s);
    }
    else
    {
      // We can express it in days, hours, minutes, and seconds.
      int d = numSeconds / 86400;
      int h = (numSeconds % 86400) / 3600;
      int m = (numSeconds % 86400 % 3600) / 60;
      int s = numSeconds % 86400 % 3600 % 60;
      return INFO_TIME_IN_DAYS_HOURS_MINUTES_SECONDS.get(d, h, m, s);
    }
  }



  public static void printPasswordPolicyResults(ConsoleApplication app,
      Connection connection)
  {
    if (connection instanceof AuthenticatedConnection)
    {
      AuthenticatedConnection conn = (AuthenticatedConnection) connection;
      BindResult result = conn.getAuthenticatedBindResult();

      Control control = result
          .getControl(AuthorizationIdentityControl.OID_AUTHZID_RESPONSE);
      if (control != null)
      {
        AuthorizationIdentityControl.Response dc = (AuthorizationIdentityControl.Response) control;
        Message message = INFO_BIND_AUTHZID_RETURNED.get(dc
            .getAuthorizationID());
        app.println(message);
      }
      control = result
          .getControl(PasswordExpiredControl.OID_NS_PASSWORD_EXPIRED);
      if (control != null)
      {
        Message message = INFO_BIND_PASSWORD_EXPIRED.get();
        app.println(message);
      }
      control = result
          .getControl(PasswordExpiringControl.OID_NS_PASSWORD_EXPIRING);
      if (control != null)
      {
        PasswordExpiringControl dc = (PasswordExpiringControl) control;
        Message timeString = Utils.secondsToTimeString(dc
            .getSecondsUntilExpiration());
        Message message = INFO_BIND_PASSWORD_EXPIRING.get(timeString);
        app.println(message);
      }
      control = result
          .getControl(PasswordPolicyControl.OID_PASSWORD_POLICY_CONTROL);
      if (control != null)
      {
        PasswordPolicyControl.Response dc = (PasswordPolicyControl.Response) control;
        PasswordPolicyErrorType errorType = dc.getErrorType();
        if (errorType == PasswordPolicyErrorType.PASSWORD_EXPIRED)
        {
          Message message = INFO_BIND_PASSWORD_EXPIRED.get();
          app.println(message);
        }
        else if (errorType == PasswordPolicyErrorType.ACCOUNT_LOCKED)
        {
          Message message = INFO_BIND_ACCOUNT_LOCKED.get();
          app.println(message);
        }
        else if (errorType == PasswordPolicyErrorType.CHANGE_AFTER_RESET)
        {

          Message message = INFO_BIND_MUST_CHANGE_PASSWORD.get();
          app.println(message);
        }

        PasswordPolicyWarningType warningType = dc.getWarningType();
        if (warningType == PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION)
        {
          Message timeString = Utils.secondsToTimeString(dc
              .getWarningValue());
          Message message = INFO_BIND_PASSWORD_EXPIRING.get(timeString);
          app.println(message);
        }
        else if (warningType == PasswordPolicyWarningType.GRACE_LOGINS_REMAINING)
        {
          Message message = INFO_BIND_GRACE_LOGINS_REMAINING.get(dc
              .getWarningValue());
          app.println(message);
        }
      }
    }
  }



  /**
   * Filters the provided value to ensure that it is appropriate for use
   * as an exit code. Exit code values are generally only allowed to be
   * between 0 and 255, so any value outside of this range will be
   * converted to 255, which is the typical exit code used to indicate
   * an overflow value.
   *
   * @param exitCode
   *          The exit code value to be processed.
   * @return An integer value between 0 and 255, inclusive. If the
   *         provided exit code was already between 0 and 255, then the
   *         original value will be returned. If the provided value was
   *         out of this range, then 255 will be returned.
   */
  public static int filterExitCode(int exitCode)
  {
    if (exitCode < 0)
    {
      return 255;
    }
    else if (exitCode > 255)
    {
      return 255;
    }
    else
    {
      return exitCode;
    }
  }
}
