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

package org.opends.sdk.tools;



import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.StringTokenizer;

import org.opends.messages.Message;
import org.opends.sdk.*;
import org.opends.sdk.controls.*;
import org.opends.sdk.ldif.*;
import org.opends.sdk.requests.AddRequest;
import org.opends.sdk.requests.DeleteRequest;
import org.opends.sdk.requests.ModifyDNRequest;
import org.opends.sdk.requests.ModifyRequest;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.server.util.cli.ConsoleApplication;



/**
 * This class provides a tool that can be used to issue modify requests
 * to the Directory Server.
 */
public final class LDAPModify extends ConsoleApplication
{
  private Connection connection;
  private EntryWriter writer;
  private Collection<Control> controls;
  private BooleanArgument verbose;



  /**
   * The main method for LDAPModify tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainModify(args, System.in, System.out, System.err);

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

  public static int mainModify(String[] args)
  {
    return mainModify(args, System.in, System.out, System.err);
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
  public static int mainModify(String[] args, InputStream inStream,
      OutputStream outStream, OutputStream errStream)
  {
    return new LDAPModify(inStream, outStream, errStream).run(args);
  }



  private LDAPModify(InputStream in, OutputStream out, OutputStream err)
  {
    super(in, out, err);

  }



  private int run(String[] args)
  {
    // Create the command-line argument parser for use with this
    // program.
    Message toolDescription = INFO_LDAPMODIFY_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
        new ArgumentParser(LDAPModify.class.getName(), toolDescription,
            false);
    ArgumentParserConnectionFactory connectionFactory;

    BooleanArgument continueOnError;
    // TODO: Remove this due to new LDIF reader api?
    BooleanArgument defaultAdd;
    BooleanArgument noop;
    BooleanArgument showUsage;
    IntegerArgument version;
    StringArgument assertionFilter;
    StringArgument controlStr;
    StringArgument encodingStr;
    StringArgument filename;
    StringArgument postReadAttributes;
    StringArgument preReadAttributes;
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

      defaultAdd =
          new BooleanArgument("defaultAdd", 'a', "defaultAdd",
              INFO_MODIFY_DESCRIPTION_DEFAULT_ADD.get());
      argParser.addArgument(defaultAdd);

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

      preReadAttributes =
          new StringArgument("prereadattrs", null, "preReadAttributes",
              false, false, true,
              INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_PREREAD_ATTRS.get());
      preReadAttributes.setPropertyName("preReadAttributes");
      argParser.addArgument(preReadAttributes);

      postReadAttributes =
          new StringArgument("postreadattrs", null,
              "postReadAttributes", false, false, true,
              INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_POSTREAD_ATTRS.get());
      postReadAttributes.setPropertyName("postReadAttributes");
      argParser.addArgument(postReadAttributes);

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

    // modifyOptions.setShowOperations(noop.isPresent());
    // modifyOptions.setVerbose(verbose.isPresent());
    // modifyOptions.setContinueOnError(continueOnError.isPresent());
    // modifyOptions.setEncoding(encodingStr.getValue());
    // modifyOptions.setDefaultAdd(defaultAdd.isPresent());

    controls = new LinkedList<Control>();
    if (controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        try
        {
          Control ctrl = Utils.getControl(ctrlString);
          controls.add(ctrl);
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
      controls.add(proxyControl);
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
        controls.add(assertionControl);
      }
      catch (LocalizedIllegalArgumentException le)
      {
        Message message =
            ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage());
        println(message);
        return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
      }
    }

    if (preReadAttributes.isPresent())
    {
      String valueStr = preReadAttributes.getValue();
      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      PreReadControl.Request control = new PreReadControl.Request(true);
      while (tokenizer.hasMoreTokens())
      {
        control.addAttribute(tokenizer.nextToken());
      }
      controls.add(control);
    }

    if (postReadAttributes.isPresent())
    {
      String valueStr = postReadAttributes.getValue();
      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      PostReadControl.Request control =
          new PostReadControl.Request(true);
      while (tokenizer.hasMoreTokens())
      {
        control.addAttribute(tokenizer.nextToken());
      }
      controls.add(control);
    }

    if (!noop.isPresent())
    {
      try
      {
        connection = connectionFactory.getConnection();
      }
      catch (ErrorResultException ere)
      {
        return Utils.printErrorMessage(this, ere);
      }
    }

    Utils.printPasswordPolicyResults(this, connection);

    writer = new LDIFEntryWriter(getOutputStream());
    VisitorImpl visitor = new VisitorImpl();
    try
    {
      ChangeRecordReader reader;
      if (filename.isPresent())
      {
        try
        {
          reader =
              new LDIFChangeRecordReader(new FileInputStream(filename
                  .getValue()));
        }
        catch (Exception e)
        {
          Message message =
              ERR_LDIF_FILE_CANNOT_OPEN_FOR_READ.get(filename
                  .getValue(), e.getLocalizedMessage());
          println(message);
          return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
      }
      else
      {
        reader = new LDIFChangeRecordReader(getInputStream());
      }

      ChangeRecord cr;
      try
      {
        int result;
        while ((cr = reader.readChangeRecord()) != null)
        {
          result = cr.accept(visitor, null);
          if (result != 0 && !continueOnError.isPresent())
          {
            return result;
          }
        }
      }
      catch (IOException ioe)
      {
        Message message =
            ERR_LDIF_FILE_READ_ERROR.get(filename.getValue(), ioe
                .getLocalizedMessage());
        println(message);
        return ResultCode.CLIENT_SIDE_LOCAL_ERROR.intValue();
      }
    }
    finally
    {
      if (connection != null)
      {
        connection.close();
      }
    }

    return ResultCode.SUCCESS.intValue();
  }



  private class VisitorImpl implements
      ChangeRecordVisitor<Integer, java.lang.Void>
  {
    private void printResult(String operationType, String name, Result r)
    {
      if (r.getResultCode() != ResultCode.SUCCESS
          && r.getResultCode() != ResultCode.REFERRAL)
      {
        Message msg = INFO_OPERATION_FAILED.get(operationType);
        println(msg);
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
      }
      else
      {
        Message msg =
            INFO_OPERATION_SUCCESSFUL.get(operationType, name);
        println(msg);
        if ((r.getDiagnosticMessage() != null)
            && (r.getDiagnosticMessage().length() > 0))
        {
          println(Message.raw(r.getDiagnosticMessage()));
        }
        if (r.getReferralURIs() != null)
        {
          for (String uri : r.getReferralURIs())
          {
            println(Message.raw(uri));
          }
        }
      }

      Control control =
          r.getControl(PreReadControl.OID_LDAP_READENTRY_PREREAD);
      if (control != null && control instanceof PreReadControl.Response)
      {
        PreReadControl.Response dc = (PreReadControl.Response) control;
        println(INFO_LDAPMODIFY_PREREAD_ENTRY.get());
        try
        {
          writer.writeEntry(dc.getSearchEntry());
        }
        catch (IOException ioe)
        {
          throw new RuntimeException(ioe);
        }
      }
      control =
          r.getControl(PostReadControl.OID_LDAP_READENTRY_POSTREAD);
      if (control != null
          && control instanceof PostReadControl.Response)
      {
        PostReadControl.Response dc =
            (PostReadControl.Response) control;
        println(INFO_LDAPMODIFY_POSTREAD_ENTRY.get());
        try
        {
          writer.writeEntry(dc.getSearchEntry());
        }
        catch (IOException ioe)
        {
          throw new RuntimeException(ioe);
        }
      }
      // TODO: CSN control
    }



    public Integer visitChangeRecord(Void aVoid, AddRequest change)
    {
      for (Control control : controls)
      {
        change.addControl(control);
      }
      String opType = "ADD";
      println(INFO_PROCESSING_OPERATION.get(opType, change.getName()
          .toString()));
      if (connection != null)
      {
        try
        {
          Result r;
          try
          {
            r = connection.add(change);
          }
          catch (InterruptedException e)
          {
            // This shouldn't happen because there are no other threads
            // to interrupt this one.
            r = Responses.newResult(
                ResultCode.CLIENT_SIDE_USER_CANCELLED).setCause(e)
                .setDiagnosticMessage(e.getLocalizedMessage());
            throw ErrorResultException.wrap(r);
          }
          printResult(opType, change.getName().toString(), r);
          return r.getResultCode().intValue();
        }
        catch (ErrorResultException ere)
        {
          return Utils.printErrorMessage(LDAPModify.this, ere);
        }
      }
      return ResultCode.SUCCESS.intValue();
    }



    public Integer visitChangeRecord(Void aVoid, DeleteRequest change)
    {
      for (Control control : controls)
      {
        change.addControl(control);
      }
      String opType = "DELETE";
      println(INFO_PROCESSING_OPERATION.get(opType, change.getName()
          .toString()));
      if (connection != null)
      {
        try
        {
          Result r;
          try
          {
            r = connection.delete(change);
          }
          catch (InterruptedException e)
          {
            // This shouldn't happen because there are no other threads
            // to interrupt this one.
            r = Responses.newResult(
                ResultCode.CLIENT_SIDE_USER_CANCELLED).setCause(e)
                .setDiagnosticMessage(e.getLocalizedMessage());
            throw ErrorResultException.wrap(r);
          }
          printResult(opType, change.getName().toString(), r);
          return r.getResultCode().intValue();
        }
        catch (ErrorResultException ere)
        {
          return Utils.printErrorMessage(LDAPModify.this, ere);
        }
      }
      return ResultCode.SUCCESS.intValue();
    }



    public Integer visitChangeRecord(Void aVoid, ModifyDNRequest change)
    {
      for (Control control : controls)
      {
        change.addControl(control);
      }
      String opType = "MODIFY DN";
      println(INFO_PROCESSING_OPERATION.get(opType, change.getName()
          .toString()));
      if (connection != null)
      {
        try
        {
          Result r;
          try
          {
            r = connection.modifyDN(change);
          }
          catch (InterruptedException e)
          {
            // This shouldn't happen because there are no other threads
            // to interrupt this one.
            r = Responses.newResult(
                ResultCode.CLIENT_SIDE_USER_CANCELLED).setCause(e)
                .setDiagnosticMessage(e.getLocalizedMessage());
            throw ErrorResultException.wrap(r);
          }
          printResult(opType, change.getName().toString(), r);
          return r.getResultCode().intValue();
        }
        catch (ErrorResultException ere)
        {
          return Utils.printErrorMessage(LDAPModify.this, ere);
        }
      }
      return ResultCode.SUCCESS.intValue();
    }



    public Integer visitChangeRecord(Void aVoid, ModifyRequest change)
    {
      for (Control control : controls)
      {
        change.addControl(control);
      }
      String opType = "MODIFY";
      println(INFO_PROCESSING_OPERATION.get(opType, change.getName()
          .toString()));
      if (connection != null)
      {
        try
        {
          Result r;
          try
          {
            r = connection.modify(change);
          }
          catch (InterruptedException e)
          {
            // This shouldn't happen because there are no other threads
            // to interrupt this one.
            r = Responses.newResult(
                ResultCode.CLIENT_SIDE_USER_CANCELLED).setCause(e)
                .setDiagnosticMessage(e.getLocalizedMessage());
            throw ErrorResultException.wrap(r);
          }
          printResult(opType, change.getName().toString(), r);
          return r.getResultCode().intValue();
        }
        catch (ErrorResultException ere)
        {
          return Utils.printErrorMessage(LDAPModify.this, ere);
        }
      }
      return ResultCode.SUCCESS.intValue();
    }
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
