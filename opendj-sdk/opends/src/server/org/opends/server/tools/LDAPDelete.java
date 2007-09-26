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
package org.opends.server.tools;
import org.opends.messages.Message;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LDAPException;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;


/**
 * This class provides a tool that can be used to issue delete requests to the
 * Directory Server.
 */
public class LDAPDelete
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPDelete";



  // The message ID counter to use for requests.
  private AtomicInteger nextMessageID;

  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;



  /**
   * Constructor for the LDAPDelete object.
   *
   * @param  nextMessageID  The next message ID to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPDelete(AtomicInteger nextMessageID, PrintStream out,
                    PrintStream err)
  {
    this.nextMessageID = nextMessageID;
    this.out           = out;
    this.err           = err;
  }

  /**
   * Execute the delete request on the specified list of DNs.
   *
   * @param connection        The connection to use to execute the request.
   * @param lines             The list of DNs to delete.
   * @param deleteOptions     The constraints to use for this request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection,
                             ArrayList<String> lines,
                             LDAPDeleteOptions deleteOptions)
    throws IOException, LDAPException
  {
    for(String line : lines)
    {
      executeDelete(connection, line, deleteOptions);
    }
  }

  /**
   * Read the specified DNs from the given reader
   * (file or stdin) and execute the given delete request.
   *
   * @param connection        The connection to use to execute the request.
   * @param reader            The reader to read the list of DNs from.
   * @param deleteOptions     The constraints to use for this request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection, Reader reader,
                             LDAPDeleteOptions deleteOptions)
         throws IOException, LDAPException
  {
    BufferedReader in = new BufferedReader(reader);
    String line = null;

    while ((line = in.readLine()) != null)
    {
      executeDelete(connection, line, deleteOptions);
    }
    in.close();
  }


  /**
   * Execute the delete request for the specified DN.
   *
   * @param connection        The connection to use to execute the request.
   * @param line           The DN to delete.
   * @param deleteOptions  The list of constraints for this request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  private void executeDelete(LDAPConnection connection, String line,
                             LDAPDeleteOptions deleteOptions)
          throws IOException, LDAPException
  {
    ArrayList<LDAPControl> controls = deleteOptions.getControls();
    ProtocolOp protocolOp = null;
    ASN1OctetString asn1OctetStr = new ASN1OctetString(line);

    protocolOp = new DeleteRequestProtocolOp(asn1OctetStr);

    out.println(INFO_PROCESSING_OPERATION.get(
            "DELETE", String.valueOf(asn1OctetStr)));
    if(!deleteOptions.showOperations())
    {
      LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                            protocolOp, controls);
      LDAPMessage responseMessage = null;
      try
      {
        connection.getLDAPWriter().writeMessage(message);
        responseMessage = connection.getLDAPReader().readMessage();
      } catch(ASN1Exception ae)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ae);
        }
        if (!deleteOptions.continueOnError())
        {
          throw new IOException(ae.getMessage());
        }
        else
        {

          Message msg = INFO_OPERATION_FAILED.get("DELETE");
          err.println(wrapText(msg, MAX_LINE_WIDTH));
          err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
          return;
        }
      }

      DeleteResponseProtocolOp op =
           responseMessage.getDeleteResponseProtocolOp();
      int resultCode = op.getResultCode();
      Message errorMessage = op.getErrorMessage();
      if(resultCode != SUCCESS && resultCode != REFERRAL &&
         !deleteOptions.continueOnError())
      {
        Message msg = INFO_OPERATION_FAILED.get("DELETE");
        throw new LDAPException(resultCode, errorMessage, msg,
                                op.getMatchedDN(), null);
      } else
      {
        if(resultCode != SUCCESS && resultCode != REFERRAL)
        {

          Message msg = INFO_OPERATION_FAILED.get("DELETE");
          LDAPToolUtils.printErrorMessage(err, msg, resultCode, errorMessage,
                                          op.getMatchedDN());
        } else
        {

          Message msg = INFO_OPERATION_SUCCESSFUL.get("DELETE", line);
          out.println(msg);
        }
      }
    }
  }

  /**
   * The main method for LDAPDelete tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainDelete(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapdelete tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainDelete(String[] args)
  {
    return mainDelete(args, true, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapdelete tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   *
   * @return The error code.
   */

  public static int mainDelete(String[] args, boolean initializeServer,
                               OutputStream outStream, OutputStream errStream)
  {
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }


    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    LDAPDeleteOptions deleteOptions = new LDAPDeleteOptions();
    LDAPConnection connection = null;

    BooleanArgument   continueOnError        = null;
    BooleanArgument   deleteSubtree          = null;
    BooleanArgument   noop                   = null;
    BooleanArgument   saslExternal           = null;
    BooleanArgument   showUsage              = null;
    BooleanArgument   startTLS               = null;
    BooleanArgument   trustAll               = null;
    BooleanArgument   useSSL                 = null;
    BooleanArgument   verbose                = null;
    FileBasedArgument bindPasswordFile       = null;
    FileBasedArgument keyStorePasswordFile   = null;
    FileBasedArgument trustStorePasswordFile = null;
    IntegerArgument   port                   = null;
    IntegerArgument   version                = null;
    StringArgument    bindDN                 = null;
    StringArgument    bindPassword           = null;
    StringArgument    certNickname           = null;
    StringArgument    controlStr             = null;
    StringArgument    encodingStr            = null;
    StringArgument    filename               = null;
    StringArgument    hostName               = null;
    StringArgument    keyStorePath           = null;
    StringArgument    keyStorePassword       = null;
    StringArgument    saslOptions            = null;
    StringArgument    trustStorePath         = null;
    StringArgument    trustStorePassword     = null;

    Reader rdr = null;
    ArrayList<String> dnStrings = new ArrayList<String> ();

    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_LDAPDELETE_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false, true, 0, 1, "\"DN\"");
    try
    {
      hostName = new StringArgument("host", OPTION_SHORT_HOST,
                                    OPTION_LONG_HOST, false, false, true,
                                    OPTION_VALUE_HOST, "localhost", null,
                                    INFO_DESCRIPTION_HOST.get());
      argParser.addArgument(hostName);

      port = new IntegerArgument("port", OPTION_SHORT_PORT,
                                 OPTION_LONG_PORT, false, false, true,
                                 OPTION_VALUE_PORT, 389, null,
                                 INFO_DESCRIPTION_PORT.get());
      argParser.addArgument(port);

      useSSL = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   INFO_DESCRIPTION_USE_SSL.get());
      argParser.addArgument(useSSL);

      startTLS = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
                                     OPTION_LONG_START_TLS,
                                     INFO_DESCRIPTION_START_TLS.get());
      argParser.addArgument(startTLS);

      bindDN = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  OPTION_VALUE_BINDDN, null, null,
                                  INFO_DESCRIPTION_BINDDN.get());
      argParser.addArgument(bindDN);

      bindPassword = new StringArgument("bindPassword", OPTION_SHORT_BINDPWD,
                                        OPTION_LONG_BINDPWD,
                                        false, false, true,
                                        OPTION_VALUE_BINDPWD,
                                        null, null,
                                        INFO_DESCRIPTION_BINDPASSWORD.get());
      argParser.addArgument(bindPassword);

      bindPasswordFile =
           new FileBasedArgument("bindPasswordFile", OPTION_SHORT_BINDPWD_FILE,
                                 OPTION_LONG_BINDPWD_FILE,
                                 false, false,
                                 OPTION_VALUE_BINDPWD_FILE, null,
                                 null, INFO_DESCRIPTION_BINDPASSWORDFILE.get());
      argParser.addArgument(bindPasswordFile);

      filename = new StringArgument("filename", OPTION_SHORT_FILENAME,
                                    OPTION_LONG_FILENAME, false, false,
                                    true, OPTION_VALUE_FILENAME, null, null,
                                    INFO_DELETE_DESCRIPTION_FILENAME.get());
      argParser.addArgument(filename);

      saslExternal =
              new BooleanArgument("useSASLExternal", 'r',
                                  "useSASLExternal",
                                  INFO_DESCRIPTION_USE_SASL_EXTERNAL.get());
      argParser.addArgument(saslExternal);

      saslOptions = new StringArgument(
              "saslOption", OPTION_SHORT_SASLOPTION,
              OPTION_LONG_SASLOPTION,
              false, true, true,
              OPTION_VALUE_SASLOPTION, null,
              null, INFO_DESCRIPTION_SASL_PROPERTIES.get());
      argParser.addArgument(saslOptions);

      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
                                     INFO_DESCRIPTION_TRUSTALL.get());
      argParser.addArgument(trustAll);

      keyStorePath = new StringArgument("keyStorePath",
                                        OPTION_SHORT_KEYSTOREPATH,
                                        OPTION_LONG_KEYSTOREPATH,
                                        false, false, true,
                                        OPTION_VALUE_KEYSTOREPATH,
                                        null, null,
                                        INFO_DESCRIPTION_KEYSTOREPATH.get());
      argParser.addArgument(keyStorePath);

      keyStorePassword =
              new StringArgument("keyStorePassword",
                                 OPTION_SHORT_KEYSTORE_PWD,
                                 OPTION_LONG_KEYSTORE_PWD,
                                 false, false,
                                 true, OPTION_VALUE_KEYSTORE_PWD,
                                 null, null,
                                 INFO_DESCRIPTION_KEYSTOREPASSWORD.get());
      argParser.addArgument(keyStorePassword);

      keyStorePasswordFile =
           new FileBasedArgument("keyStorePasswordFile",
                                 OPTION_SHORT_KEYSTORE_PWD_FILE,
                                 OPTION_LONG_KEYSTORE_PWD_FILE,
                                 false, false,
                                 OPTION_VALUE_KEYSTORE_PWD_FILE,
                                 null, null,
                                 INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
      argParser.addArgument(keyStorePasswordFile);

      certNickname = new StringArgument(
              "certnickname", 'N', "certNickname",
              false, false, true, "{nickname}", null,
              null, INFO_DESCRIPTION_CERT_NICKNAME.get());
      argParser.addArgument(certNickname);

      trustStorePath = new StringArgument(
              "trustStorePath",
              OPTION_SHORT_TRUSTSTOREPATH,
              OPTION_LONG_TRUSTSTOREPATH,
              false, false, true,
              OPTION_VALUE_TRUSTSTOREPATH,
              null, null,
              INFO_DESCRIPTION_TRUSTSTOREPATH.get());
      argParser.addArgument(trustStorePath);

      trustStorePassword =
           new StringArgument("trustStorePassword", null,
                              OPTION_LONG_TRUSTSTORE_PWD,
                              false, false, true,
                              OPTION_VALUE_TRUSTSTORE_PWD, null,
                              null, INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
      argParser.addArgument(trustStorePassword);

      trustStorePasswordFile =
           new FileBasedArgument(
                   "trustStorePasswordFile",
                   OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                   OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                   OPTION_VALUE_TRUSTSTORE_PWD_FILE, null, null,
                   INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
      argParser.addArgument(trustStorePasswordFile);

      deleteSubtree =
           new BooleanArgument("deleteSubtree", 'x', "deleteSubtree",
                               INFO_DELETE_DESCRIPTION_DELETE_SUBTREE.get());
      argParser.addArgument(deleteSubtree);

      controlStr =
           new StringArgument("control", 'J', "control", false, true, true,
                    "{controloid[:criticality[:value|::b64value|:<fileurl]]}",
                    null, null, INFO_DESCRIPTION_CONTROLS.get());
      argParser.addArgument(controlStr);

      version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                                    OPTION_LONG_PROTOCOL_VERSION, false, false,
                                    true, OPTION_VALUE_PROTOCOL_VERSION, 3,
                                    null, INFO_DESCRIPTION_VERSION.get());
      argParser.addArgument(version);

      encodingStr = new StringArgument("encoding", 'i',
                                       OPTION_LONG_ENCODING, false,
                                       false, true, "{encoding}", null,  null,
                                       INFO_DESCRIPTION_ENCODING.get());
      argParser.addArgument(encodingStr);

      continueOnError =
           new BooleanArgument("continueOnError", 'c', "continueOnError",
                               INFO_DESCRIPTION_CONTINUE_ON_ERROR.get());
      argParser.addArgument(continueOnError);

      noop = new BooleanArgument("no-op", OPTION_SHORT_DRYRUN,
          OPTION_LONG_DRYRUN, INFO_DESCRIPTION_NOOP.get());
      argParser.addArgument(noop);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    INFO_DESCRIPTION_VERBOSE.get());
      argParser.addArgument(verbose);

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    } catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }

    // If we should just display usage or version information,
    // then it has already been done so just exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              bindPassword.getLongIdentifier(),
              bindPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {

        err.println(wrapText(ERR_DESCRIPTION_INVALID_VERSION.get(
                String.valueOf(versionNumber)), MAX_LINE_WIDTH));
        return 1;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    String bindDNValue = bindDN.getValue();
    String fileNameValue = filename.getValue();
    String bindPasswordValue = bindPassword.getValue();
    if(bindPasswordValue != null && bindPasswordValue.equals("-"))
    {
      // read the password from the stdin.
      try
      {
        out.print(INFO_LDAPAUTH_PASSWORD_PROMPT.get(bindDNValue));
        char[] pwChars = PasswordReader.readPassword();
        bindPasswordValue = new String(pwChars);
      } catch(Exception ex)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        err.println(wrapText(ex.getMessage(), MAX_LINE_WIDTH));
        return 1;
      }
    } else if(bindPasswordValue == null)
    {
      // Read from file if it exists.
      bindPasswordValue = bindPasswordFile.getValue();
    }

    String keyStorePathValue = keyStorePath.getValue();
    String trustStorePathValue = trustStorePath.getValue();

    String keyStorePasswordValue = null;
    if (keyStorePassword.isPresent())
    {
      keyStorePasswordValue = keyStorePassword.getValue();
    }
    else if (keyStorePasswordFile.isPresent())
    {
      keyStorePasswordValue = keyStorePasswordFile.getValue();
    }

    String trustStorePasswordValue = null;
    if (trustStorePassword.isPresent())
    {
      trustStorePasswordValue = trustStorePassword.getValue();
    }
    else if (trustStorePasswordFile.isPresent())
    {
      trustStorePasswordValue = trustStorePasswordFile.getValue();
    }

    deleteOptions.setShowOperations(noop.isPresent());
    deleteOptions.setVerbose(verbose.isPresent());
    deleteOptions.setContinueOnError(continueOnError.isPresent());
    deleteOptions.setEncoding(encodingStr.getValue());
    deleteOptions.setDeleteSubtree(deleteSubtree.isPresent());

    if(controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        LDAPControl ctrl = LDAPToolUtils.getControl(ctrlString, err);
        if(ctrl == null)
        {
          Message message = ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          err.println(argParser.getUsage());
          return 1;
        }
        deleteOptions.getControls().add(ctrl);
      }
    }

    if(deleteOptions.getDeleteSubtree())
    {
      LDAPControl control = new LDAPControl(OID_SUBTREE_DELETE_CONTROL);
      deleteOptions.getControls().add(control);
    }

    ArrayList<String> trailingArgs = argParser.getTrailingArguments();
    for(String s : trailingArgs)
    {
      dnStrings.add(s);
    }

    // Set the connection options.
    // Parse the SASL properties.
    connectionOptions.setSASLExternal(saslExternal.isPresent());
    if(saslOptions.isPresent())
    {
      LinkedList<String> values = saslOptions.getValues();
      for(String saslOption : values)
      {
        if(saslOption.startsWith("mech="))
        {
          boolean val = connectionOptions.setSASLMechanism(saslOption);
          if(val == false)
          {
            return 1;
          }
        } else
        {
          boolean val = connectionOptions.addSASLProperty(saslOption);
          if(val == false)
          {
            return 1;
          }
        }
      }
    }
    connectionOptions.setUseSSL(useSSL.isPresent());
    connectionOptions.setStartTLS(startTLS.isPresent());

    if(connectionOptions.useSASLExternal())
    {
      if(!connectionOptions.useSSL() && !connectionOptions.useStartTLS())
      {
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      if(keyStorePathValue == null)
      {
        Message message = ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    LDAPDelete ldapDelete = null;
    try
    {
      if (initializeServer)
      {
        // Bootstrap and initialize directory data structures.
        EmbeddedUtils.initializeForClientUse();
      }

      // Connect to the specified host with the supplied userDN and password.
      SSLConnectionFactory sslConnectionFactory = null;
      if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
      {
        String clientAlias;
        if (certNickname.isPresent())
        {
          clientAlias = certNickname.getValue();
        }
        else
        {
          clientAlias = null;
        }

        sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStorePathValue,
                                  keyStorePasswordValue, clientAlias,
                                  trustStorePathValue, trustStorePasswordValue);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID);

      ldapDelete = new LDAPDelete(nextMessageID, out, err);
      if(fileNameValue == null && dnStrings.isEmpty())
      {
        // Read from stdin.
        rdr = new InputStreamReader(System.in);
      } else if(fileNameValue != null)
      {
        rdr = new FileReader(fileNameValue);
      }

      if(rdr != null)
      {
        ldapDelete.readAndExecute(connection, rdr, deleteOptions);
      } else
      {
        ldapDelete.readAndExecute(connection, dnStrings, deleteOptions);
      }
    } catch(LDAPException le)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, le);
      }
      LDAPToolUtils.printErrorMessage(err, le.getMessageObject(),
                                      le.getResultCode(),
                                      le.getErrorMessage(),
                                      le.getMatchedDN());
      int code = le.getResultCode();
      return code;
    } catch(LDAPConnectionException lce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, lce);
      }
      LDAPToolUtils.printErrorMessage(err, lce.getMessageObject(),
                                      lce.getResultCode(),
                                      lce.getErrorMessage(),
                                      lce.getMatchedDN());
      int code = lce.getResultCode();
      return code;
    } catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    } finally
    {
      if(connection != null)
      {
        if (ldapDelete == null)
        {
          connection.close(null);
        }
        else
        {
          connection.close(ldapDelete.nextMessageID);
        }
      }
    }
    return 0;
  }

}

