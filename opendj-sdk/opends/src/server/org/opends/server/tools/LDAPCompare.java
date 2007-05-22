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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.CompareRequestProtocolOp;
import org.opends.server.protocols.ldap.CompareResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LDAPException;
import org.opends.server.util.Base64;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;


/**
 * This class provides a tool that can be used to issue compare requests to the
 * Directory Server.
 */
public class LDAPCompare
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
      "org.opends.server.tools.LDAPCompare";


  // The message ID counter to use for requests.
  private AtomicInteger nextMessageID;

  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;



  /**
   * Constructor for the LDAPCompare object.
   *
   * @param  nextMessageID  The message ID counter to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPCompare(AtomicInteger nextMessageID, PrintStream out,
                     PrintStream err)
  {
    this.nextMessageID = nextMessageID;
    this.out           = out;
    this.err           = err;
  }

  /**
   * Execute the compare request in the specified list of DNs.
   *
   * @param connection      The connection to execute the request on.
   * @param attributeType   The attribute type to compare.
   * @param attributeVal    The attribute value to compare.
   * @param lines           The list of DNs to compare the attribute in.
   * @param compareOptions  The constraints for the compare request.
   *
   * @throws  IOException  If a problem occurs while communicating with the
   *                       Directory Server.
   *
   * @throws  LDAPException  If the server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection, String attributeType,
                             byte[] attributeVal, ArrayList<String> lines,
                             LDAPCompareOptions compareOptions)
         throws IOException, LDAPException
  {
    for(String line : lines)
    {
      executeCompare(connection, attributeType, attributeVal, line,
                     compareOptions);
    }
  }


  /**
   * Read the specified DNs from the given reader
   * (file or stdin) and execute the given compare request.
   *
   * @param connection      The connection to execute the request on.
   * @param attributeType   The attribute type to compare.
   * @param attributeVal    The attribute value to compare.
   * @param reader          The reader to read the list of DNs from.
   * @param compareOptions  The constraints for the compare request.
   *
   * @throws  IOException  If a problem occurs while communicating with the
   *                       Directory Server.
   *
   * @throws  LDAPException  If the server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection, String attributeType,
                             byte[] attributeVal, Reader reader,
                             LDAPCompareOptions compareOptions)
         throws IOException, LDAPException
  {
    BufferedReader in = new BufferedReader(reader);
    String line = null;

    while ((line = in.readLine()) != null)
    {
      executeCompare(connection, attributeType, attributeVal, line,
                     compareOptions);
    }
    in.close();
  }


  /**
   * Execute the compare request for the specified DN entry.
   *
   * @param connection      The connection to execute the request on.
   * @param attributeType   The attribute type to compare.
   * @param attributeVal    The attribute value to compare.
   * @param line            The DN to compare attribute in.
   * @param compareOptions  The constraints for the compare request.
   *
   * @throws  IOException  If a problem occurs while communicating with the
   *                       Directory Server.
   *
   * @throws  LDAPException  If the server returns an error response.
   */
  private void executeCompare(LDAPConnection connection, String attributeType,
                              byte[] attributeVal, String line,
                              LDAPCompareOptions compareOptions)
          throws IOException, LDAPException
  {
    ArrayList<LDAPControl> controls = compareOptions.getControls();
    ASN1OctetString dnOctetStr = new ASN1OctetString(line);
    ASN1OctetString attrValOctetStr = new ASN1OctetString(attributeVal);

    ProtocolOp protocolOp = new CompareRequestProtocolOp(dnOctetStr,
                                     attributeType, attrValOctetStr);

    int msgID = MSGID_PROCESSING_COMPARE_OPERATION;
    out.println(getMessage(msgID, attributeType, attrValOctetStr, dnOctetStr));

    if(!compareOptions.showOperations())
    {
      LDAPMessage responseMessage = null;
      try
      {
        LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                              protocolOp, controls);
        connection.getASN1Writer().writeElement(message.encode());
        ASN1Element element = connection.getASN1Reader().readElement();
        responseMessage =
             LDAPMessage.decode(ASN1Sequence.decodeAsSequence(element));
      } catch(ASN1Exception ae)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ae);
        }
        if (!compareOptions.continueOnError())
        {
          throw new IOException(ae.getMessage());
        }
        else
        {
          msgID = MSGID_OPERATION_FAILED;
          String msg = getMessage(msgID, "COMPARE");
          err.println(wrapText(msg, MAX_LINE_WIDTH));
          err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
          return;
        }
      }

      CompareResponseProtocolOp op =
        responseMessage.getCompareResponseProtocolOp();
      int resultCode = op.getResultCode();
      String errorMessage = op.getErrorMessage();

      if(resultCode != COMPARE_TRUE && resultCode != COMPARE_FALSE
         && !compareOptions.continueOnError())
      {
        msgID = MSGID_OPERATION_FAILED;
        String msg = getMessage(msgID, "COMPARE");
        throw new LDAPException(resultCode, errorMessage, msgID, msg,
                                op.getMatchedDN(), null);
      } else
      {
        if(resultCode == COMPARE_FALSE)
        {
          msgID = MSGID_COMPARE_OPERATION_RESULT_FALSE;
          out.println(getMessage(msgID, line));
        } else if(resultCode == COMPARE_TRUE)
        {
          msgID = MSGID_COMPARE_OPERATION_RESULT_TRUE;
          out.println(getMessage(msgID, line));
        } else
        {
          msgID = MSGID_OPERATION_FAILED;
          String msg = getMessage(msgID, "COMPARE");
          LDAPToolUtils.printErrorMessage(err, msg, resultCode, errorMessage,
                                          op.getMatchedDN());
        }
      }
    }
  }

  /**
   * The main method for LDAPCompare tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainCompare(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapcompare tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainCompare(String[] args)
  {
    return mainCompare(args, true, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapcompare tool.
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

  public static int mainCompare(String[] args, boolean initializeServer,
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
    LDAPCompareOptions compareOptions = new LDAPCompareOptions();
    LDAPConnection connection = null;

    BooleanArgument   continueOnError        = null;
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
    StringArgument    assertionFilter        = null;
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

    ArrayList<String> dnStrings = new ArrayList<String> ();
    String attributeType = null;
    byte[] attributeVal = null;
    Reader rdr = null;

    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_LDAPCOMPARE_TOOL_DESCRIPTION);
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                        false, true, 1, 0,
                                        " \'attribute:value\' \"DN\" ...");

    try
    {
      hostName = new StringArgument("host", OPTION_SHORT_HOST,
                                    OPTION_LONG_HOST, false, false, true,
                                    OPTION_VALUE_HOST, "localhost", null,
                                    MSGID_DESCRIPTION_HOST);
      argParser.addArgument(hostName);

      port = new IntegerArgument("port", OPTION_SHORT_PORT,
                                 OPTION_LONG_PORT, false, false, true,
                                 OPTION_VALUE_PORT, 389, null,
                                 MSGID_DESCRIPTION_PORT);
      argParser.addArgument(port);

      useSSL = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   MSGID_DESCRIPTION_USE_SSL);
      argParser.addArgument(useSSL);

      startTLS = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
                                     OPTION_LONG_START_TLS,
                                     MSGID_DESCRIPTION_START_TLS);
      argParser.addArgument(startTLS);

      bindDN = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  OPTION_VALUE_BINDDN, null, null,
                                  MSGID_DESCRIPTION_BINDDN);
      argParser.addArgument(bindDN);

      bindPassword = new StringArgument("bindPassword", OPTION_SHORT_BINDPWD,
                                        OPTION_LONG_BINDPWD,
                                        false, false, true,
                                        OPTION_VALUE_BINDPWD,
                                        null, null,
                                        MSGID_DESCRIPTION_BINDPASSWORD);
      argParser.addArgument(bindPassword);

      bindPasswordFile =
           new FileBasedArgument("bindPasswordFile",
                                 OPTION_SHORT_BINDPWD_FILE,
                                 OPTION_LONG_BINDPWD_FILE,
                                 false, false,
                                 OPTION_VALUE_BINDPWD_FILE, null,
                                 null, MSGID_DESCRIPTION_BINDPASSWORDFILE);
      argParser.addArgument(bindPasswordFile);

      filename = new StringArgument("filename", OPTION_SHORT_FILENAME,
                                    OPTION_LONG_FILENAME, false, false,
                                    true, OPTION_VALUE_FILENAME, null, null,
                                    MSGID_COMPARE_DESCRIPTION_FILENAME);
      argParser.addArgument(filename);

      saslExternal = new BooleanArgument("useSASLExternal", 'r',
                                         "useSASLExternal",
                                         MSGID_DESCRIPTION_USE_SASL_EXTERNAL);
      argParser.addArgument(saslExternal);

      saslOptions = new StringArgument("saslOption", OPTION_SHORT_SASLOPTION,
                                       OPTION_LONG_SASLOPTION, false,
                                       true, true,
                                       OPTION_VALUE_SASLOPTION, null, null,
                                       MSGID_DESCRIPTION_SASL_PROPERTIES);
      argParser.addArgument(saslOptions);

      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
                                     MSGID_DESCRIPTION_TRUSTALL);
      argParser.addArgument(trustAll);

      keyStorePath = new StringArgument("keyStorePath",
                                        OPTION_SHORT_KEYSTOREPATH,
                                        OPTION_LONG_KEYSTOREPATH,
                                        false, false, true,
                                        OPTION_VALUE_KEYSTOREPATH,
                                        null, null,
                                        MSGID_DESCRIPTION_KEYSTOREPATH);
      argParser.addArgument(keyStorePath);

      keyStorePassword = new StringArgument("keyStorePassword",
                                  OPTION_SHORT_KEYSTORE_PWD,
                                  OPTION_LONG_KEYSTORE_PWD, false, false,
                                  true, "{keyStorePassword}", null, null,
                                  MSGID_DESCRIPTION_KEYSTOREPASSWORD);
      argParser.addArgument(keyStorePassword);

      keyStorePasswordFile =
           new FileBasedArgument("keyStorePasswordFile",
                                 OPTION_SHORT_KEYSTORE_PWD_FILE,
                                 OPTION_LONG_KEYSTORE_PWD_FILE,
                                 false, false,
                                 OPTION_VALUE_KEYSTORE_PWD_FILE,
                                 null, null,
                                 MSGID_DESCRIPTION_KEYSTOREPASSWORD_FILE);
      argParser.addArgument(keyStorePasswordFile);

      certNickname = new StringArgument("certnickname", 'N', "certNickname",
                                        false, false, true, "{nickname}", null,
                                        null, MSGID_DESCRIPTION_CERT_NICKNAME);
      argParser.addArgument(certNickname);

      trustStorePath = new StringArgument("trustStorePath",
                                          OPTION_SHORT_TRUSTSTOREPATH,
                                          OPTION_LONG_TRUSTSTOREPATH,
                                          false, false, true,
                                          OPTION_VALUE_TRUSTSTOREPATH,
                                          null, null,
                                          MSGID_DESCRIPTION_TRUSTSTOREPATH);
      argParser.addArgument(trustStorePath);

      trustStorePassword =
           new StringArgument("trustStorePassword", null,
                              OPTION_LONG_TRUSTSTORE_PWD,
                              false, false, true,
                              OPTION_VALUE_TRUSTSTORE_PWD, null,
                              null, MSGID_DESCRIPTION_TRUSTSTOREPASSWORD);
      argParser.addArgument(trustStorePassword);

      trustStorePasswordFile =
           new FileBasedArgument("trustStorePasswordFile",
                                 OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                                 OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                                 OPTION_VALUE_TRUSTSTORE_PWD_FILE, null, null,
                                 MSGID_DESCRIPTION_TRUSTSTOREPASSWORD_FILE);
      argParser.addArgument(trustStorePasswordFile);

      assertionFilter = new StringArgument("assertionfilter", null,
                                 OPTION_LONG_ASSERTION_FILE, false, false, true,
                                 OPTION_VALUE_ASSERTION_FILE, null, null,
                                 MSGID_DESCRIPTION_ASSERTION_FILTER);
      argParser.addArgument(assertionFilter);

      controlStr =
           new StringArgument("control", 'J', "control", false, true, true,
                    "{controloid[:criticality[:value|::b64value|:<filePath]]}",
                    null, null, MSGID_DESCRIPTION_CONTROLS);
      argParser.addArgument(controlStr);

      version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                                    OPTION_LONG_PROTOCOL_VERSION,
                                    false, false, true,
                                    OPTION_VALUE_PROTOCOL_VERSION,
                                    3, null, MSGID_DESCRIPTION_VERSION);
      argParser.addArgument(version);

      encodingStr = new StringArgument("encoding", 'i', "encoding",
                                      false, false,
                                      true, "{encoding}", null, null,
                                      MSGID_DESCRIPTION_ENCODING);
      argParser.addArgument(encodingStr);

      continueOnError = new BooleanArgument("continueOnError", 'c',
                                    "continueOnError",
                                    MSGID_DESCRIPTION_CONTINUE_ON_ERROR);
      argParser.addArgument(continueOnError);

      noop = new BooleanArgument("no-op", OPTION_SHORT_DRYRUN,
                                    OPTION_LONG_DRYRUN,
                                    MSGID_DESCRIPTION_NOOP);
      argParser.addArgument(noop);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    MSGID_DESCRIPTION_VERBOSE);
      argParser.addArgument(verbose);

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
                                    OPTION_LONG_HELP,
                                    MSGID_DESCRIPTION_SHOWUSAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    } catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

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
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      int    msgID   = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, bindPassword.getLongIdentifier(),
                                  bindPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    ArrayList<String> attrAndDNStrings = argParser.getTrailingArguments();

    if(attrAndDNStrings.isEmpty())
    {
      int    msgID   = MSGID_LDAPCOMPARE_NO_ATTR;
      String message = getMessage(msgID);
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // First element should be an attribute string.
    String attributeString = attrAndDNStrings.remove(0);

    // Rest are DN strings
    for(String s : attrAndDNStrings)
    {
      dnStrings.add(s);
    }

    // If no DNs were provided, then exit with an error.
    if (dnStrings.isEmpty())
    {
      int msgID = MSGID_LDAPCOMPARE_NO_DNS;
      err.println(wrapText(getMessage(msgID), MAX_LINE_WIDTH));
      return 1;
    }

    // parse the attribute string
    int idx = attributeString.indexOf(":");
    if(idx == -1)
    {
      int    msgID   = MSGID_LDAPCOMPARE_INVALID_ATTR_STRING;
      String message = getMessage(msgID, attributeString);
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    attributeType = attributeString.substring(0, idx);
    String remainder = attributeString.substring(idx+1,
                                                 attributeString.length());
    if (remainder.length() > 0)
    {
      char nextChar = remainder.charAt(0);
      if(nextChar == ':')
      {
        String base64 = remainder.substring(1, remainder.length());
        try
        {
          attributeVal = Base64.decode(base64);
        }
        catch (ParseException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_COMPARE_CANNOT_BASE64_DECODE_ASSERTION_VALUE;
          err.println(wrapText(getMessage(msgID), MAX_LINE_WIDTH));
          return 1;
        }
      } else if(nextChar == '<')
      {
        try
        {
          String filePath = remainder.substring(1, remainder.length());
          attributeVal = LDAPToolUtils.readBytesFromFile(filePath, err);
        }
        catch (Exception e)
        {
          int msgID = MSGID_COMPARE_CANNOT_READ_ASSERTION_VALUE_FROM_FILE;
          err.println(wrapText(getMessage(msgID, String.valueOf(e)),
                               MAX_LINE_WIDTH));
          return 1;
        }
      } else
      {
        attributeVal = remainder.getBytes();
      }
    }
    else
    {
      attributeVal = remainder.getBytes();
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch (ArgumentException ae)
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
        int msgID = MSGID_DESCRIPTION_INVALID_VERSION;
        err.println(wrapText(getMessage(msgID, versionNumber), MAX_LINE_WIDTH));
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
        out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, bindDNValue));
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

    compareOptions.setShowOperations(noop.isPresent());
    compareOptions.setVerbose(verbose.isPresent());
    compareOptions.setContinueOnError(continueOnError.isPresent());
    compareOptions.setEncoding(encodingStr.getValue());

    if(controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        LDAPControl ctrl = LDAPToolUtils.getControl(ctrlString, err);
        if(ctrl == null)
        {
          int    msgID   = MSGID_TOOL_INVALID_CONTROL_STRING;
          String message = getMessage(msgID, ctrlString);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          err.println(argParser.getUsage());
          return 1;
        }
        compareOptions.getControls().add(ctrl);
      }
    }

    if (assertionFilter.isPresent())
    {
      String filterString = assertionFilter.getValue();
      LDAPFilter filter;
      try
      {
        filter = LDAPFilter.decode(filterString);

        LDAPControl assertionControl =
             new LDAPControl(OID_LDAP_ASSERTION, true,
                             new ASN1OctetString(filter.encode().encode()));
        compareOptions.getControls().add(assertionControl);
      }
      catch (LDAPException le)
      {
        int    msgID   = MSGID_LDAP_ASSERTION_INVALID_FILTER;
        String message = getMessage(msgID, le.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
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
        int    msgID   = MSGID_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS;
        String message = getMessage(msgID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      if(keyStorePathValue == null)
      {
        int    msgID   = MSGID_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE;
        String message = getMessage(msgID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    LDAPCompare ldapCompare = null;
    try
    {
      if (initializeServer)
      {
        // Bootstrap and initialize directory data structures.
        DirectoryServer.bootstrapClient();
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


      ldapCompare = new LDAPCompare(nextMessageID, out, err);
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
        ldapCompare.readAndExecute(connection, attributeType, attributeVal,
                                   rdr, compareOptions);
      } else
      {
        ldapCompare.readAndExecute(connection, attributeType, attributeVal,
                                   dnStrings, compareOptions);
      }
    } catch(LDAPException le)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, le);
      }
      LDAPToolUtils.printErrorMessage(err, le.getMessage(), le.getResultCode(),
                                      le.getErrorMessage(), le.getMatchedDN());
      int code = le.getResultCode();
      return code;
    } catch(LDAPConnectionException lce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, lce);
      }
      LDAPToolUtils.printErrorMessage(err, lce.getMessage(),
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
        if (ldapCompare == null)
        {
          connection.close(null);
        }
        else
        {
          connection.close(ldapCompare.nextMessageID);
        }
      }
    }
    return 0;
  }

}

