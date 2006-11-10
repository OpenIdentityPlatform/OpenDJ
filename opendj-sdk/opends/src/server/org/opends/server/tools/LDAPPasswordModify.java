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



import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.types.DN;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This program provides a utility that uses the LDAP password modify extended
 * operation to change the password for a user.  It exposes the three primary
 * options available for this operation, which are:
 *
 * <UL>
 *   <LI>The user identity whose password should be changed.</LI>
 *   <LI>The current password for the user.</LI>
 *   <LI>The new password for the user.
 * </UL>
 *
 * All of these are optional components that may be included or omitted from the
 * request.
 */
public class LDAPPasswordModify
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.tools.LDAPPasswordModify";




  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int returnCode = mainPasswordModify(args, true, System.out, System.err);
    if (returnCode != 0)
    {
      System.exit(returnCode);
    }
  }



  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  An integer value of zero if everything completed successfully, or
   *          a nonzero value if an error occurred.
   */
  public static int mainPasswordModify(String[] args)
  {
    return mainPasswordModify(args, true, System.out, System.err);
  }



  /**
   * Parses the command-line arguments, establishes a connection to the
   * Directory Server, sends the password modify request, and reads the
   * response.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output.
   * @param  errStream         The output stream to use for standard error.
   *
   * @return  An integer value of zero if everything completed successfully, or
   *          a nonzero value if an error occurred.
   */
  public static int mainPasswordModify(String[] args, boolean initializeServer,
                                       OutputStream outStream,
                                       OutputStream errStream)
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


    // Create the arguments that will be used by this program.
    BooleanArgument   provideDNForAuthzID;
    BooleanArgument   showUsage;
    BooleanArgument   sslBlindTrust;
    BooleanArgument   useSSL;
    BooleanArgument   useStartTLS;
    FileBasedArgument bindPWFile;
    FileBasedArgument currentPWFile;
    FileBasedArgument newPWFile;
    FileBasedArgument sslKeyStorePINFile;
    FileBasedArgument sslTrustStorePINFile;
    IntegerArgument   ldapPort;
    StringArgument    authzID;
    StringArgument    bindDN;
    StringArgument    bindPW;
    StringArgument    currentPW;
    StringArgument    ldapHost;
    StringArgument    newPW;
    StringArgument    sslKeyStore;
    StringArgument    sslTrustStore;


    // Initialize the argument parser.
    String toolDescription = getMessage(MSGID_LDAPPWMOD_TOOL_DESCRIPTION);
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);

    try
    {
      ldapHost = new StringArgument("ldaphost", 'h', "ldapHost", false, false,
                                    true, "{host}", "127.0.0.1", null,
                                    MSGID_LDAPPWMOD_DESCRIPTION_HOST);
      argParser.addArgument(ldapHost);


      ldapPort = new IntegerArgument("ldapport", 'p', "ldapPort", false, false,
                                     true, "{port}", 389, null, true, 1, true,
                                     65535, MSGID_LDAPPWMOD_DESCRIPTION_PORT);
      argParser.addArgument(ldapPort);


      useSSL = new BooleanArgument("usessl", 'Z', "useSSL",
                                   MSGID_LDAPPWMOD_DESCRIPTION_USE_SSL);
      argParser.addArgument(useSSL);


      useStartTLS = new BooleanArgument("usestarttls", 'q', "useStartTLS",
                             MSGID_LDAPPWMOD_DESCRIPTION_USE_STARTTLS);
      argParser.addArgument(useStartTLS);


      bindDN = new StringArgument("binddn", 'D', "bindDN", false, false, true,
                                  "{bindDN}", null, null,
                                  MSGID_LDAPPWMOD_DESCRIPTION_BIND_DN);
      argParser.addArgument(bindDN);


      bindPW = new StringArgument("bindpw", 'w', "bindPassword", false, false,
                                  true, "{bindDN}", null, null,
                                  MSGID_LDAPPWMOD_DESCRIPTION_BIND_PW);
      argParser.addArgument(bindPW);


      bindPWFile =
           new FileBasedArgument("bindpwfile", 'W', "bindPasswordFile", false,
                                 false, "{file}", null, null,
                                 MSGID_LDAPPWMOD_DESCRIPTION_BIND_PW_FILE);
      argParser.addArgument(bindPWFile);


      authzID = new StringArgument("authzid", 'a', "authzID", false, false,
                                   true, "{authzID}", null, null,
                                   MSGID_LDAPPWMOD_DESCRIPTION_AUTHZID);
      argParser.addArgument(authzID);


      provideDNForAuthzID =
           new BooleanArgument("providednforauthzid", 'A',"provideDNForAuthZID",
                    MSGID_LDAPPWMOD_DESCRIPTION_PROVIDE_DN_FOR_AUTHZID);
      argParser.addArgument(provideDNForAuthzID);


      newPW = new StringArgument("newpw", 'n', "newPassword", false, false,
                                 true, "{newPassword}", null, null,
                                 MSGID_LDAPPWMOD_DESCRIPTION_NEWPW);
      argParser.addArgument(newPW);


      newPWFile = new FileBasedArgument("newpwfile", 'N', "newPasswordFile",
                                        false, false, "{file}", null, null,
                                        MSGID_LDAPPWMOD_DESCRIPTION_NEWPWFILE);
      argParser.addArgument(newPWFile);


      currentPW =
           new StringArgument("currentpw", 'c', "currentPassword", false, false,
                              true, "{currentPassword}", null,  null,
                              MSGID_LDAPPWMOD_DESCRIPTION_CURRENTPW);
      argParser.addArgument(currentPW);


      currentPWFile =
           new FileBasedArgument("currentpwfile", 'C', "currentPasswordFile",
                                 false, false, "{file}", null, null,
                                 MSGID_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE);
      argParser.addArgument(currentPWFile);


      sslBlindTrust =
           new BooleanArgument("blindtrust", 'X', "trustAllCertificates",
                               MSGID_LDAPPWMOD_DESCRIPTION_BLIND_TRUST);
      argParser.addArgument(sslBlindTrust);


      sslKeyStore =
           new StringArgument("sslkeystore", 'k', "sslKeyStore", false, false,
                              true, "{file}", null, null,
                              MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE);
      argParser.addArgument(sslKeyStore);


      sslKeyStorePINFile =
           new FileBasedArgument("sslkeystorepin", 'K', "sslKeyStorePINFile",
                                 false, false, "{file}", null, null,
                                 MSGID_LDAPPWMOD_DESCRIPTION_KEYSTORE_PINFILE);
      argParser.addArgument(sslKeyStorePINFile);


      sslTrustStore =
           new StringArgument("ssltruststore", 't', "sslTrustStore", false,
                              false, true, "{file}", null, null,
                              MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE);
      argParser.addArgument(sslTrustStore);


      sslTrustStorePINFile =
           new FileBasedArgument("ssltruststorepin", 'T',
                    "sslTrustStorePINFile", false, false, "{file}", null, null,
                    MSGID_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PINFILE);
      argParser.addArgument(sslTrustStorePINFile);


      showUsage = new BooleanArgument("help", 'H', "help",
                                      MSGID_LDAPPWMOD_DESCRIPTION_USAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_LDAPPWMOD_CANNOT_INITIALIZE_ARGS;
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
      int    msgID   = MSGID_LDAPPWMOD_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }


    // If the usage argument was provided, then we don't need to do anything
    // else.
    if (showUsage.isPresent())
    {
      return 0;
    }


    // Make sure that the user didn't specify any conflicting arguments.
    if (bindPW.isPresent() && bindPWFile.isPresent())
    {
      int    msgID   = MSGID_LDAPPWMOD_CONFLICTING_ARGS;
      String message = getMessage(msgID, bindPW.getLongIdentifier(),
                                  bindPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    if (newPW.isPresent() && newPWFile.isPresent())
    {
      int    msgID   = MSGID_LDAPPWMOD_CONFLICTING_ARGS;
      String message = getMessage(msgID, newPW.getLongIdentifier(),
                                  newPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    if (currentPW.isPresent() && currentPWFile.isPresent())
    {
      int    msgID   = MSGID_LDAPPWMOD_CONFLICTING_ARGS;
      String message = getMessage(msgID, currentPW.getLongIdentifier(),
                                  currentPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    if (useSSL.isPresent() && useStartTLS.isPresent())
    {
      int    msgID   = MSGID_LDAPPWMOD_CONFLICTING_ARGS;
      String message = getMessage(msgID, useSSL.getLongIdentifier(),
                                  useStartTLS.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // If a bind DN was provided, make sure that a password was given.  If a
    // password was given, make sure a bind DN was provided.  If neither were
    // given, then make sure that an authorization ID and the current password
    // were provided.
    if (bindDN.isPresent())
    {
      if (! (bindPW.isPresent() || bindPWFile.isPresent()))
      {
        int    msgID   = MSGID_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER;
        String message = getMessage(msgID);

        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }
    }
    else if (bindPW.isPresent() || bindPWFile.isPresent())
    {
      int    msgID   = MSGID_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER;
      String message = getMessage(msgID);

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }
    else
    {
      if (provideDNForAuthzID.isPresent())
      {
        int    msgID   = MSGID_LDAPPWMOD_DEPENDENT_ARGS;
        String message = getMessage(msgID,
                                    provideDNForAuthzID.getLongIdentifier(),
                                    bindDN.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }

      if (! (authzID.isPresent() &&
             (currentPW.isPresent() || currentPWFile.isPresent())))
      {
        int    msgID   = MSGID_LDAPPWMOD_ANON_REQUIRES_AUTHZID_AND_CURRENTPW;
        String message = getMessage(msgID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return 1;
      }
    }


    // Get the host and port.
    String host = ldapHost.getValue();
    int    port;
    try
    {
      port = ldapPort.getIntValue();
    }
    catch (Exception e)
    {
      // This should never happen.
      err.println(e);
      return 1;
    }


    // Perform a basic Directory Server bootstrap if appropriate.
    if (initializeServer)
    {
      DirectoryServer.bootstrapClient();
    }


    // Establish a connection to the Directory Server.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    connectionOptions.setUseSSL(useSSL.isPresent());
    connectionOptions.setStartTLS(useStartTLS.isPresent());
    connectionOptions.setVersionNumber(3);
    if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
    {
      try
      {
        SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(sslBlindTrust.isPresent(),
                                  sslKeyStore.getValue(),
                                  sslKeyStorePINFile.getValue(),
                                  sslTrustStore.getValue(),
                                  sslTrustStorePINFile.getValue());
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDAPPWMOD_ERROR_INITIALIZING_SSL;
        String message = getMessage(msgID, String.valueOf(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    LDAPConnection connection = new LDAPConnection(host, port,
                                                   connectionOptions, out, err);
    String dn;
    String pw;
    if (bindPW.isPresent())
    {
      dn = bindDN.getValue();
      pw = bindPW.getValue();
    }
    else if (bindPWFile.isPresent())
    {
      dn = bindDN.getValue();
      pw = bindPWFile.getValue();
    }
    else
    {
      dn = null;
      pw = null;
    }

    try
    {
      connection.connectToHost(dn, pw, nextMessageID);
    }
    catch (LDAPConnectionException lce)
    {
      int    msgID   = MSGID_LDAPPWMOD_CANNOT_CONNECT;
      String message = getMessage(msgID, lce.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return lce.getErrorCode();
    }

    ASN1Reader reader = connection.getASN1Reader();
    ASN1Writer writer = connection.getASN1Writer();


    // Construct the password modify request.
    ArrayList<ASN1Element> requestElements = new ArrayList<ASN1Element>(3);
    if (authzID.isPresent())
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_USER_ID,
                                              authzID.getValue()));
    }
    else if (provideDNForAuthzID.isPresent())
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_USER_ID,
                                              "dn:" + dn));
    }

    if (currentPW.isPresent())
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              currentPW.getValue()));
    }
    else if (provideDNForAuthzID.isPresent())
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              pw));
    }

    if (newPW.isPresent())
    {
      requestElements.add(new ASN1OctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
                                              newPW.getValue()));
    }

    ASN1OctetString requestValue =
         new ASN1OctetString(new ASN1Sequence(requestElements).encode());

    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_PASSWORD_MODIFY_REQUEST,
                                       requestValue);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), extendedRequest);


    // Send the request to the server and read the response.
    try
    {
      writer.writeElement(requestMessage.encode());
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPPWMOD_CANNOT_SEND_PWMOD_REQUEST;
      String message = getMessage(msgID, String.valueOf(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeElement(requestMessage.encode());
      }
      catch (Exception e2) {}

      try
      {
        reader.close();
        writer.close();
      } catch (Exception e2) {}

      return 1;
    }


    // Read the response from the server.
    LDAPMessage responseMessage = null;
    try
    {
      ASN1Sequence responseSequence = reader.readElement().decodeAsSequence();
      responseMessage = LDAPMessage.decode(responseSequence);
    }
    catch (Exception e)
    {
      int msgID = MSGID_LDAPPWMOD_CANNOT_READ_PWMOD_RESPONSE;
      String message = getMessage(msgID, String.valueOf(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeElement(requestMessage.encode());
      }
      catch (Exception e2) {}

      try
      {
        reader.close();
        writer.close();
      } catch (Exception e2) {}

      return 1;
    }


    // Make sure that the response was acceptable.
    ExtendedResponseProtocolOp extendedResponse =
         responseMessage.getExtendedResponseProtocolOp();
    int resultCode = extendedResponse.getResultCode();
    if (resultCode != LDAPResultCode.SUCCESS)
    {
      int    msgID   = MSGID_LDAPPWMOD_FAILED;
      String message = getMessage(msgID, resultCode);
      err.println(wrapText(message, MAX_LINE_WIDTH));

      String errorMessage = extendedResponse.getErrorMessage();
      if ((errorMessage != null) && (errorMessage.length() > 0))
      {
        msgID   = MSGID_LDAPPWMOD_FAILURE_ERROR_MESSAGE;
        message = getMessage(msgID, errorMessage);
        err.println(wrapText(message, MAX_LINE_WIDTH));
      }

      DN matchedDN = extendedResponse.getMatchedDN();
      if (matchedDN != null)
      {
        msgID   = MSGID_LDAPPWMOD_FAILURE_MATCHED_DN;
        message = getMessage(msgID, matchedDN.toString());
        err.println(wrapText(message, MAX_LINE_WIDTH));
      }

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeElement(requestMessage.encode());
      }
      catch (Exception e) {}

      try
      {
        reader.close();
        writer.close();
      } catch (Exception e) {}

      return resultCode;
    }
    else
    {
      int    msgID   = MSGID_LDAPPWMOD_SUCCESSFUL;
      String message = getMessage(msgID);
      out.println(wrapText(message, MAX_LINE_WIDTH));

      String additionalInfo = extendedResponse.getErrorMessage();
      if ((additionalInfo != null) && (additionalInfo.length() > 0))
      {
        msgID   = MSGID_LDAPPWMOD_ADDITIONAL_INFO;
        message = getMessage(msgID, additionalInfo);
        out.println(wrapText(message, MAX_LINE_WIDTH));
      }
    }


    // See if the response included a generated password.
    ASN1OctetString responseValue = extendedResponse.getValue();
    if (responseValue != null)
    {
      try
      {
        ASN1Sequence responseSequence =
             ASN1Sequence.decodeAsSequence(responseValue.value());
        for (ASN1Element e : responseSequence.elements())
        {
          if (e.getType() == TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD)
          {
            int    msgID   = MSGID_LDAPPWMOD_GENERATED_PASSWORD;
            String message = getMessage(msgID,
                                        e.decodeAsOctetString().stringValue());
            out.println(wrapText(message, MAX_LINE_WIDTH));
          }
          else
          {
            int    msgID   = MSGID_LDAPPWMOD_UNRECOGNIZED_VALUE_TYPE;
            String message = getMessage(msgID, byteToHex(e.getType()));
            err.println(wrapText(message, MAX_LINE_WIDTH));
          }
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDAPPWMOD_COULD_NOT_DECODE_RESPONSE_VALUE;
        String message = getMessage(msgID, String.valueOf(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));

        try
        {
          requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                           new UnbindRequestProtocolOp());
          writer.writeElement(requestMessage.encode());
        }
        catch (Exception e2) {}

        try
        {
          reader.close();
          writer.close();
        } catch (Exception e2) {}

        return 1;
      }
    }


    // Unbind from the server and close the connection.
    try
    {
      requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                       new UnbindRequestProtocolOp());
      writer.writeElement(requestMessage.encode());
    }
    catch (Exception e) {}

    try
    {
      reader.close();
      writer.close();
    } catch (Exception e) {}

    return 0;
  }
}

