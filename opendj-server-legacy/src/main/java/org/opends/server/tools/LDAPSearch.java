/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.CliMessages.ERR_TOOL_CONFLICTING_ARGS;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.cli.LDAPConnectionArgumentParser.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.controls.*;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.protocols.ldap.*;
import org.opends.server.types.*;
import org.opends.server.util.Base64;
import org.opends.server.util.EmbeddedUtils;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CliConstants;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This class provides a tool that can be used to issue search requests to the
 * Directory Server.
 */
public class LDAPSearch
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPSearch";



  /** The set of response controls for the search. */
  private List<Control> responseControls;

  /** The message ID counter to use for requests. */
  private final AtomicInteger nextMessageID;

  /** The print stream to use for standard error. */
  private final PrintStream err;
  /** The print stream to use for standard output. */
  private final PrintStream out;



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
    responseControls   = new ArrayList<>();
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
   * @return  The number of matching entries returned by the server.  If there
   *          were multiple search filters provided, then this will be the
   *          total number of matching entries for all searches.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public int executeSearch(LDAPConnection connection, String baseDN,
                           List<LDAPFilter> filters,
                           Set<String> attributes,
                           LDAPSearchOptions searchOptions,
                           int wrapColumn )
         throws IOException, LDAPException
  {
    int matchingEntries = 0;

    for (LDAPFilter filter: filters)
    {
      ByteString asn1OctetStr = ByteString.valueOfUtf8(baseDN);

      SearchRequestProtocolOp protocolOp =
        new SearchRequestProtocolOp(asn1OctetStr,
                                    searchOptions.getSearchScope(),
                                    searchOptions.getDereferencePolicy(),
                                    searchOptions.getSizeLimit(),
                                    searchOptions.getTimeLimit(),
                                    searchOptions.getTypesOnly(),
                                    filter, attributes);
      if(!searchOptions.showOperations())
      {
        try
        {
          boolean typesOnly = searchOptions.getTypesOnly();
          LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                protocolOp,
                                                searchOptions.getControls());
          connection.getLDAPWriter().writeMessage(message);

          byte opType;
          do
          {
            int resultCode = 0;
            LocalizableMessage errorMessage = null;
            DN matchedDN = null;
            LDAPMessage responseMessage =
                 connection.getLDAPReader().readMessage();
            responseControls = responseMessage.getControls();


            opType = responseMessage.getProtocolOpType();
            switch(opType)
            {
              case OP_TYPE_SEARCH_RESULT_ENTRY:
                for (Control c : responseControls)
                {
                  if (c.getOID().equals(OID_ENTRY_CHANGE_NOTIFICATION))
                  {
                    try
                    {
                      EntryChangeNotificationControl ecn =
                        EntryChangeNotificationControl.DECODER
                        .decode(c.isCritical(), ((LDAPControl) c).getValue());

                      out.println(INFO_LDAPSEARCH_PSEARCH_CHANGE_TYPE.get(ecn.getChangeType()));
                      DN previousDN = ecn.getPreviousDN();
                      if (previousDN != null)
                      {
                        out.println(INFO_LDAPSEARCH_PSEARCH_PREVIOUS_DN.get(previousDN));
                      }
                    } catch (Exception e) {}
                  }
                  else if (c.getOID().equals(OID_ACCOUNT_USABLE_CONTROL))
                  {
                    try
                    {
                      AccountUsableResponseControl acrc =
                        AccountUsableResponseControl.DECODER
                        .decode(c.isCritical(), ((LDAPControl) c).getValue());

                      out.println(INFO_LDAPSEARCH_ACCTUSABLE_HEADER.get());
                      if (acrc.isUsable())
                      {
                        out.println(INFO_LDAPSEARCH_ACCTUSABLE_IS_USABLE.get());
                        if (acrc.getSecondsBeforeExpiration() > 0)
                        {
                          int timeToExp = acrc.getSecondsBeforeExpiration();
                          LocalizableMessage timeToExpStr = secondsToTimeString(timeToExp);

                          out.println(
                               INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_EXPIRATION.
                                       get(timeToExpStr));
                        }
                      }
                      else
                      {
                        out.println(
                                INFO_LDAPSEARCH_ACCTUSABLE_NOT_USABLE.get());
                        if (acrc.isInactive())
                        {
                          out.println(
                               INFO_LDAPSEARCH_ACCTUSABLE_ACCT_INACTIVE.get());
                        }
                        if (acrc.isReset())
                        {
                          out.println(
                                  INFO_LDAPSEARCH_ACCTUSABLE_PW_RESET.get());
                        }
                        if (acrc.isExpired())
                        {
                          out.println(
                                  INFO_LDAPSEARCH_ACCTUSABLE_PW_EXPIRED.get());

                          if (acrc.getRemainingGraceLogins() > 0)
                          {
                            out.println(
                                    INFO_LDAPSEARCH_ACCTUSABLE_REMAINING_GRACE
                                         .get(acrc.getRemainingGraceLogins()));
                          }
                        }
                        if (acrc.isLocked())
                        {
                          out.println(INFO_LDAPSEARCH_ACCTUSABLE_LOCKED.get());
                          if (acrc.getSecondsBeforeUnlock() > 0)
                          {
                            int timeToUnlock = acrc.getSecondsBeforeUnlock();
                            LocalizableMessage timeToUnlockStr =
                                        secondsToTimeString(timeToUnlock);

                            out.println(
                                    INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_UNLOCK
                                            .get(timeToUnlockStr));
                          }
                        }
                      }
                    } catch (Exception e) {}
                  }
                  else if (c.getOID().equals(OID_ECL_COOKIE_EXCHANGE_CONTROL))
                  {
                    try
                    {
                      EntryChangelogNotificationControl ctrl =
                        EntryChangelogNotificationControl.DECODER.decode(
                          c.isCritical(), ((LDAPControl) c).getValue());
                      out.println(
                          INFO_LDAPSEARCH_PUBLIC_CHANGELOG_COOKIE_EXC.get(
                            c.getOID(), ctrl.getCookie()));
                    }
                    catch (Exception e)
                    {
                      logger.traceException(e);
                    }
                  }
                }

                SearchResultEntryProtocolOp searchEntryOp =
                     responseMessage.getSearchResultEntryProtocolOp();
                StringBuilder sb = new StringBuilder();
                toLDIF(searchEntryOp, sb, wrapColumn, typesOnly);
                out.print(sb.toString());
                matchingEntries++;
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
                matchedDN = searchOp.getMatchedDN();

                for (Control c : responseMessage.getControls())
                {
                  if (c.getOID().equals(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL))
                  {
                    try
                    {
                      ServerSideSortResponseControl sortResponse =
                        ServerSideSortResponseControl.DECODER
                        .decode(c.isCritical(), ((LDAPControl) c).getValue());
                      int rc = sortResponse.getResultCode();
                      if (rc != LDAPResultCode.SUCCESS)
                      {
                        LocalizableMessage msg   = WARN_LDAPSEARCH_SORT_ERROR.get(
                                LDAPResultCode.toString(rc));
                        err.println(msg);
                      }
                    }
                    catch (Exception e)
                    {
                      LocalizableMessage msg   =
                              WARN_LDAPSEARCH_CANNOT_DECODE_SORT_RESPONSE.get(
                                      getExceptionMessage(e));
                      err.println(msg);
                    }
                  }
                  else if (c.getOID().equals(OID_VLV_RESPONSE_CONTROL))
                  {
                    try
                    {
                      VLVResponseControl vlvResponse =
                          VLVResponseControl.DECODER.decode(c.isCritical(),
                              ((LDAPControl) c).getValue());
                      int rc = vlvResponse.getVLVResultCode();
                      if (rc == LDAPResultCode.SUCCESS)
                      {
                        LocalizableMessage msg = INFO_LDAPSEARCH_VLV_TARGET_OFFSET.get(
                                vlvResponse.getTargetPosition());
                        out.println(msg);


                        msg = INFO_LDAPSEARCH_VLV_CONTENT_COUNT.get(
                                vlvResponse.getContentCount());
                        out.println(msg);
                      }
                      else
                      {
                        LocalizableMessage msg = WARN_LDAPSEARCH_VLV_ERROR.get(
                                LDAPResultCode.toString(rc));
                        err.println(msg);
                      }
                    }
                    catch (Exception e)
                    {
                      LocalizableMessage msg   =
                              WARN_LDAPSEARCH_CANNOT_DECODE_VLV_RESPONSE.get(
                                      getExceptionMessage(e));
                      err.println(msg);
                    }
                  }
                }

                break;
              default:
                if(opType == OP_TYPE_EXTENDED_RESPONSE)
                {
                  ExtendedResponseProtocolOp op =
                    responseMessage.getExtendedResponseProtocolOp();
                  if(op.getOID().equals(OID_NOTICE_OF_DISCONNECTION))
                  {
                    resultCode = op.getResultCode();
                    errorMessage = op.getErrorMessage();
                    matchedDN = op.getMatchedDN();
                    break;
                  }
                }
                // FIXME - throw exception?
              printWrappedText(err, INFO_SEARCH_OPERATION_INVALID_PROTOCOL.get(opType));
            }

            if(resultCode != SUCCESS)
            {
              LocalizableMessage msg = INFO_OPERATION_FAILED.get("SEARCH");
              throw new LDAPException(resultCode, errorMessage, msg,
                                      matchedDN, null);
            }
            else if (errorMessage != null)
            {
              out.println();
              printWrappedText(out, errorMessage);
            }

          } while(opType != OP_TYPE_SEARCH_RESULT_DONE);

        } catch(DecodeException ae)
        {
          logger.traceException(ae);
          throw new IOException(ae.getMessage());
        }
      }
    }

    if (searchOptions.countMatchingEntries())
    {
      LocalizableMessage message =
              INFO_LDAPSEARCH_MATCHING_ENTRY_COUNT.get(matchingEntries);
      out.println(message);
      out.println();
    }
    return matchingEntries;
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
    if (dnLength <= colsRemaining || colsRemaining <= 0)
    {
      buffer.append(dnString);
      buffer.append(EOL);
    }
    else
    {
      buffer.append(dnString, 0, colsRemaining);
      buffer.append(EOL);

      int startPos = colsRemaining;
      while (dnLength - startPos > wrapColumn - 1)
      {
        buffer.append(" ");
        buffer.append(dnString, startPos, startPos+wrapColumn-1);
        buffer.append(EOL);

        startPos += wrapColumn-1;
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
        for (ByteString v : a.getValues())
        {
          String valueString;
          if (needsBase64Encoding(v))
          {
            valueString = Base64.encode(v);
            buffer.append(name);
            buffer.append(":: ");

            colsRemaining = wrapColumn - nameLength - 3;
          } else
          {
            valueString = v.toString();
            buffer.append(name);
            buffer.append(": ");

            colsRemaining = wrapColumn - nameLength - 2;
          }

          int valueLength = valueString.length();
          if (valueLength <= colsRemaining || colsRemaining <= 0)
          {
            buffer.append(valueString);
            buffer.append(EOL);
          } else
          {
            buffer.append(valueString, 0, colsRemaining);
            buffer.append(EOL);

            int startPos = colsRemaining;
            while (valueLength - startPos > wrapColumn - 1)
            {
              buffer.append(" ");
              buffer.append(valueString, startPos, startPos+wrapColumn-1);
              buffer.append(EOL);

              startPos += wrapColumn-1;
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
   * Retrieves the set of response controls included in the last search result
   * done message.
   *
   * @return  The set of response controls included in the last search result
   *          done message.
   */
  public List<Control> getResponseControls()
  {
    return responseControls;
  }

  /**
   * The main method for LDAPSearch tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainSearch(args, true, false, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
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
    return mainSearch(args, true, true, System.out, System.err);
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
    return mainSearch(args, initializeServer, true, outStream, errStream);
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapsearch tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  returnMatchingEntries whether when the option --countEntries is
   *                           specified, the number of matching entries should
   *                           be returned or not.
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
      boolean returnMatchingEntries, OutputStream outStream,
      OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);

    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    LDAPSearchOptions searchOptions = new LDAPSearchOptions();
    LDAPConnection connection = null;
    final List<LDAPFilter> filters = new ArrayList<>();
    final Set<String> attributes = new LinkedHashSet<>();

    final BooleanArgument continueOnError;
    final BooleanArgument countEntries;
    final BooleanArgument dontWrap;
    final BooleanArgument noop;
    final BooleanArgument reportAuthzID;
    final BooleanArgument saslExternal;
    final BooleanArgument showUsage;
    final BooleanArgument trustAll;
    final BooleanArgument usePasswordPolicyControl;
    final BooleanArgument useSSL;
    final BooleanArgument startTLS;
    final BooleanArgument typesOnly;
    final BooleanArgument verbose;
    final FileBasedArgument bindPasswordFile;
    final FileBasedArgument keyStorePasswordFile;
    final FileBasedArgument trustStorePasswordFile;
    final IntegerArgument port;
    final IntegerArgument simplePageSize;
    final IntegerArgument sizeLimit;
    final IntegerArgument timeLimit;
    final IntegerArgument version;
    final StringArgument assertionFilter;
    final StringArgument baseDN;
    final StringArgument bindDN;
    final StringArgument bindPassword;
    final StringArgument certNickname;
    final StringArgument controlStr;
    final StringArgument dereferencePolicy;
    final StringArgument encodingStr;
    final StringArgument filename;
    final StringArgument hostName;
    final StringArgument keyStorePath;
    final StringArgument keyStorePassword;
    final StringArgument matchedValuesFilter;
    final StringArgument proxyAuthzID;
    final StringArgument pSearchInfo;
    final StringArgument saslOptions;
    final MultiChoiceArgument searchScope;
    final StringArgument sortOrder;
    final StringArgument trustStorePath;
    final StringArgument trustStorePassword;
    final IntegerArgument connectTimeout;
    final StringArgument vlvDescriptor;
    final StringArgument effectiveRightsUser;
    final StringArgument effectiveRightsAttrs;
    final StringArgument propertiesFileArgument;
    final BooleanArgument noPropertiesFileArgument;
    final BooleanArgument subEntriesArgument ;

    // Create the command-line argument parser for use with this program.
    LocalizableMessage toolDescription = INFO_LDAPSEARCH_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false, true, 0, 0,
                                                  "[filter] [attributes ...]");
    argParser.setShortToolDescription(REF_SHORT_DESC_LDAPSEARCH.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    try
    {
      propertiesFileArgument =
              StringArgument.builder(OPTION_LONG_PROP_FILE_PATH)
                      .description(INFO_DESCRIPTION_PROP_FILE_PATH.get())
                      .valuePlaceholder(INFO_PROP_FILE_PATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument =
              BooleanArgument.builder(OPTION_LONG_NO_PROP_FILE)
                      .description(INFO_DESCRIPTION_NO_PROP_FILE.get())
                      .buildAndAddToParser(argParser);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      hostName =
              StringArgument.builder(OPTION_LONG_HOST)
                      .shortIdentifier(OPTION_SHORT_HOST)
                      .description(INFO_DESCRIPTION_HOST.get())
                      .defaultValue("localhost")
                      .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      port =
              IntegerArgument.builder(OPTION_LONG_PORT)
                      .shortIdentifier(OPTION_SHORT_PORT)
                      .description(INFO_DESCRIPTION_PORT.get())
                      .range(1, 65535)
                      .defaultValue(389)
                      .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      useSSL =
              BooleanArgument.builder(OPTION_LONG_USE_SSL)
                      .shortIdentifier(OPTION_SHORT_USE_SSL)
                      .description(INFO_DESCRIPTION_USE_SSL.get())
                      .buildAndAddToParser(argParser);
      startTLS =
              BooleanArgument.builder(OPTION_LONG_START_TLS)
                      .shortIdentifier(OPTION_SHORT_START_TLS)
                      .description(INFO_DESCRIPTION_START_TLS.get())
                      .buildAndAddToParser(argParser);
      bindDN =
              StringArgument.builder(OPTION_LONG_BINDDN)
                      .shortIdentifier(OPTION_SHORT_BINDDN)
                      .description(INFO_DESCRIPTION_BINDDN.get())
                      .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPassword =
              StringArgument.builder(OPTION_LONG_BINDPWD)
                      .shortIdentifier(OPTION_SHORT_BINDPWD)
                      .description(INFO_DESCRIPTION_BINDPASSWORD.get())
                      .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      bindPasswordFile =
              FileBasedArgument.builder(OPTION_LONG_BINDPWD_FILE)
                      .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                      .description(INFO_DESCRIPTION_BINDPASSWORDFILE.get())
                      .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      baseDN =
              StringArgument.builder(OPTION_LONG_BASEDN)
                      .shortIdentifier(OPTION_SHORT_BASEDN)
                      .description(INFO_SEARCH_DESCRIPTION_BASEDN.get())
                      .required()
                      .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      searchScope =
              MultiChoiceArgument.<String>builder("searchScope")
                      .shortIdentifier('s')
                      .description(INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE.get())
                      .allowedValues("base", "one", "sub", "subordinate")
                      .defaultValue("sub")
                      .valuePlaceholder(INFO_SEARCH_SCOPE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      filename =
              StringArgument.builder(OPTION_LONG_FILENAME)
                      .shortIdentifier(OPTION_SHORT_FILENAME)
                      .description(INFO_SEARCH_DESCRIPTION_FILENAME.get())
                      .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      saslExternal =
              BooleanArgument.builder("useSASLExternal")
                      .shortIdentifier('r')
                      .description(INFO_DESCRIPTION_USE_SASL_EXTERNAL.get())
                      .buildAndAddToParser(argParser);
      saslOptions =
              StringArgument.builder(OPTION_LONG_SASLOPTION)
                      .shortIdentifier(OPTION_SHORT_SASLOPTION)
                      .description(INFO_DESCRIPTION_SASL_PROPERTIES.get())
                      .multiValued()
                      .valuePlaceholder(INFO_SASL_OPTION_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      trustAll = trustAllArgument();
      argParser.addArgument(trustAll);

      keyStorePath =
              StringArgument.builder(OPTION_LONG_KEYSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_KEYSTOREPATH)
                      .description(INFO_DESCRIPTION_KEYSTOREPATH.get())
                      .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyStorePassword =
              StringArgument.builder(OPTION_LONG_KEYSTORE_PWD)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD)
                      .description(INFO_DESCRIPTION_KEYSTOREPASSWORD.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      keyStorePasswordFile =
              FileBasedArgument.builder(OPTION_LONG_KEYSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD_FILE)
                      .description(INFO_DESCRIPTION_KEYSTOREPASSWORD_FILE.get())
                      .valuePlaceholder(INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      certNickname =
              StringArgument.builder(OPTION_LONG_CERT_NICKNAME)
                      .shortIdentifier(OPTION_SHORT_CERT_NICKNAME)
                      .description(INFO_DESCRIPTION_CERT_NICKNAME.get())
                      .valuePlaceholder(INFO_NICKNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePath =
              StringArgument.builder(OPTION_LONG_TRUSTSTOREPATH)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTOREPATH)
                      .description(INFO_DESCRIPTION_TRUSTSTOREPATH.get())
                      .valuePlaceholder(INFO_TRUSTSTOREPATH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePassword =
              StringArgument.builder(OPTION_LONG_TRUSTSTORE_PWD)
                      .description(INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      trustStorePasswordFile =
              FileBasedArgument.builder(OPTION_LONG_TRUSTSTORE_PWD_FILE)
                      .shortIdentifier(OPTION_SHORT_TRUSTSTORE_PWD_FILE)
                      .description(INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get())
                      .valuePlaceholder(INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      proxyAuthzID =
              StringArgument.builder(OPTION_LONG_PROXYAUTHID)
                      .shortIdentifier(OPTION_SHORT_PROXYAUTHID)
                      .description(INFO_DESCRIPTION_PROXY_AUTHZID.get())
                      .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      reportAuthzID =
              BooleanArgument.builder(OPTION_LONG_REPORT_AUTHZ_ID)
                      .shortIdentifier('E')
                      .description(INFO_DESCRIPTION_REPORT_AUTHZID.get())
                      .buildAndAddToParser(argParser);
      usePasswordPolicyControl =
              BooleanArgument.builder(OPTION_LONG_USE_PW_POLICY_CTL)
                      .description(INFO_DESCRIPTION_USE_PWP_CONTROL.get())
                      .buildAndAddToParser(argParser);
      pSearchInfo =
              StringArgument.builder("persistentSearch")
                      .shortIdentifier('C')
                      .description(INFO_DESCRIPTION_PSEARCH_INFO.get())
                      .docDescriptionSupplement(SUPPLEMENT_DESCRIPTION_PSEARCH_INFO.get())
                      .valuePlaceholder(INFO_PSEARCH_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      simplePageSize =
              IntegerArgument.builder("simplePageSize")
                      .description(INFO_DESCRIPTION_SIMPLE_PAGE_SIZE.get())
                      .lowerBound(1)
                      .defaultValue(1000)
                      .valuePlaceholder(INFO_NUM_ENTRIES_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      assertionFilter =
              StringArgument.builder(OPTION_LONG_ASSERTION_FILE)
                      .description(INFO_DESCRIPTION_ASSERTION_FILTER.get())
                      .valuePlaceholder(INFO_ASSERTION_FILTER_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      matchedValuesFilter =
              StringArgument.builder("matchedValuesFilter")
                      .description(INFO_DESCRIPTION_MATCHED_VALUES_FILTER.get())
                      .multiValued()
                      .valuePlaceholder(INFO_FILTER_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      sortOrder =
              StringArgument.builder("sortOrder")
                      .shortIdentifier('S')
                      .description(INFO_DESCRIPTION_SORT_ORDER.get())
                      .valuePlaceholder(INFO_SORT_ORDER_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      vlvDescriptor =
              StringArgument.builder("virtualListView")
                      .shortIdentifier('G')
                      .description(INFO_DESCRIPTION_VLV.get())
                      .valuePlaceholder(INFO_VLV_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      controlStr =
              StringArgument.builder("control")
                      .shortIdentifier('J')
                      .description(INFO_DESCRIPTION_CONTROLS.get())
                      .docDescriptionSupplement(SUPPLEMENT_DESCRIPTION_CONTROLS.get())
                      .multiValued()
                      .valuePlaceholder(INFO_LDAP_CONTROL_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      subEntriesArgument =
              BooleanArgument.builder(OPTION_LONG_SUBENTRIES)
                      .shortIdentifier(OPTION_SHORT_SUBENTRIES)
                      .description(INFO_DESCRIPTION_SUBENTRIES.get())
                      .buildAndAddToParser(argParser);
      effectiveRightsUser =
              StringArgument.builder(OPTION_LONG_EFFECTIVERIGHTSUSER)
                      .shortIdentifier(OPTION_SHORT_EFFECTIVERIGHTSUSER)
                      .description(INFO_DESCRIPTION_EFFECTIVERIGHTS_USER.get())
                      .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      effectiveRightsAttrs =
              StringArgument.builder(OPTION_LONG_EFFECTIVERIGHTSATTR)
                      .shortIdentifier(OPTION_SHORT_EFFECTIVERIGHTSATTR)
                      .description(INFO_DESCRIPTION_EFFECTIVERIGHTS_ATTR.get())
                      .multiValued()
                      .valuePlaceholder(INFO_ATTRIBUTE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      version =
              IntegerArgument.builder(OPTION_LONG_PROTOCOL_VERSION)
                      .shortIdentifier(OPTION_SHORT_PROTOCOL_VERSION)
                      .description(INFO_DESCRIPTION_VERSION.get())
                      .defaultValue(3)
                      .valuePlaceholder(INFO_PROTOCOL_VERSION_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      connectTimeout =
              IntegerArgument.builder(OPTION_LONG_CONNECT_TIMEOUT)
                      .description(INFO_DESCRIPTION_CONNECTION_TIMEOUT.get())
                      .lowerBound(0)
                      .defaultValue(CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT)
                      .valuePlaceholder(INFO_TIMEOUT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      encodingStr =
              StringArgument.builder("encoding")
                      .shortIdentifier('i')
                      .description(INFO_DESCRIPTION_ENCODING.get())
                      .valuePlaceholder(INFO_ENCODING_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      dereferencePolicy =
              StringArgument.builder("dereferencePolicy")
                      .shortIdentifier('a')
                      .description(INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY.get())
                      .defaultValue("never")
                      .valuePlaceholder(INFO_DEREFERENCE_POLICE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      typesOnly =
              BooleanArgument.builder("typesOnly")
                      .shortIdentifier('A')
                      .description(INFO_DESCRIPTION_TYPES_ONLY.get())
                      .buildAndAddToParser(argParser);
      sizeLimit =
              IntegerArgument.builder("sizeLimit")
                      .shortIdentifier('z')
                      .description(INFO_SEARCH_DESCRIPTION_SIZE_LIMIT.get())
                      .defaultValue(0)
                      .valuePlaceholder(INFO_SIZE_LIMIT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      timeLimit =
              IntegerArgument.builder("timeLimit")
                      .shortIdentifier('l')
                      .description(INFO_SEARCH_DESCRIPTION_TIME_LIMIT.get())
                      .defaultValue(0)
                      .valuePlaceholder(INFO_TIME_LIMIT_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      dontWrap =
              BooleanArgument.builder("dontWrap")
                      .shortIdentifier('T')
                      .description(INFO_DESCRIPTION_DONT_WRAP.get())
                      .buildAndAddToParser(argParser);
      countEntries =
              BooleanArgument.builder("countEntries")
                      .description(INFO_DESCRIPTION_COUNT_ENTRIES.get())
                      .buildAndAddToParser(argParser);
      continueOnError =
              BooleanArgument.builder("continueOnError")
                      .shortIdentifier('c')
                      .description(INFO_DESCRIPTION_CONTINUE_ON_ERROR.get())
                      .buildAndAddToParser(argParser);
      noop =
              BooleanArgument.builder(OPTION_LONG_DRYRUN)
                      .shortIdentifier(OPTION_SHORT_DRYRUN)
                      .description(INFO_DESCRIPTION_NOOP.get())
                      .buildAndAddToParser(argParser);

      verbose = verboseArgument();
      argParser.addArgument(verbose);

      showUsage = showUsageArgument();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    } catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    final List<String> filterAndAttributeStrings = argParser.getTrailingArguments();
    if(!filterAndAttributeStrings.isEmpty())
    {
      // the list of trailing arguments should be structured as follow:
      // - If a filter file is present, trailing arguments are considered
      //   as attributes
      // - If filter file is not present, the first trailing argument is
      // considered the filter, the other as attributes.
      if (! filename.isPresent())
      {
        String filterString = filterAndAttributeStrings.remove(0);

        try
        {
          filters.add(LDAPFilter.decode(filterString));
        } catch (LDAPException le)
        {
          logger.traceException(le);
          printWrappedText(err, le.getMessage());
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
      attributes.addAll(filterAndAttributeStrings);
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      printWrappedText(err,
          ERR_TOOL_CONFLICTING_ARGS.get(bindPassword.getLongIdentifier(), bindPasswordFile.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (useSSL.isPresent() && startTLS.isPresent())
    {
      printWrappedText(err, ERR_TOOL_CONFLICTING_ARGS.get(useSSL.getLongIdentifier(), startTLS.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (keyStorePassword.isPresent() && keyStorePasswordFile.isPresent())
    {
      printWrappedText(err, ERR_TOOL_CONFLICTING_ARGS.get(
          keyStorePassword.getLongIdentifier(), keyStorePasswordFile.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if (trustStorePassword.isPresent() && trustStorePasswordFile.isPresent())
    {
      printWrappedText(err, ERR_TOOL_CONFLICTING_ARGS.get(
          trustStorePassword.getLongIdentifier(), trustStorePasswordFile.getLongIdentifier()));
      return CLIENT_SIDE_PARAM_ERROR;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      logger.traceException(ae);
      argParser.displayMessageAndUsageReference(err, ae.getMessageObject());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    // Read the LDAP version number.
    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {
        printWrappedText(err, ERR_DESCRIPTION_INVALID_VERSION.get(versionNumber));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      logger.traceException(ae);
      argParser.displayMessageAndUsageReference(err, ae.getMessageObject());
      return CLIENT_SIDE_PARAM_ERROR;
    }


    // Indicate whether we should report the authorization ID and/or use the
    // password policy control.
    connectionOptions.setReportAuthzID(reportAuthzID.isPresent());
    connectionOptions.setUsePasswordPolicyControl(
         usePasswordPolicyControl.isPresent());


    String baseDNValue = baseDN.getValue();
    String bindDNValue = bindDN.getValue();
    String fileNameValue = filename.getValue();
    String bindPasswordValue;
    try
    {
      bindPasswordValue = getPasswordValue(
          bindPassword, bindPasswordFile, bindDNValue, out, err);
    }
    catch (Exception ex)
    {
      logger.traceException(ex);
      printWrappedText(err, ex.getMessage());
      return CLIENT_SIDE_PARAM_ERROR;
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

    searchOptions.setTypesOnly(typesOnly.isPresent());
    searchOptions.setShowOperations(noop.isPresent());
    searchOptions.setVerbose(verbose.isPresent());
    searchOptions.setContinueOnError(continueOnError.isPresent());
    searchOptions.setEncoding(encodingStr.getValue());
    searchOptions.setCountMatchingEntries(countEntries.isPresent());
    try
    {
      searchOptions.setTimeLimit(timeLimit.getIntValue());
      searchOptions.setSizeLimit(sizeLimit.getIntValue());
    } catch(ArgumentException ex1)
    {
      argParser.displayMessageAndUsageReference(err, ex1.getMessageObject());
      return CLIENT_SIDE_PARAM_ERROR;
    }
    if (!searchOptions.setSearchScope(searchScope.getValue(), err)
        || !searchOptions.setDereferencePolicy(dereferencePolicy.getValue(), err))
    {
      return CLIENT_SIDE_PARAM_ERROR;
    }

    if(controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        Control ctrl = LDAPToolUtils.getControl(ctrlString, err);
        if(ctrl == null)
        {
          printWrappedText(err, ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
          return CLIENT_SIDE_PARAM_ERROR;
        }
        searchOptions.getControls().add(ctrl);
      }
    }

    if(effectiveRightsUser.isPresent()) {
      String authzID=effectiveRightsUser.getValue();
      if (!authzID.startsWith("dn:")) {
        printWrappedText(err, ERR_EFFECTIVERIGHTS_INVALID_AUTHZID.get(authzID));
        return CLIENT_SIDE_PARAM_ERROR;
      }
      Control effectiveRightsControl =
          new GetEffectiveRightsRequestControl(false, authzID.substring(3),
              effectiveRightsAttrs.getValues());
      searchOptions.getControls().add(effectiveRightsControl);
    }

    if (proxyAuthzID.isPresent())
    {
      Control proxyControl =
          new ProxiedAuthV2Control(true,
              ByteString.valueOfUtf8(proxyAuthzID.getValue()));
      searchOptions.getControls().add(proxyControl);
    }

    if (pSearchInfo.isPresent())
    {
      String infoString = toLowerCase(pSearchInfo.getValue().trim());
      HashSet<PersistentSearchChangeType> changeTypes = new HashSet<>();
      boolean changesOnly = true;
      boolean returnECs = true;

      StringTokenizer tokenizer = new StringTokenizer(infoString, ":");

      if (! tokenizer.hasMoreTokens())
      {
        printWrappedText(err, ERR_PSEARCH_MISSING_DESCRIPTOR.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }
      else
      {
        String token = tokenizer.nextToken();
        if (! token.equals("ps"))
        {
          printWrappedText(err, ERR_PSEARCH_DOESNT_START_WITH_PS.get(infoString));
          return CLIENT_SIDE_PARAM_ERROR;
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
            printWrappedText(err, ERR_PSEARCH_INVALID_CHANGE_TYPE.get(token));
            return CLIENT_SIDE_PARAM_ERROR;
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
          printWrappedText(err, ERR_PSEARCH_INVALID_CHANGESONLY.get(token));
          return CLIENT_SIDE_PARAM_ERROR;
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
          printWrappedText(err, ERR_PSEARCH_INVALID_RETURN_ECS.get(token));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }

      PersistentSearchControl psearchControl =
           new PersistentSearchControl(changeTypes, changesOnly, returnECs);
      searchOptions.getControls().add(psearchControl);
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
        Control assertionControl =
            new LDAPAssertionRequestControl(true, filter);
        searchOptions.getControls().add(assertionControl);
      }
      catch (LDAPException le)
      {
        printWrappedText(err, ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage()));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (matchedValuesFilter.isPresent())
    {
      List<String> mvFilterStrings = matchedValuesFilter.getValues();
      List<MatchedValuesFilter> mvFilters = new ArrayList<>();
      for (String s : mvFilterStrings)
      {
        try
        {
          LDAPFilter f = LDAPFilter.decode(s);
          mvFilters.add(MatchedValuesFilter.createFromLDAPFilter(f));
        }
        catch (LDAPException le)
        {
          printWrappedText(err, ERR_LDAP_MATCHEDVALUES_INVALID_FILTER.get(le.getMessage()));
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }

      MatchedValuesControl mvc = new MatchedValuesControl(true, mvFilters);
      searchOptions.getControls().add(mvc);
    }

    if (sortOrder.isPresent())
    {
      try
      {
        searchOptions.getControls().add(
            new ServerSideSortRequestControl(sortOrder.getValue()));
      }
      catch (LDAPException le)
      {
        printWrappedText(err, ERR_LDAP_SORTCONTROL_INVALID_ORDER.get(le.getErrorMessage()));
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (vlvDescriptor.isPresent())
    {
      if (! sortOrder.isPresent())
      {
        printWrappedText(err,
            ERR_LDAPSEARCH_VLV_REQUIRES_SORT.get(vlvDescriptor.getLongIdentifier(), sortOrder.getLongIdentifier()));
        return CLIENT_SIDE_PARAM_ERROR;
      }

      StringTokenizer tokenizer =
           new StringTokenizer(vlvDescriptor.getValue(), ":");
      int numTokens = tokenizer.countTokens();
      if (numTokens == 3)
      {
        try
        {
          int beforeCount = Integer.parseInt(tokenizer.nextToken());
          int afterCount  = Integer.parseInt(tokenizer.nextToken());
          ByteString assertionValue = ByteString.valueOfUtf8(tokenizer.nextToken());
          searchOptions.getControls().add(
              new VLVRequestControl(beforeCount, afterCount, assertionValue));
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
      else if (numTokens == 4)
      {
        try
        {
          int beforeCount  = Integer.parseInt(tokenizer.nextToken());
          int afterCount   = Integer.parseInt(tokenizer.nextToken());
          int offset       = Integer.parseInt(tokenizer.nextToken());
          int contentCount = Integer.parseInt(tokenizer.nextToken());
          searchOptions.getControls().add(
              new VLVRequestControl(beforeCount, afterCount, offset,
                  contentCount));
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
          return CLIENT_SIDE_PARAM_ERROR;
        }
      }
      else
      {
        printWrappedText(err, ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    if (subEntriesArgument.isPresent())
    {
      Control subentriesControl =
          new SubentriesControl(true, true);
      searchOptions.getControls().add(subentriesControl);
    }

    // Set the connection options.
    connectionOptions.setSASLExternal(saslExternal.isPresent());
    if(saslOptions.isPresent())
    {
      List<String> values = saslOptions.getValues();
      for(String saslOption : values)
      {
        if(saslOption.startsWith("mech="))
        {
          if (!connectionOptions.setSASLMechanism(saslOption))
          {
            return CLIENT_SIDE_PARAM_ERROR;
          }
        } else
        {
          if (!connectionOptions.addSASLProperty(saslOption))
          {
            return CLIENT_SIDE_PARAM_ERROR;
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
        printWrappedText(err, ERR_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }
      if(keyStorePathValue == null)
      {
        printWrappedText(err, ERR_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE.get());
        return CLIENT_SIDE_PARAM_ERROR;
      }
    }

    connectionOptions.setVerbose(verbose.isPresent());

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
        logger.traceException(e);
        printWrappedText(err, e.getMessage());
        return CLIENT_SIDE_PARAM_ERROR;
      }
      finally
      {
        close(in);
      }
    }

    if(filters.isEmpty())
    {
      argParser.displayMessageAndUsageReference(err, ERR_SEARCH_NO_FILTERS.get());
      return CLIENT_SIDE_PARAM_ERROR;
    }

    int wrapColumn = 80;
    if (dontWrap.isPresent())
    {
      wrapColumn = 0;
    }

    LDAPSearch ldapSearch = null;
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

      if (noop.isPresent())
      {
        // We don't actually need to open a connection or perform the search,
        // so we're done.  We should return 0 to either mean that the processing
        // was successful or that there were no matching entries, based on
        // countEntries.isPresent() (but in either case the return value should
        // be zero).
        return 0;
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      int timeout = pSearchInfo.isPresent()?0:connectTimeout.getIntValue();
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID,
          timeout);


      int matchingEntries = 0;
      if (simplePageSize.isPresent())
      {
        if (filters.size() > 1)
        {
          LocalizableMessage message = ERR_PAGED_RESULTS_REQUIRES_SINGLE_FILTER.get();
          throw new LDAPException(CLIENT_SIDE_PARAM_ERROR, message);
        }

        int pageSize = simplePageSize.getIntValue();
        ByteString cookieValue = null;
        ArrayList<Control> origControls = searchOptions.getControls();

        while (true)
        {
          ArrayList<Control> newControls = new ArrayList<>(origControls.size() + 1);
          newControls.addAll(origControls);
          newControls.add(new PagedResultsControl(true, pageSize, cookieValue));
          searchOptions.setControls(newControls);

          ldapSearch = new LDAPSearch(nextMessageID, out, err);
          matchingEntries += ldapSearch.executeSearch(connection, baseDNValue,
                                                      filters, attributes,
                                                      searchOptions,
                                                      wrapColumn);

          List<Control> responseControls =
               ldapSearch.getResponseControls();
          boolean responseFound = false;
          for (Control c : responseControls)
          {
            if (c.getOID().equals(OID_PAGED_RESULTS_CONTROL))
            {
              try
              {
                PagedResultsControl control = PagedResultsControl.DECODER
                    .decode(c.isCritical(), ((LDAPControl) c).getValue());
                responseFound = true;
                cookieValue = control.getCookie();
                break;
              }
              catch (DirectoryException de)
              {
                LocalizableMessage message =
                    ERR_PAGED_RESULTS_CANNOT_DECODE.get(de.getMessage());
                throw new LDAPException(
                        CLIENT_SIDE_DECODING_ERROR, message, de);
              }
            }
          }

          if (! responseFound)
          {
            LocalizableMessage message = ERR_PAGED_RESULTS_RESPONSE_NOT_FOUND.get();
            throw new LDAPException(CLIENT_SIDE_CONTROL_NOT_FOUND, message);
          }
          else if (cookieValue.length() == 0)
          {
            break;
          }
        }
      }
      else
      {
        ldapSearch = new LDAPSearch(nextMessageID, out, err);
        matchingEntries = ldapSearch.executeSearch(connection, baseDNValue,
                                                   filters, attributes,
                                                   searchOptions, wrapColumn);
      }

      if (countEntries.isPresent() && returnMatchingEntries)
      {
        return matchingEntries;
      }
      else
      {
        return 0;
      }

    } catch(LDAPException le)
    {
      int code = le.getResultCode();
      if (code == REFERRAL)
      {
        out.println();
        printWrappedText(out, le.getErrorMessage());
      }
      else
      {
      logger.traceException(le);

        LDAPToolUtils.printErrorMessage(err, le.getMessageObject(), code,
            le.getErrorMessage(), le.getMatchedDN());
      }
      return code;
    } catch(LDAPConnectionException lce)
    {
      logger.traceException(lce);
      LDAPToolUtils.printErrorMessage(err,
                                      lce.getMessageObject(),
                                      lce.getResultCode(),
                                      lce.getErrorMessage(),
                                      lce.getMatchedDN());
      return lce.getResultCode();
    } catch(Exception e)
    {
      logger.traceException(e);
      printWrappedText(err, e.getMessage());
      return 1;
    } finally
    {
      if(connection != null)
      {
        if (ldapSearch == null)
        {
          connection.close(null);
        }
        else
        {
          connection.close(ldapSearch.nextMessageID);
        }
      }
    }
  }

}

