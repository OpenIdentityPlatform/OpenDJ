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
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.Base64;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class provides a tool that can be used to issue compare requests to the
 * Directory Server.
 */
public class LDAPCompare
{
  /**
   * The fully-qualified name of this class for debugging purposes.
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
        int numBytes =
              connection.getASN1Writer().writeElement(message.encode());
        ASN1Element element = connection.getASN1Reader().readElement();
        responseMessage =
             LDAPMessage.decode(ASN1Sequence.decodeAsSequence(element));
      } catch(ASN1Exception ae)
      {
        assert debugException(CLASS_NAME, "executeCompare", ae);
        if(!compareOptions.continueOnError())
        {
          throw new IOException(ae.getMessage());
        } else
        {
          msgID = MSGID_OPERATION_FAILED;
          String msg = getMessage(msgID, "COMPARE", line, ae.getMessage());
          err.println(msg);
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
        String msg = getMessage(msgID, "COMPARE", line, errorMessage);
        throw new LDAPException(resultCode, msgID, msg);
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
          String msg = getMessage(msgID, "COMPARE", line, errorMessage);
          err.println(msg);
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

    BooleanArgument noop = null;
    BooleanArgument trustAll = null;
    StringArgument assertionFilter = null;
    StringArgument bindDN = null;
    StringArgument bindPassword = null;
    FileBasedArgument bindPasswordFile = null;
    StringArgument encodingStr = null;
    StringArgument keyStorePath = null;
    StringArgument keyStorePassword = null;
    StringArgument trustStorePath = null;
    StringArgument trustStorePassword = null;
    StringArgument hostName = null;
    IntegerArgument port = null;
    IntegerArgument version = null;
    BooleanArgument showUsage = null;
    StringArgument controlStr = null;
    BooleanArgument verbose = null;
    BooleanArgument continueOnError = null;
    BooleanArgument useSSL = null;
    BooleanArgument startTLS = null;
    BooleanArgument saslExternal = null;
    StringArgument filename = null;
    StringArgument saslOptions = null;

    ArrayList<String> dnStrings = new ArrayList<String> ();
    String attributeType = null;
    byte[] attributeVal = null;
    Reader rdr = null;

    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, false, true,
                                1, 0, " \'attribute:value\' \"DN\" ...");

    try
    {
      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
                                    MSGID_DESCRIPTION_TRUSTALL);
      argParser.addArgument(trustAll);
      bindDN = new StringArgument("bindDN", 'D', "bindDN", false, false,
                                  true, "{bindDN}", null, null,
                                  MSGID_DESCRIPTION_BINDDN);
      argParser.addArgument(bindDN);
      bindPassword = new StringArgument("bindPassword", 'w', "bindPassword",
                                  false, false,
                                  true, "{bindPassword}", null, null,
                                  MSGID_DESCRIPTION_BINDPASSWORD);
      argParser.addArgument(bindPassword);
      bindPasswordFile = new FileBasedArgument("bindPasswordFile", 'j',
                                  "bindPasswordFile", false, false,
                                  "{bindPasswordFilename}", null, null,
                                  MSGID_DESCRIPTION_BINDPASSWORDFILE);
      argParser.addArgument(bindPasswordFile);
      encodingStr = new StringArgument("encoding", 'i', "encoding",
                                      false, false,
                                      true, "{encoding}", null, null,
                                      MSGID_DESCRIPTION_ENCODING);
      argParser.addArgument(encodingStr);
      keyStorePath = new StringArgument("keyStorePath", 'K',
                                  "keyStorePath", false, false, true,
                                  "{keyStorePath}", null, null,
                                  MSGID_DESCRIPTION_KEYSTOREPATH);
      argParser.addArgument(keyStorePath);
      trustStorePath = new StringArgument("trustStorePath", 'P',
                                  "trustStorePath", false, false, true,
                                  "{trustStorePath}", null, null,
                                  MSGID_DESCRIPTION_TRUSTSTOREPATH);
      argParser.addArgument(trustStorePath);
      keyStorePassword = new StringArgument("keyStorePassword", 'W',
                                  "keyStorePassword", false, false,
                                  true, "{keyStorePassword}", null, null,
                                  MSGID_DESCRIPTION_KEYSTOREPASSWORD);
      argParser.addArgument(keyStorePassword);
      hostName = new StringArgument("host", 'h', "host",
                                      false, false,
                                      true, "{host}", "localhost", null,
                                      MSGID_DESCRIPTION_HOST);
      argParser.addArgument(hostName);
      port = new IntegerArgument("port", 'p', "port",
                              false, false, true, "{port}", 389, null,
                              MSGID_DESCRIPTION_PORT);
      argParser.addArgument(port);
      version = new IntegerArgument("version", 'V', "version",
                              false, false, true, "{version}", 3, null,
                              MSGID_DESCRIPTION_VERSION);
      argParser.addArgument(version);
      filename = new StringArgument("filename", 'f',
                                  "filename", false, false, true,
                                  "{filename}", null, null,
                                  MSGID_COMPARE_DESCRIPTION_FILENAME);
      argParser.addArgument(filename);
      showUsage = new BooleanArgument("showUsage", 'H', "help",
                                    MSGID_DESCRIPTION_SHOWUSAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
      controlStr = new StringArgument("controls", 'J', "controls", false,
                false, true,
                "{controloid[:criticality[:value|::b64value|:<fileurl]]}",
                null, null, MSGID_DESCRIPTION_CONTROLS);
      argParser.addArgument(controlStr);
      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    MSGID_DESCRIPTION_VERBOSE);
      argParser.addArgument(verbose);
      continueOnError = new BooleanArgument("continueOnError", 'c',
                                    "continueOnError",
                                    MSGID_DESCRIPTION_CONTINUE_ON_ERROR);
      argParser.addArgument(continueOnError);
      useSSL = new BooleanArgument("useSSL", 'Z',
                                    "useSSL",
                                    MSGID_DESCRIPTION_USE_SSL);
      argParser.addArgument(useSSL);
      startTLS = new BooleanArgument("startTLS", 'q',
                                    "startTLS",
                                    MSGID_DESCRIPTION_START_TLS);
      argParser.addArgument(startTLS);
      saslExternal = new BooleanArgument("useSASLExternal", 'r',
                                    "useSASLExternal",
                                    MSGID_DESCRIPTION_USE_SASL_EXTERNAL);
      argParser.addArgument(saslExternal);
      saslOptions = new StringArgument("saslOptions", 'o', "saslOptions",
                             false, true, true, "{name=value}", null, null,
                             MSGID_DESCRIPTION_SASL_PROPERTIES);
      argParser.addArgument(saslOptions);
      noop = new BooleanArgument("no-op", 'n',
                                    "noop",
                                    MSGID_DESCRIPTION_NOOP);
      argParser.addArgument(noop);
      assertionFilter = new StringArgument("assertionfilter", null,
                                 "assertionFilter", false, false, true,
                                 "{filter}", null, null,
                                 MSGID_DESCRIPTION_COMPARE_ASSERTION_FILTER);
      argParser.addArgument(assertionFilter);
    } catch (ArgumentException ae)
    {
      int    msgID   = MSGID_ENCPW_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(message);
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_ENCPW_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(message);
      err.println(argParser.getUsage());
      return 1;
    }

    // If we should just display usage information, then print it and exit.
    if (showUsage.isPresent())
    {
      return 1;
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      err.println("ERROR: Both -w and -j flags specified. " +
                  "Please specify one.");
      return 1;
    }

    ArrayList<String> attrAndDNStrings = argParser.getTrailingArguments();

    if(attrAndDNStrings.isEmpty())
    {
      err.println("No Attributes specified for comparison");
      return 1;
    }

    // First element should be an attribute string.
    String attributeString = attrAndDNStrings.remove(0);

    // Rest are DN strings
    for(String s : attrAndDNStrings)
    {
      dnStrings.add(s);
    }

    // parse the attribute string
    int idx = attributeString.indexOf(":");
    if(idx == -1)
    {
      err.println("Invalid attribute string:" + attributeString);
      err.println("Attribute string must be in one of the " +
      "following forms: attribute:value, attribute::base64value, " +
      "attribute:<fileURL" );
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
          assert debugException(CLASS_NAME, "main", e);
          attributeVal = remainder.getBytes();
        }
      } else if(nextChar == '<')
      {
        String fileURL = remainder.substring(1, remainder.length());
        attributeVal = LDAPToolUtils.readBytesFromFile(fileURL);
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
      assert debugException(CLASS_NAME, "main", ae);
      err.println(ae.getMessage());
      return 1;
    }

    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {
        int msgID = MSGID_DESCRIPTION_INVALID_VERSION;
        err.println(getMessage(msgID, versionNumber));
        return 1;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      assert debugException(CLASS_NAME, "main", ae);
      err.println(ae.getMessage());
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
        assert debugException(CLASS_NAME, "main", ex);
        err.println(ex.getMessage());
        return 1;
      }
    } else if(bindPasswordValue == null)
    {
      // Read from file if it exists.
      bindPasswordValue = bindPasswordFile.getValue();
    }

    String keyStorePathValue = keyStorePath.getValue();
    String keyStorePasswordValue = keyStorePassword.getValue();
    String trustStorePathValue = trustStorePath.getValue();
    String trustStorePasswordValue = null;

    compareOptions.setShowOperations(noop.isPresent());
    compareOptions.setVerbose(verbose.isPresent());
    compareOptions.setContinueOnError(continueOnError.isPresent());
    compareOptions.setEncoding(encodingStr.getValue());
    if(controlStr.hasValue())
    {
      String ctrlString = controlStr.getValue();
      LDAPControl ctrl = LDAPToolUtils.getControl(ctrlString);
      if(ctrl == null)
      {
        err.println("Invalid control specified:" + ctrlString);
        err.println(argParser.getUsage());
        return 1;
      }
      compareOptions.getControls().add(ctrl);
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
        err.println(getMessage(MSGID_LDAP_ASSERTION_INVALID_FILTER,
                               le.getMessage()));
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
        err.println("SASL External requires either SSL or StartTLS " +
                    "options to be requested.");
        return 1;
      }
      if(keyStorePathValue == null)
      {
        err.println("SASL External requires a path to the SSL " +
                    "client certificate keystore.");
        return 1;
      }
    }

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
        sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStorePathValue,
                                  keyStorePasswordValue,
                                  trustStorePathValue, trustStorePasswordValue);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID);


      LDAPCompare ldapCompare = new LDAPCompare(nextMessageID, out, err);
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
      assert debugException(CLASS_NAME, "main", le);
      err.println(le.getMessage());
      int code = le.getResultCode();
      return code;
    } catch(LDAPConnectionException lce)
    {
        assert debugException(CLASS_NAME, "main", lce);
        err.println(lce.getMessage());
        int code = lce.getErrorCode();
        return code;
    } catch(Exception e)
    {
      assert debugException(CLASS_NAME, "main", e);
      err.println(e.getMessage());
      return 1;
    } finally
    {
      if(connection != null)
      {
        connection.close();
      }
    }
    return 0;
  }

}

