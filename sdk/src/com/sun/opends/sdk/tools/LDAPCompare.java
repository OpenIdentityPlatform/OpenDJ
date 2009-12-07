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

package com.sun.opends.sdk.tools;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.tools.ToolConstants.*;
import static com.sun.opends.sdk.tools.Utils.*;

import java.io.*;
import java.util.ArrayList;

import org.opends.sdk.*;
import org.opends.sdk.controls.AssertionControl;
import org.opends.sdk.controls.Control;
import org.opends.sdk.controls.ProxiedAuthV2Control;
import org.opends.sdk.requests.CompareRequest;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;

import com.sun.opends.sdk.util.Base64;
import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;
import com.sun.opends.sdk.util.Message;



/**
 * A tool that can be used to issue Compare requests to the Directory
 * Server.
 */
public final class LDAPCompare extends ConsoleApplication
{
  private BooleanArgument verbose;



  /**
   * The main method for LDAPModify tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCompare(args, System.in, System.out, System.err);

    if (retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }



  /**
   * Parses the provided command-line arguments and uses that
   * information to run the LDAPModify tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @return The error code.
   */

  static int mainCompare(String[] args)
  {
    return mainCompare(args, System.in, System.out, System.err);
  }



  /**
   * Parses the provided command-line arguments and uses that
   * information to run the LDAPModify tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   *          specified, the number of matching entries should be
   *          returned or not.
   * @param inStream
   *          The input stream to use for standard input, or
   *          <CODE>null</CODE> if standard input is not needed.
   * @param outStream
   *          The output stream to use for standard output, or
   *          <CODE>null</CODE> if standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or
   *          <CODE>null</CODE> if standard error is not needed.
   * @return The error code.
   */
  static int mainCompare(String[] args, InputStream inStream,
      OutputStream outStream, OutputStream errStream)
  {
    return new LDAPCompare(inStream, outStream, errStream).run(args);
  }



  private LDAPCompare(InputStream in, OutputStream out, OutputStream err)
  {
    super(in, out, err);

  }



  private int run(String[] args)
  {
    // Create the command-line argument parser for use with this
    // program.
    Message toolDescription = INFO_LDAPCOMPARE_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
        new ArgumentParser(LDAPCompare.class.getName(),
            toolDescription, false, true, 1, 0,
            "attribute:value [DN ...]");
    ArgumentParserConnectionFactory connectionFactory;

    BooleanArgument continueOnError;
    BooleanArgument noop;
    BooleanArgument showUsage;
    IntegerArgument version;
    StringArgument assertionFilter;
    StringArgument controlStr;
    StringArgument encodingStr;
    StringArgument filename;
    StringArgument proxyAuthzID;
    StringArgument propertiesFileArgument;
    BooleanArgument noPropertiesFileArgument;

    try
    {
      connectionFactory =
          new ArgumentParserConnectionFactory(argParser, this);
      propertiesFileArgument =
          new StringArgument("propertiesFilePath", null,
              OPTION_LONG_PROP_FILE_PATH, false, false, true,
              INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument =
          new BooleanArgument("noPropertiesFileArgument", null,
              OPTION_LONG_NO_PROP_FILE, INFO_DESCRIPTION_NO_PROP_FILE
                  .get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      filename =
          new StringArgument("filename", OPTION_SHORT_FILENAME,
              OPTION_LONG_FILENAME, false, false, true,
              INFO_FILE_PLACEHOLDER.get(), null, null,
              INFO_LDAPMODIFY_DESCRIPTION_FILENAME.get());
      filename.setPropertyName(OPTION_LONG_FILENAME);
      argParser.addArgument(filename);

      proxyAuthzID =
          new StringArgument("proxy_authzid", OPTION_SHORT_PROXYAUTHID,
              OPTION_LONG_PROXYAUTHID, false, false, true,
              INFO_PROXYAUTHID_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_PROXY_AUTHZID.get());
      proxyAuthzID.setPropertyName(OPTION_LONG_PROXYAUTHID);
      argParser.addArgument(proxyAuthzID);

      assertionFilter =
          new StringArgument("assertionfilter", null,
              OPTION_LONG_ASSERTION_FILE, false, false, true,
              INFO_ASSERTION_FILTER_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_ASSERTION_FILTER.get());
      assertionFilter.setPropertyName(OPTION_LONG_ASSERTION_FILE);
      argParser.addArgument(assertionFilter);

      controlStr =
          new StringArgument("control", 'J', "control", false, true,
              true, INFO_LDAP_CONTROL_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_CONTROLS.get());
      controlStr.setPropertyName("control");
      argParser.addArgument(controlStr);

      version =
          new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
              OPTION_LONG_PROTOCOL_VERSION, false, false, true,
              INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
              INFO_DESCRIPTION_VERSION.get());
      version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
      argParser.addArgument(version);

      encodingStr =
          new StringArgument("encoding", 'i', "encoding", false, false,
              true, INFO_ENCODING_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_ENCODING.get());
      encodingStr.setPropertyName("encoding");
      argParser.addArgument(encodingStr);

      continueOnError =
          new BooleanArgument("continueOnError", 'c',
              "continueOnError", INFO_DESCRIPTION_CONTINUE_ON_ERROR
                  .get());
      continueOnError.setPropertyName("continueOnError");
      argParser.addArgument(continueOnError);

      noop =
          new BooleanArgument("no-op", OPTION_SHORT_DRYRUN,
              OPTION_LONG_DRYRUN, INFO_DESCRIPTION_NOOP.get());
      noop.setPropertyName(OPTION_LONG_DRYRUN);
      argParser.addArgument(noop);

      verbose =
          new BooleanArgument("verbose", 'v', "verbose",
              INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);

      showUsage =
          new BooleanArgument("showUsage", OPTION_SHORT_HELP,
              OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, getOutputStream());
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      connectionFactory.validate();
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
      println(message);
      println(argParser.getUsageMessage());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    try
    {
      int versionNumber = version.getIntValue();
      if (versionNumber != 2 && versionNumber != 3)
      {
        println(ERR_DESCRIPTION_INVALID_VERSION.get(String
            .valueOf(versionNumber)));
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }
    catch (ArgumentException ae)
    {
      println(ERR_DESCRIPTION_INVALID_VERSION.get(String
          .valueOf(version.getValue())));
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    ArrayList<String> dnStrings = new ArrayList<String>();
    ArrayList<String> attrAndDNStrings =
        argParser.getTrailingArguments();

    if (attrAndDNStrings.isEmpty())
    {
      Message message = ERR_LDAPCOMPARE_NO_ATTR.get();
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // First element should be an attribute string.
    String attributeString = attrAndDNStrings.remove(0);

    // Rest are DN strings
    for (String s : attrAndDNStrings)
    {
      dnStrings.add(s);
    }

    // If no DNs were provided, then exit with an error.
    if (dnStrings.isEmpty() && (!filename.isPresent()))
    {
      println(ERR_LDAPCOMPARE_NO_DNS.get());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // If trailing DNs were provided and the filename argument was also
    // provided, exit with an error.
    if (!dnStrings.isEmpty() && filename.isPresent())
    {
      println(ERR_LDAPCOMPARE_FILENAME_AND_DNS.get());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // parse the attribute string
    int idx = attributeString.indexOf(":");
    if (idx == -1)
    {
      Message message =
          ERR_LDAPCOMPARE_INVALID_ATTR_STRING.get(attributeString);
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }
    String attributeType = attributeString.substring(0, idx);
    ByteString attributeVal;
    String remainder =
        attributeString.substring(idx + 1, attributeString.length());
    if (remainder.length() > 0)
    {
      char nextChar = remainder.charAt(0);
      if (nextChar == ':')
      {
        String base64 = remainder.substring(1, remainder.length());
        try
        {
          attributeVal = Base64.decode(base64);
        }
        catch (LocalizedIllegalArgumentException e)
        {
          println(INFO_COMPARE_CANNOT_BASE64_DECODE_ASSERTION_VALUE
              .get());
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
      else if (nextChar == '<')
      {
        try
        {
          String filePath = remainder.substring(1, remainder.length());
          attributeVal =
              ByteString.wrap(Utils.readBytesFromFile(filePath));
        }
        catch (Exception e)
        {
          println(INFO_COMPARE_CANNOT_READ_ASSERTION_VALUE_FROM_FILE
              .get(String.valueOf(e)));
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
      else
      {
        attributeVal = ByteString.valueOf(remainder);
      }
    }
    else
    {
      attributeVal = ByteString.valueOf(remainder);
    }

    CompareRequest compare =
        Requests.newCompareRequest("", attributeType, attributeVal);

    if (controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        try
        {
          Control ctrl = Utils.getControl(ctrlString);
          compare.addControl(ctrl);
        }
        catch (DecodeException de)
        {
          Message message =
              ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString);
          println(message);
          ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
    }

    if (proxyAuthzID.isPresent())
    {
      Control proxyControl =
          new ProxiedAuthV2Control(proxyAuthzID.getValue());
      compare.addControl(proxyControl);
    }

    if (assertionFilter.isPresent())
    {
      String filterString = assertionFilter.getValue();
      Filter filter;
      try
      {
        filter = Filter.valueOf(filterString);

        // FIXME -- Change this to the correct OID when the official one
        // is
        // assigned.
        Control assertionControl = new AssertionControl(true, filter);
        compare.addControl(assertionControl);
      }
      catch (LocalizedIllegalArgumentException le)
      {
        Message message =
            ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage());
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }

    BufferedReader rdr = null;
    if (!filename.isPresent() && dnStrings.isEmpty())
    {
      // Read from stdin.
      rdr = new BufferedReader(new InputStreamReader(System.in));
    }
    else if (filename.isPresent())
    {
      try
      {
        rdr = new BufferedReader(new FileReader(filename.getValue()));
      }
      catch (FileNotFoundException t)
      {
        println(ERR_LDAPCOMPARE_ERROR_READING_FILE.get(filename
            .getValue(), t.toString()));
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }

    Connection connection = null;
    if (!noop.isPresent())
    {
      try
      {
        connection = connectionFactory.getConnection();
      }
      catch (ErrorResultException ere)
      {
        println(Message.raw(ere.getMessage()));
        return ere.getResult().getResultCode().intValue();
      }
    }

    try
    {
      int result;
      if (rdr == null)
      {
        for (String dn : dnStrings)
        {
          compare.setName(dn);
          result = executeCompare(compare, connection);
          if (result != 0 && !continueOnError.isPresent())
          {
            return result;
          }
        }
      }
      else
      {
        String dn;
        try
        {
          while ((dn = rdr.readLine()) != null)
          {
            compare.setName(dn);
            result = executeCompare(compare, connection);
            if (result != 0 && !continueOnError.isPresent())
            {
              return result;
            }
          }
        }
        catch (IOException ioe)
        {
          println(ERR_LDAPCOMPARE_ERROR_READING_FILE.get(filename
              .getValue(), ioe.toString()));
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
      if (rdr != null)
      {
        try
        {
          rdr.close();
        }
        catch (IOException ioe)
        {
          // Just ignore
        }
      }
    }

    return 0;
  }



  private int executeCompare(CompareRequest request,
      Connection connection)
  {
    println(INFO_PROCESSING_COMPARE_OPERATION.get(request
        .getAttributeDescription().toString(), request
        .getAssertionValueAsString(), request.getName().toString()));
    if (connection != null)
    {
      try
      {
        Result result;
        try
        {
          result = connection.compare(request);
        }
        catch (InterruptedException e)
        {
          // This shouldn't happen because there are no other threads to
          // interrupt this one.
          result = Responses.newResult(
              ResultCode.CLIENT_SIDE_USER_CANCELLED).setCause(e)
              .setDiagnosticMessage(e.getLocalizedMessage());
          throw ErrorResultException.wrap(result);
        }

        if (result.getResultCode() == ResultCode.COMPARE_FALSE)
        {
          println(INFO_COMPARE_OPERATION_RESULT_FALSE.get(request
              .getName().toString()));
        }
        else
        {

          println(INFO_COMPARE_OPERATION_RESULT_TRUE.get(request
              .getName().toString()));
        }
      }
      catch (ErrorResultException ere)
      {
        Message msg = INFO_OPERATION_FAILED.get("COMPARE");
        println(msg);
        Result r = ere.getResult();
        println(ERR_TOOL_RESULT_CODE.get(r.getResultCode().intValue(),
            r.getResultCode().toString()));
        if ((r.getDiagnosticMessage() != null)
            && (r.getDiagnosticMessage().length() > 0))
        {
          println(Message.raw(r.getDiagnosticMessage()));
        }
        if (r.getMatchedDN() != null && r.getMatchedDN().length() > 0)
        {
          println(ERR_TOOL_MATCHED_DN.get(r.getMatchedDN()));
        }
        return r.getResultCode().intValue();
      }
    }
    return ResultCode.SUCCESS.intValue();
  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested
   *         advanced mode.
   */
  public boolean isAdvancedMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested interactive
   * behavior.
   *
   * @return Returns <code>true</code> if the user has requested
   *         interactive behavior.
   */
  public boolean isInteractive()
  {
    return false;
  }



  /**
   * Indicates whether or not this console application is running in its
   * menu-driven mode. This can be used to dictate whether output should
   * go to the error stream or not. In addition, it may also dictate
   * whether or not sub-menus should display a cancel option as well as
   * a quit option.
   *
   * @return Returns <code>true</code> if this console application is
   *         running in its menu-driven mode.
   */
  public boolean isMenuDrivenMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested quiet
   *         output.
   */
  public boolean isQuiet()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested script-friendly
   * output.
   *
   * @return Returns <code>true</code> if the user has requested
   *         script-friendly output.
   */
  public boolean isScriptFriendly()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested verbose
   *         output.
   */
  public boolean isVerbose()
  {
    return verbose.isPresent();
  }

}
