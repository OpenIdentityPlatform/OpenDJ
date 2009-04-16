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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;



import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.PasswordPolicyWarningType;
import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.types.*;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;



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
   * The fully-qualified name of this class.
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
      System.exit(filterExitCode(returnCode));
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
    StringArgument    certNickname           = null;
    FileBasedArgument currentPWFile;
    FileBasedArgument newPWFile;
    FileBasedArgument sslKeyStorePINFile;
    FileBasedArgument sslTrustStorePINFile;
    IntegerArgument   ldapPort;
    StringArgument    authzID;
    StringArgument    bindDN;
    StringArgument    bindPW;
    StringArgument    controlStr;
    StringArgument    currentPW;
    StringArgument    ldapHost;
    StringArgument    newPW;
    StringArgument    sslKeyStore;
    StringArgument    sslKeyStorePIN;
    StringArgument    sslTrustStore;
    StringArgument    sslTrustStorePIN;
    StringArgument    propertiesFileArgument;
    BooleanArgument   noPropertiesFileArgument;


    // Initialize the argument parser.
    Message toolDescription = INFO_LDAPPWMOD_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);

    try
    {
      propertiesFileArgument = new StringArgument("propertiesFilePath",
          null, OPTION_LONG_PROP_FILE_PATH,
          false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      ldapHost = new StringArgument("ldaphost", OPTION_SHORT_HOST,
                                    OPTION_LONG_HOST, false, false,
                                    true, INFO_HOST_PLACEHOLDER.get(),
                                    "127.0.0.1", null,
                                    INFO_LDAPPWMOD_DESCRIPTION_HOST.get());
      ldapHost.setPropertyName(OPTION_LONG_HOST);
      argParser.addArgument(ldapHost);


      ldapPort = new IntegerArgument(
              "ldapport", OPTION_SHORT_PORT,
              OPTION_LONG_PORT, false, false,
              true, INFO_PORT_PLACEHOLDER.get(), 389,
              null, true, 1, true,
              65535, INFO_LDAPPWMOD_DESCRIPTION_PORT.get());
      ldapPort.setPropertyName(OPTION_LONG_PORT);
      argParser.addArgument(ldapPort);


      useSSL = new BooleanArgument("usessl", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   INFO_LDAPPWMOD_DESCRIPTION_USE_SSL.get());
      useSSL.setPropertyName(OPTION_LONG_USE_SSL);
      argParser.addArgument(useSSL);


      useStartTLS = new BooleanArgument("usestarttls", OPTION_SHORT_START_TLS,
                             OPTION_LONG_START_TLS,
                             INFO_LDAPPWMOD_DESCRIPTION_USE_STARTTLS.get());
      useStartTLS.setPropertyName(OPTION_LONG_START_TLS);
      argParser.addArgument(useStartTLS);


      bindDN = new StringArgument("binddn", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  INFO_BINDDN_PLACEHOLDER.get(), null, null,
                                  INFO_LDAPPWMOD_DESCRIPTION_BIND_DN.get());
      bindDN.setPropertyName(OPTION_LONG_BINDDN);
      argParser.addArgument(bindDN);


      bindPW = new StringArgument("bindpw", OPTION_SHORT_BINDPWD,
                                  OPTION_LONG_BINDPWD, false, false,
                                  true, INFO_BINDPWD_PLACEHOLDER.get(), null,
                                  null,
                                  INFO_LDAPPWMOD_DESCRIPTION_BIND_PW.get());
      bindPW.setPropertyName(OPTION_LONG_BINDPWD);
      argParser.addArgument(bindPW);


      bindPWFile =
           new FileBasedArgument("bindpwfile", OPTION_SHORT_BINDPWD_FILE,
                                 OPTION_LONG_BINDPWD_FILE, false,
                                 false, INFO_BINDPWD_FILE_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_LDAPPWMOD_DESCRIPTION_BIND_PW_FILE.get());
      bindPWFile.setPropertyName(OPTION_LONG_BINDPWD_FILE);
      argParser.addArgument(bindPWFile);


      authzID = new StringArgument("authzid", 'a', "authzID", false, false,
                                   true, INFO_PROXYAUTHID_PLACEHOLDER.get(),
                                   null, null,
                                   INFO_LDAPPWMOD_DESCRIPTION_AUTHZID.get());
      authzID.setPropertyName("authzID");
      argParser.addArgument(authzID);


      provideDNForAuthzID =
           new BooleanArgument("providednforauthzid", 'A',"provideDNForAuthzID",
                    INFO_LDAPPWMOD_DESCRIPTION_PROVIDE_DN_FOR_AUTHZID.get());
      provideDNForAuthzID.setPropertyName("provideDNForAuthzID");
      argParser.addArgument(provideDNForAuthzID);


      newPW = new StringArgument("newpw", 'n', "newPassword", false, false,
                                 true, INFO_NEW_PASSWORD_PLACEHOLDER.get(),
                                 null, null,
                                 INFO_LDAPPWMOD_DESCRIPTION_NEWPW.get());
      newPW.setPropertyName("newPassword");
      argParser.addArgument(newPW);


      newPWFile = new FileBasedArgument(
              "newpwfile", 'N', "newPasswordFile",
              false, false, INFO_FILE_PLACEHOLDER.get(), null, null,
              INFO_LDAPPWMOD_DESCRIPTION_NEWPWFILE.get());
      newPWFile.setPropertyName("newPasswordFile");
      argParser.addArgument(newPWFile);


      currentPW =
           new StringArgument("currentpw", 'c', "currentPassword", false, false,
                              true, INFO_CURRENT_PASSWORD_PLACEHOLDER.get(),
                              null,  null,
                              INFO_LDAPPWMOD_DESCRIPTION_CURRENTPW.get());
      currentPW.setPropertyName("currentPassword");
      argParser.addArgument(currentPW);


      currentPWFile =
           new FileBasedArgument(
                   "currentpwfile", 'C', "currentPasswordFile",
                   false, false, INFO_FILE_PLACEHOLDER.get(), null, null,
                   INFO_LDAPPWMOD_DESCRIPTION_CURRENTPWFILE.get());
      currentPWFile.setPropertyName("currentPasswordFile");
      argParser.addArgument(currentPWFile);


      sslBlindTrust =
           new BooleanArgument("blindtrust", 'X', "trustAll",
                               INFO_LDAPPWMOD_DESCRIPTION_BLIND_TRUST.get());
      sslBlindTrust.setPropertyName("trustAll");
      argParser.addArgument(sslBlindTrust);


      sslKeyStore =
           new StringArgument("keystorepath", OPTION_SHORT_KEYSTOREPATH,
                              OPTION_LONG_KEYSTOREPATH, false, false,
                              true, INFO_KEYSTOREPATH_PLACEHOLDER.get(), null,
                              null,
                              INFO_LDAPPWMOD_DESCRIPTION_KEYSTORE.get());
      sslKeyStore.setPropertyName(OPTION_LONG_KEYSTOREPATH);
      argParser.addArgument(sslKeyStore);


      sslKeyStorePIN =
           new StringArgument("keystorepassword",
                              OPTION_SHORT_KEYSTORE_PWD,
                              OPTION_LONG_KEYSTORE_PWD ,
                              false, false, true,
                              INFO_KEYSTORE_PWD_PLACEHOLDER.get(),
                              null, null,
                              INFO_LDAPPWMOD_DESCRIPTION_KEYSTORE_PIN.get());
      sslKeyStorePIN.setPropertyName(OPTION_LONG_KEYSTORE_PWD);
      argParser.addArgument(sslKeyStorePIN);


      sslKeyStorePINFile =
           new FileBasedArgument(
                   "keystorepasswordfile",
                   OPTION_SHORT_KEYSTORE_PWD_FILE,
                   OPTION_LONG_KEYSTORE_PWD_FILE,
                   false, false, INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(),
                   null, null,
                   INFO_LDAPPWMOD_DESCRIPTION_KEYSTORE_PINFILE.get());
      sslKeyStorePINFile.setPropertyName(OPTION_LONG_KEYSTORE_PWD_FILE);
      argParser.addArgument(sslKeyStorePINFile);

      certNickname = new StringArgument("certnickname", null, "certNickname",
          false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_CERT_NICKNAME.get());
      certNickname.setPropertyName("certNickname");
      argParser.addArgument(certNickname);



      sslTrustStore =
           new StringArgument("truststorepath",
                              OPTION_SHORT_TRUSTSTOREPATH,
                              OPTION_LONG_TRUSTSTOREPATH, false,
                              false, true,
                              INFO_TRUSTSTOREPATH_PLACEHOLDER.get(), null, null,
                              INFO_LDAPPWMOD_DESCRIPTION_TRUSTSTORE.get());
      sslTrustStore.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
      argParser.addArgument(sslTrustStore);


      sslTrustStorePIN =
           new StringArgument("truststorepassword", null,
                              OPTION_LONG_TRUSTSTORE_PWD,
                              false, false, true,
                              INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null, null,
                              INFO_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PIN.get());
      sslTrustStorePIN.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
      argParser.addArgument(sslTrustStorePIN);


      sslTrustStorePINFile =
           new FileBasedArgument("truststorepasswordfile",
                    OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                    OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                    INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null,
                    null, INFO_LDAPPWMOD_DESCRIPTION_TRUSTSTORE_PINFILE.get());
      sslTrustStorePINFile.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
      argParser.addArgument(sslTrustStorePINFile);


      controlStr =
           new StringArgument("control", 'J', "control", false, true, true,
                    INFO_LDAP_CONTROL_PLACEHOLDER.get(),
                    null, null, INFO_DESCRIPTION_CONTROLS.get());
      controlStr.setPropertyName("control");
      argParser.addArgument(controlStr);


      showUsage = new BooleanArgument("help", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      INFO_DESCRIPTION_USAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
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
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If the usage or version argument was provided,
    // then we don't need to do anything else.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }


    // Make sure that the user didn't specify any conflicting arguments.
    if (bindPW.isPresent() && bindPWFile.isPresent())
    {
      Message message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
              bindPW.getLongIdentifier(),
              bindPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (newPW.isPresent() && newPWFile.isPresent())
    {
      Message message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
              newPW.getLongIdentifier(),
              newPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (currentPW.isPresent() && currentPWFile.isPresent())
    {
      Message message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
              currentPW.getLongIdentifier(),
              currentPWFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (useSSL.isPresent() && useStartTLS.isPresent())
    {
      Message message = ERR_LDAPPWMOD_CONFLICTING_ARGS.get(
              useSSL.getLongIdentifier(),
              useStartTLS.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (sslKeyStorePIN.isPresent() && sslKeyStorePINFile.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              sslKeyStorePIN.getLongIdentifier(),
              sslKeyStorePINFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (sslTrustStorePIN.isPresent() && sslTrustStorePINFile.isPresent())
    {
      Message message = ERR_TOOL_CONFLICTING_ARGS.get(
              sslTrustStorePIN.getLongIdentifier(),
              sslTrustStorePINFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If a bind DN was provided, make sure that a password was given.  If a
    // password was given, make sure a bind DN was provided.  If neither were
    // given, then make sure that an authorization ID and the current password
    // were provided.
    if (bindDN.isPresent())
    {
      if (! (bindPW.isPresent() || bindPWFile.isPresent()))
      {
        Message message = ERR_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER.get();

        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }
    else if (bindPW.isPresent() || bindPWFile.isPresent())
    {
      Message message = ERR_LDAPPWMOD_BIND_DN_AND_PW_MUST_BE_TOGETHER.get();

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return CLIENT_SIDE_PARAM_ERROR;
    }
    else
    {
      if (provideDNForAuthzID.isPresent())
      {
        Message message =
                ERR_LDAPPWMOD_DEPENDENT_ARGS.get(
                        provideDNForAuthzID.getLongIdentifier(),
                        bindDN.getLongIdentifier());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return CLIENT_SIDE_PARAM_ERROR;
      }

      if (! (authzID.isPresent() &&
             (currentPW.isPresent() || currentPWFile.isPresent())))
      {
        Message message =
                ERR_LDAPPWMOD_ANON_REQUIRES_AUTHZID_AND_CURRENTPW.get();
        err.println(wrapText(message, MAX_LINE_WIDTH));
        err.println(argParser.getUsage());
        return CLIENT_SIDE_PARAM_ERROR;
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
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // If a control string was provided, then decode the requested controls.
    ArrayList<Control> controls = new ArrayList<Control>();
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
          return CLIENT_SIDE_PARAM_ERROR;
        }
        controls.add(ctrl);
      }
    }


    // Perform a basic Directory Server bootstrap if appropriate.
    if (initializeServer)
    {
      EmbeddedUtils.initializeForClientUse();
    }


    // Establish a connection to the Directory Server.
    AtomicInteger nextMessageID = new AtomicInteger(1);
    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    connectionOptions.setUseSSL(useSSL.isPresent());
    connectionOptions.setStartTLS(useStartTLS.isPresent());
    connectionOptions.setVersionNumber(3);
    if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
    {
      String keyPIN = null;
      if (sslKeyStorePIN.isPresent())
      {
        keyPIN = sslKeyStorePIN.getValue();
      }
      else if (sslKeyStorePINFile.isPresent())
      {
        keyPIN = sslKeyStorePINFile.getValue();
      }

      String trustPIN = null;
      if (sslTrustStorePIN.isPresent())
      {
        trustPIN = sslTrustStorePIN.getValue();
      }
      else if (sslTrustStorePINFile.isPresent())
      {
        trustPIN = sslTrustStorePINFile.getValue();
      }

      try
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
        SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(sslBlindTrust.isPresent(),
                                  sslKeyStore.getValue(), keyPIN, clientAlias,
                                  sslTrustStore.getValue(), trustPIN);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }
      catch (Exception e)
      {
        Message message =
                ERR_LDAPPWMOD_ERROR_INITIALIZING_SSL.get(String.valueOf(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return CLIENT_SIDE_PARAM_ERROR;
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
      Message message = ERR_LDAPPWMOD_CANNOT_CONNECT.get(lce.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return lce.getResultCode();
    }

    LDAPReader reader = connection.getLDAPReader();
    LDAPWriter writer = connection.getLDAPWriter();


    // Construct the password modify request.
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer asn1Writer = ASN1.getWriter(builder);

    try
    {
    asn1Writer.writeStartSequence();
    if (authzID.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_USER_ID,
          authzID.getValue());
    }
    else if (provideDNForAuthzID.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_USER_ID, "dn:" + dn);
    }

    if (currentPW.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              currentPW.getValue());
    }
    else if (currentPWFile.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              currentPWFile.getValue());
    }
    else if (provideDNForAuthzID.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_OLD_PASSWORD,
                                              pw);
    }

    if (newPW.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
                                              newPW.getValue());
    }
    else if (newPWFile.isPresent())
    {
      asn1Writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD,
                                              newPWFile.getValue());
    }
    asn1Writer.writeEndSequence();
    }
    catch(Exception e)
    {
      err.println(e);
    }

    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_PASSWORD_MODIFY_REQUEST,
                                       builder.toByteString());
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), extendedRequest,
                         controls);


    // Send the request to the server and read the response.
    try
    {
      writer.writeMessage(requestMessage);
    }
    catch (Exception e)
    {
      Message message = ERR_LDAPPWMOD_CANNOT_SEND_PWMOD_REQUEST.get(
              String.valueOf(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeMessage(requestMessage);
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
      responseMessage = reader.readMessage();
    }
    catch (Exception e)
    {
      Message message = ERR_LDAPPWMOD_CANNOT_READ_PWMOD_RESPONSE.get(
              String.valueOf(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeMessage(requestMessage);
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
      Message message = ERR_LDAPPWMOD_FAILED.get(resultCode);
      err.println(wrapText(message, MAX_LINE_WIDTH));

      Message errorMessage = extendedResponse.getErrorMessage();
      if ((errorMessage != null) && (errorMessage.length() > 0))
      {

        message = ERR_LDAPPWMOD_FAILURE_ERROR_MESSAGE.get(errorMessage);
        err.println(wrapText(message, MAX_LINE_WIDTH));
      }

      DN matchedDN = extendedResponse.getMatchedDN();
      if (matchedDN != null)
      {

        message = ERR_LDAPPWMOD_FAILURE_MATCHED_DN.get(matchedDN.toString());
        err.println(wrapText(message, MAX_LINE_WIDTH));
      }

      try
      {
        requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                         new UnbindRequestProtocolOp());
        writer.writeMessage(requestMessage);
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
      Message message = INFO_LDAPPWMOD_SUCCESSFUL.get();
      out.println(wrapText(message, MAX_LINE_WIDTH));

      Message additionalInfo = extendedResponse.getErrorMessage();
      if ((additionalInfo != null) && (additionalInfo.length() > 0))
      {

        message = INFO_LDAPPWMOD_ADDITIONAL_INFO.get(additionalInfo);
        out.println(wrapText(message, MAX_LINE_WIDTH));
      }
    }


    // See if the response included any controls that we recognize, and if so
    // then handle them.
    List<Control> responseControls = responseMessage.getControls();
    if (responseControls != null)
    {
      for (Control c : responseControls)
      {
        if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          try
          {
            PasswordPolicyResponseControl pwPolicyControl =
              PasswordPolicyResponseControl.DECODER
                .decode(c.isCritical(), ((LDAPControl) c).getValue());

            PasswordPolicyWarningType pwPolicyWarningType =
                 pwPolicyControl.getWarningType();
            if (pwPolicyWarningType != null)
            {
              Message message = INFO_LDAPPWMOD_PWPOLICY_WARNING.get(
                      pwPolicyWarningType.toString(),
                      pwPolicyControl.getWarningValue());
              out.println(wrapText(message, MAX_LINE_WIDTH));
            }

            PasswordPolicyErrorType pwPolicyErrorType =
                 pwPolicyControl.getErrorType();
            if (pwPolicyErrorType != null)
            {
              Message message = INFO_LDAPPWMOD_PWPOLICY_ERROR.get(
                      pwPolicyErrorType.toString());
              out.println(wrapText(message, MAX_LINE_WIDTH));
            }
          }
          catch (Exception e)
          {
            Message message = ERR_LDAPPWMOD_CANNOT_DECODE_PWPOLICY_CONTROL.get(
                    String.valueOf(e));
            err.println(wrapText(message, MAX_LINE_WIDTH));
          }
        }
      }
    }


    // See if the response included a generated password.
    ByteString responseValue = extendedResponse.getValue();
    if (responseValue != null)
    {
      try
      {
        ASN1Reader asn1Reader = ASN1.getReader(responseValue);
        asn1Reader.readStartSequence();
        while(asn1Reader.hasNextElement())
        {
          if (asn1Reader.peekType() == TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD)
          {
            Message message = INFO_LDAPPWMOD_GENERATED_PASSWORD.get(
                    asn1Reader.readOctetStringAsString());
            out.println(wrapText(message, MAX_LINE_WIDTH));
          }
          else
          {
            Message message = ERR_LDAPPWMOD_UNRECOGNIZED_VALUE_TYPE.get(
                    asn1Reader.readOctetStringAsString());
            err.println(wrapText(message, MAX_LINE_WIDTH));
          }
        }
        asn1Reader.readEndSequence();
      }
      catch (Exception e)
      {
        Message message = ERR_LDAPPWMOD_COULD_NOT_DECODE_RESPONSE_VALUE.get(
                String.valueOf(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));

        try
        {
          requestMessage = new LDAPMessage(nextMessageID.getAndIncrement(),
                                           new UnbindRequestProtocolOp());
          writer.writeMessage(requestMessage);
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
      writer.writeMessage(requestMessage);
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

