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
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.controls.AccountUsableResponseControl;
import org.opends.server.controls.EntryChangeNotificationControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.controls.MatchedValuesFilter;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.Base64;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchResultDoneProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.SearchResultReferenceProtocolOp;
import org.opends.server.types.DN;
import org.opends.server.types.NullOutputStream;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;




/**
 * This class provides a tool that can be used to issue search requests to the
 * Directory Server.
 */
public class LDAPSearch
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPSearch";



  // The message ID counter to use for requests.
  private AtomicInteger nextMessageID;

  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;



  /**
   * Constructor for the LDAPSearch object.
   *
   * @param  nextMessageID  The message ID counter to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPSearch(AtomicInteger nextMessageID, PrintStream out,
                    PrintStream err)
  {
    this.nextMessageID = nextMessageID;
    this.out           = out;
    this.err           = err;
  }


  /**
   * Execute the search based on the specified input parameters.
   *
   * @param connection     The connection to use for the search.
   * @param baseDN         The base DN for the search request.
   * @param filters        The filters to use for the results.
   * @param attributes     The attributes to return in the results.
   * @param searchOptions  The constraints for the search.
   * @param wrapColumn     The column at which to wrap long lines.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void executeSearch(LDAPConnection connection, String baseDN,
                            ArrayList<LDAPFilter> filters,
                            LinkedHashSet<String> attributes,
                            LDAPSearchOptions searchOptions,
                            int wrapColumn )
         throws IOException, LDAPException
  {
    for (LDAPFilter filter: filters)
    {
      ASN1OctetString asn1OctetStr = new ASN1OctetString(baseDN);

      SearchRequestProtocolOp protocolOp =
        new SearchRequestProtocolOp(asn1OctetStr,
                                    searchOptions.getSearchScope(),
                                    searchOptions.getDereferencePolicy(),
                                    searchOptions.getSizeLimit(),
                                    searchOptions.getTimeLimit(),
                              false, filter, attributes);
      try
      {
        boolean typesOnly = searchOptions.getTypesOnly();
        LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                              protocolOp,
                                              searchOptions.getControls());
        int numBytes =
            connection.getASN1Writer().writeElement(message.encode());

        byte opType;
        do
        {
          int resultCode = 0;
          String errorMessage = null;
          ASN1Element element = connection.getASN1Reader().readElement();
          LDAPMessage responseMessage =
               LDAPMessage.decode(ASN1Sequence.decodeAsSequence(element));
          ArrayList<LDAPControl> controls = responseMessage.getControls();


          opType = responseMessage.getProtocolOpType();
          switch(opType)
          {
            case OP_TYPE_SEARCH_RESULT_ENTRY:
              for (LDAPControl c : controls)
              {
                if (c.getOID().equals(OID_ENTRY_CHANGE_NOTIFICATION))
                {
                  try
                  {
                    EntryChangeNotificationControl ecn =
                         EntryChangeNotificationControl.decodeControl(
                              c.getControl());
                    out.println("# Persistent Search Change Type:  " +
                                ecn.getChangeType().toString());
                    DN previousDN = ecn.getPreviousDN();
                    if (previousDN != null)
                    {
                      out.println("# Persistent Search Previous DN:  " +
                                  previousDN.toString());
                    }
                  } catch (Exception e) {}
                }
                else if (c.getOID().equals(OID_ACCOUNT_USABLE_CONTROL))
                {
                  try
                  {
                    AccountUsableResponseControl acrc =
                         AccountUsableResponseControl.decodeControl(
                              c.getControl());
                    out.println("# Account Usable Response Control");
                    if (acrc.isUsable())
                    {
                      out.println("#   Account is usable");
                      if (acrc.getSecondsBeforeExpiration() > 0)
                      {
                        int    timeToExp    = acrc.getSecondsBeforeExpiration();
                        String timeToExpStr = secondsToTimeString(timeToExp);
                        out.println("#   Time until expiration:  " +
                                    timeToExpStr);
                      }
                    }
                    else
                    {
                      out.println("#   Account is not usable");
                      if (acrc.isInactive())
                      {
                        out.println("#   Account is inactive");
                      }
                      if (acrc.isReset())
                      {
                        out.println("#   Password has been reset");
                      }
                      if (acrc.isExpired())
                      {
                        out.println("#   Password is expired");

                        if (acrc.getRemainingGraceLogins() > 0)
                        {
                          out.println("#   Grace logins remaining:  " +
                                      acrc.getRemainingGraceLogins());
                        }
                      }
                      if (acrc.isLocked())
                      {
                        out.println("#   Account is locked");
                        if (acrc.getSecondsBeforeUnlock() > 0)
                        {
                          int timeToUnlock = acrc.getSecondsBeforeUnlock();
                          String timeToUnlockStr =
                                      secondsToTimeString(timeToUnlock);
                          out.println("#   Time until automatic unlock:  " +
                                      timeToUnlockStr);
                        }
                      }
                    }
                  } catch (Exception e) {}
                }
              }

              SearchResultEntryProtocolOp searchEntryOp =
                   responseMessage.getSearchResultEntryProtocolOp();
              StringBuilder sb = new StringBuilder();
              toLDIF(searchEntryOp, sb, wrapColumn, typesOnly);
              out.println(sb.toString());
              break;

            case OP_TYPE_SEARCH_RESULT_REFERENCE:
              SearchResultReferenceProtocolOp searchRefOp =
                   responseMessage.getSearchResultReferenceProtocolOp();
              out.println(searchRefOp.toString());
              break;

            case OP_TYPE_SEARCH_RESULT_DONE:
              SearchResultDoneProtocolOp searchOp =
                   responseMessage.getSearchResultDoneProtocolOp();
              resultCode = searchOp.getResultCode();
              errorMessage = searchOp.getErrorMessage();
              break;
            default:
              // FIXME - throw exception?
              int msgID = MSGID_SEARCH_OPERATION_INVALID_PROTOCOL;
              String msg = getMessage(msgID, opType);
              err.println(msg);
              break;
          }

          if(resultCode != SUCCESS && resultCode != REFERRAL)
          {
            int msgID = MSGID_OPERATION_FAILED;
            if(errorMessage == null)
            {
              errorMessage = "Result Code:" + resultCode;
            }
            throw new LDAPException(resultCode, msgID, errorMessage);
          }
          else if (errorMessage != null)
          {
            out.println();
            out.println(errorMessage);
          }

        } while(opType != OP_TYPE_SEARCH_RESULT_DONE);

      } catch(ASN1Exception ae)
      {
        assert debugException(CLASS_NAME, "executeSearch", ae);
        throw new IOException(ae.getMessage());
      }
    }
  }

  /**
   * Appends an LDIF representation of the entry to the provided buffer.
   *
   * @param  entry       The entry to be written as LDIF.
   * @param  buffer      The buffer to which the entry should be appended.
   * @param  wrapColumn  The column at which long lines should be wrapped.
   * @param  typesOnly   Indicates whether to include only attribute types
   *                     without values.
   */
  public void toLDIF(SearchResultEntryProtocolOp entry, StringBuilder buffer,
                     int wrapColumn, boolean typesOnly)
  {
    // Add the DN to the buffer.
    String dnString = entry.getDN().toString();
    int    colsRemaining;
    if (needsBase64Encoding(dnString))
    {
      dnString = Base64.encode(getBytes(dnString));
      buffer.append("dn:: ");

      colsRemaining = wrapColumn - 5;
    }
    else
    {
      buffer.append("dn: ");

      colsRemaining = wrapColumn - 4;
    }

    int dnLength = dnString.length();
    if ((dnLength <= colsRemaining) || (colsRemaining <= 0))
    {
      buffer.append(dnString);
      buffer.append(EOL);
    }
    else
    {
      buffer.append(dnString.substring(0, colsRemaining));
      buffer.append(EOL);

      int startPos = colsRemaining;
      while ((dnLength - startPos) > (wrapColumn - 1))
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos, (startPos+wrapColumn-1)));

        buffer.append(EOL);

        startPos += (wrapColumn-1);
      }

      if (startPos < dnLength)
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos));
        buffer.append(EOL);
      }
    }


    LinkedList<LDAPAttribute> attributes = entry.getAttributes();
    // Add the attributes to the buffer.
    for (LDAPAttribute a : attributes)
    {
      String name       = a.getAttributeType();
      int    nameLength = name.length();


      if(typesOnly)
      {
          buffer.append(name);
          buffer.append(EOL);
      } else
      {
        for (ASN1OctetString v : a.getValues())
        {
          String valueString;
          if (needsBase64Encoding(v.value()))
          {
            valueString = Base64.encode(v.value());
            buffer.append(name);
            buffer.append(":: ");

            colsRemaining = wrapColumn - nameLength - 3;
          } else
          {
            valueString = v.stringValue();
            buffer.append(name);
            buffer.append(": ");

            colsRemaining = wrapColumn - nameLength - 2;
          }

          int valueLength = valueString.length();
          if ((valueLength <= colsRemaining) || (colsRemaining <= 0))
          {
            buffer.append(valueString);
            buffer.append(EOL);
          } else
          {
            buffer.append(valueString.substring(0, colsRemaining));
            buffer.append(EOL);

            int startPos = colsRemaining;
            while ((valueLength - startPos) > (wrapColumn - 1))
            {
              buffer.append(" ");
              buffer.append(valueString.substring(startPos,
                                                (startPos+wrapColumn-1)));
              buffer.append(EOL);

              startPos += (wrapColumn-1);
            }

            if (startPos < valueLength)
            {
              buffer.append(" ");
              buffer.append(valueString.substring(startPos));
              buffer.append(EOL);
            }
          }
        }
      }
    }


    // Make sure to add an extra blank line to ensure that there will be one
    // between this entry and the next.
    buffer.append(EOL);
  }

  /**
   * The main method for LDAPSearch tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainSearch(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(retCode);
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapsearch tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainSearch(String[] args)
  {
    return mainSearch(args, true, System.out, System.err);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapsearch tool.
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

  public static int mainSearch(String[] args, boolean initializeServer,
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
    LDAPSearchOptions searchOptions = new LDAPSearchOptions();
    LDAPConnection connection = null;
    ArrayList<LDAPFilter> filters = new ArrayList<LDAPFilter>();
    LinkedHashSet<String> attributes = new LinkedHashSet<String>();

    BooleanArgument trustAll = null;
    BooleanArgument noop = null;
    StringArgument assertionFilter = null;
    StringArgument baseDN = null;
    StringArgument bindDN = null;
    StringArgument bindPassword = null;
    FileBasedArgument bindPasswordFile = null;
    StringArgument proxyAuthzID = null;
    BooleanArgument reportAuthzID = null;
    BooleanArgument usePasswordPolicyControl = null;
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
    IntegerArgument sizeLimit = null;
    IntegerArgument timeLimit = null;
    StringArgument searchScope = null;
    StringArgument dereferencePolicy = null;
    StringArgument saslOptions = null;
    BooleanArgument dontWrap = null;
    BooleanArgument typesOnly = null;
    StringArgument pSearchInfo = null;
    StringArgument matchedValuesFilter = null;


    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, false, true, 0, 0,
                                                  "[filter] [attributes ...]");

    try
    {
      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
                                    MSGID_DESCRIPTION_TRUSTALL);
      argParser.addArgument(trustAll);
      baseDN = new StringArgument("baseDN", 'b', "baseDN", true, false,
                                  true, "{baseDN}", null, null,
                                  MSGID_SEARCH_DESCRIPTION_BASEDN);
      argParser.addArgument(baseDN);
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
      proxyAuthzID = new StringArgument("proxy_authzid", 'Y', "proxyAs", false,
                                        false, true, "{authzID}", null, null,
                                        MSGID_DESCRIPTION_PROXY_AUTHZID);
      argParser.addArgument(proxyAuthzID);
      reportAuthzID = new BooleanArgument("reportauthzid", 'E',
                                          "reportAuthzID",
                                          MSGID_DESCRIPTION_REPORT_AUTHZID);
      argParser.addArgument(reportAuthzID);
      usePasswordPolicyControl = new BooleanArgument("usepwpolicycontrol", null,
                                          "usePasswordPolicyControl",
                                          MSGID_DESCRIPTION_USE_PWP_CONTROL);
      argParser.addArgument(usePasswordPolicyControl);
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
                                  MSGID_SEARCH_DESCRIPTION_FILENAME);
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
      sizeLimit = new IntegerArgument("sizeLimit", 'z', "sizeLimit",
                              false, false, true, "{sizeLimit}", 0, null,
                              MSGID_SEARCH_DESCRIPTION_SIZE_LIMIT);
      argParser.addArgument(sizeLimit);
      timeLimit = new IntegerArgument("timeLimit", 'l', "timeLimit",
                              false, false, true, "{timeLimit}", 0, null,
                              MSGID_SEARCH_DESCRIPTION_TIME_LIMIT);
      argParser.addArgument(timeLimit);
      searchScope = new StringArgument("searchScope", 's',
                                  "searchScope", false, false, true,
                                  "{searchScope}", null, null,
                                  MSGID_SEARCH_DESCRIPTION_SEARCH_SCOPE);
      argParser.addArgument(searchScope);
      dereferencePolicy = new StringArgument("dereferencePolicy", 'a',
                                  "dereferencePolicy", false, false, true,
                                  "{dereferencePolicy}", null, null,
                                  MSGID_SEARCH_DESCRIPTION_DEREFERENCE_POLICY);
      argParser.addArgument(dereferencePolicy);
      saslOptions = new StringArgument("saslOptions", 'o', "saslOptions", false,
                                       true, true, "{name=value}", null, null,
                                       MSGID_DESCRIPTION_SASL_PROPERTIES);
      argParser.addArgument(saslOptions);
      pSearchInfo = new StringArgument("psearchinfo", 'C', "persistentSearch",
                             false, false, true,
                             "ps[:changetype[:changesonly[:entrychgcontrols]]]",
                              null, null, MSGID_DESCRIPTION_PSEARCH_INFO);
      argParser.addArgument(pSearchInfo);
      dontWrap = new BooleanArgument("dontwrap", 'T', "dontWrap",
                                     MSGID_DESCRIPTION_DONT_WRAP);
      argParser.addArgument(dontWrap);
      noop = new BooleanArgument("no-op", 'n', "noop", MSGID_DESCRIPTION_NOOP);
      argParser.addArgument(noop);
      typesOnly = new BooleanArgument("typesOnly", 'A', "typesOnly",
                                      MSGID_DESCRIPTION_TYPES_ONLY);
      argParser.addArgument(typesOnly);
      assertionFilter = new StringArgument("assertionfilter", null,
                                 "assertionFilter", false, false, true,
                                 "{filter}", null, null,
                                 MSGID_DESCRIPTION_SEARCH_ASSERTION_FILTER);
      argParser.addArgument(assertionFilter);
      matchedValuesFilter = new StringArgument("matchedvalues", null,
                                     "matchedValuesFilter", false, true, true,
                                     "{filter}", null, null,
                                     MSGID_DESCRIPTION_MATCHED_VALUES_FILTER);
      argParser.addArgument(matchedValuesFilter);
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
      return 0;
    }

    ArrayList<String> filterAndAttributeStrings =
      argParser.getTrailingArguments();
    if(filterAndAttributeStrings.size() > 0)
    {
      String filterString = filterAndAttributeStrings.remove(0);
      try
      {
        filters.add(LDAPFilter.decode(filterString));
      } catch(LDAPException le)
      {
        assert debugException(CLASS_NAME, "main", le);
        err.println(le.getMessage());
        return 1;
      }
      // The rest are attributes
      for(String s : filterAndAttributeStrings)
      {
        attributes.add(s);
      }

    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      err.println("ERROR: Both -w and -j flags specified. " +
                  "Please specify one.");
      return 1;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      assert debugException(CLASS_NAME, "main", ae);
      err.println(ae.getMessage());
      return 1;
    }

    // Read the LDAP version number.
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


    // Indicate whether we should report the authorization ID and/or use the
    // password policy control.
    connectionOptions.setReportAuthzID(reportAuthzID.isPresent());
    connectionOptions.setUsePasswordPolicyControl(
         usePasswordPolicyControl.isPresent());


    String baseDNValue = baseDN.getValue();
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

    searchOptions.setTypesOnly(typesOnly.isPresent());
    searchOptions.setShowOperations(noop.isPresent());
    searchOptions.setVerbose(verbose.isPresent());
    searchOptions.setContinueOnError(continueOnError.isPresent());
    searchOptions.setEncoding(encodingStr.getValue());
    try
    {
      searchOptions.setTimeLimit(timeLimit.getIntValue());
      searchOptions.setSizeLimit(sizeLimit.getIntValue());
    } catch(ArgumentException ex1)
    {
      err.println(ex1.getMessage());
      return 1;
    }
    boolean val = searchOptions.setSearchScope(searchScope.getValue(), err);
    if(val == false)
    {
      return 1;
    }
    val = searchOptions.setDereferencePolicy(dereferencePolicy.getValue(), err);
    if(val == false)
    {
      return 1;
    }

    if(controlStr.hasValue())
    {
      String ctrlString = controlStr.getValue();
      LDAPControl ctrl = LDAPToolUtils.getControl(ctrlString, err);
      if(ctrl == null)
      {
        err.println("Invalid control specified:" + ctrlString);
        err.println(argParser.getUsage());
        return 1;
      }
      searchOptions.getControls().add(ctrl);
    }

    if (proxyAuthzID.isPresent())
    {
      ASN1OctetString authzIDOS  = new ASN1OctetString(proxyAuthzID.getValue());
      ASN1OctetString proxyValue = new ASN1OctetString(authzIDOS.encode());

      LDAPControl proxyControl =
        new LDAPControl(OID_PROXIED_AUTH_V2, true, proxyValue);
      searchOptions.getControls().add(proxyControl);
    }

    if (pSearchInfo.isPresent())
    {
      String infoString = toLowerCase(pSearchInfo.getValue().trim());
      HashSet<PersistentSearchChangeType> changeTypes =
           new HashSet<PersistentSearchChangeType>();
      boolean changesOnly = true;
      boolean returnECs = true;

      StringTokenizer tokenizer = new StringTokenizer(infoString, ":");

      if (! tokenizer.hasMoreTokens())
      {
        int    msgID   = MSGID_PSEARCH_MISSING_DESCRIPTOR;
        String message = getMessage(msgID);
        err.println(message);
        return 1;
      }
      else
      {
        String token = tokenizer.nextToken();
        if (! token.equals("ps"))
        {
          int    msgID   = MSGID_PSEARCH_DOESNT_START_WITH_PS;
          String message = getMessage(msgID, String.valueOf(infoString));
          err.println(message);
          return 1;
        }
      }

      if (tokenizer.hasMoreTokens())
      {
        StringTokenizer st = new StringTokenizer(tokenizer.nextToken(), ", ");
        while (st.hasMoreTokens())
        {
          String token = st.nextToken();
          if (token.equals("add"))
          {
            changeTypes.add(PersistentSearchChangeType.ADD);
          }
          else if (token.equals("delete") || token.equals("del"))
          {
            changeTypes.add(PersistentSearchChangeType.DELETE);
          }
          else if (token.equals("modify") || token.equals("mod"))
          {
            changeTypes.add(PersistentSearchChangeType.MODIFY);
          }
          else if (token.equals("modifydn") || token.equals("moddn") ||
                   token.equals("modrdn"))
          {
            changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
          }
          else if (token.equals("any") || token.equals("all"))
          {
            changeTypes.add(PersistentSearchChangeType.ADD);
            changeTypes.add(PersistentSearchChangeType.DELETE);
            changeTypes.add(PersistentSearchChangeType.MODIFY);
            changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
          }
          else
          {
            int    msgID   = MSGID_PSEARCH_INVALID_CHANGE_TYPE;
            String message = getMessage(msgID, String.valueOf(token));
            err.println(message);
            return 1;
          }
        }
      }

      if (changeTypes.isEmpty())
      {
        changeTypes.add(PersistentSearchChangeType.ADD);
        changeTypes.add(PersistentSearchChangeType.DELETE);
        changeTypes.add(PersistentSearchChangeType.MODIFY);
        changeTypes.add(PersistentSearchChangeType.MODIFY_DN);
      }

      if (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();
        if (token.equals("1") || token.equals("true") || token.equals("yes"))
        {
          changesOnly = true;
        }
        else if (token.equals("0") || token.equals("false") ||
                 token.equals("no"))
        {
          changesOnly = false;
        }
        else
        {
          int    msgID   = MSGID_PSEARCH_INVALID_CHANGESONLY;
          String message = getMessage(msgID, String.valueOf(token));
          err.println(message);
          return 1;
        }
      }

      if (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();
        if (token.equals("1") || token.equals("true") || token.equals("yes"))
        {
          returnECs = true;
        }
        else if (token.equals("0") || token.equals("false") ||
                 token.equals("no"))
        {
          returnECs = false;
        }
        else
        {
          int    msgID   = MSGID_PSEARCH_INVALID_RETURN_ECS;
          String message = getMessage(msgID, String.valueOf(token));
          err.println(message);
          return 1;
        }
      }

      PersistentSearchControl psearchControl =
           new PersistentSearchControl(changeTypes, changesOnly, returnECs);
      searchOptions.getControls().add(new LDAPControl(psearchControl));
    }

    if (assertionFilter.isPresent())
    {
      String filterString = assertionFilter.getValue();
      LDAPFilter filter;
      try
      {
        filter = LDAPFilter.decode(filterString);

        // FIXME -- Change this to the correct OID when the official one is
        //          assigned.
        LDAPControl assertionControl =
             new LDAPControl(OID_LDAP_ASSERTION, true,
                             new ASN1OctetString(filter.encode().encode()));
        searchOptions.getControls().add(assertionControl);
      }
      catch (LDAPException le)
      {
        err.println(getMessage(MSGID_LDAP_ASSERTION_INVALID_FILTER,
                               le.getMessage()));
        return 1;
      }
    }

    if (matchedValuesFilter.isPresent())
    {
      LinkedList<String> mvFilterStrings = matchedValuesFilter.getValues();
      ArrayList<MatchedValuesFilter> mvFilters =
           new ArrayList<MatchedValuesFilter>();
      for (String s : mvFilterStrings)
      {
        try
        {
          LDAPFilter f = LDAPFilter.decode(s);
          mvFilters.add(MatchedValuesFilter.createFromLDAPFilter(f));
        }
        catch (LDAPException le)
        {
          err.println(getMessage(MSGID_LDAP_MATCHEDVALUES_INVALID_FILTER,
                                 le.getMessage()));
          return 1;
        }
      }

      MatchedValuesControl mvc = new MatchedValuesControl(true, mvFilters);
      searchOptions.getControls().add(new LDAPControl(mvc));
    }

    // Set the connection options.
    connectionOptions.setSASLExternal(saslExternal.isPresent());
    if(saslOptions.isPresent())
    {
      LinkedList<String> values = saslOptions.getValues();
      for(String saslOption : values)
      {
        if(saslOption.startsWith("mech="))
        {
          boolean mechValue = connectionOptions.setSASLMechanism(saslOption);
          if(mechValue == false)
          {
            return 1;
          }
        } else
        {
          boolean propValue = connectionOptions.addSASLProperty(saslOption);
          if(propValue == false)
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

    // Read the filter strings.
    if(fileNameValue != null)
    {
      BufferedReader in = null;
      try
      {
        in = new BufferedReader(new FileReader(fileNameValue));
        String line = null;

        while ((line = in.readLine()) != null)
        {
          if(line.trim().equals(""))
          {
            // ignore empty lines.
            continue;
          }
          LDAPFilter ldapFilter = LDAPFilter.decode(line);
          filters.add(ldapFilter);
        }
      } catch(Exception e)
      {
        assert debugException(CLASS_NAME, "main", e);
        err.println(e.getMessage());
        return 1;
      }
      finally
      {
        if(in != null)
        {
          try
          {
           in.close();
          } catch (IOException ioe) {}
        }
      }

    }

    if(filters.isEmpty())
    {
      int msgid = MSGID_SEARCH_NO_FILTERS;
      err.println(getMessage(msgid));
      err.println(argParser.getUsage());
      return 1;
    }

    int wrapColumn = 80;
    if (dontWrap.isPresent())
    {
      wrapColumn = 0;
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
                                  keyStorePasswordValue, trustStorePathValue,
                                  trustStorePasswordValue);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID);

      LDAPSearch ldapSearch = new LDAPSearch(nextMessageID, out, err);
      ldapSearch.executeSearch(connection, baseDNValue, filters, attributes,
                               searchOptions, wrapColumn);

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

