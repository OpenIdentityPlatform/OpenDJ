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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.tools;

import com.sun.opends.sdk.util.RecursiveFutureResult;
import org.opends.sdk.*;
import org.opends.sdk.requests.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.SearchResultEntry;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.tools.ToolConstants.*;
import static com.sun.opends.sdk.tools.Utils.filterExitCode;

/**
 * A load generation tool that can be used to load a Directory Server with
 * Bind requests using one or more LDAP connections.
 */
public final class AuthRate extends ConsoleApplication
{
  private final class BindPerformanceRunner extends PerformanceRunner
  {
    private final AtomicLong searchWaitRecentTime = new AtomicLong();
    private final AtomicInteger invalidCredRecentCount = new AtomicInteger();

    private final class BindStatsThread extends StatsThread
    {
      private final String[] extraColumn;



      private BindStatsThread(boolean extraFieldRequired)
      {
        super(extraFieldRequired ? new String[] { "bind time %" }
            : new String[0]);
        extraColumn = new String[extraFieldRequired ? 1 : 0];
      }



      @Override
      String[] getAdditionalColumns()
      {
        invalidCredRecentCount.set(0);
        if (extraColumn.length != 0)
        {
          final long searchWaitTime = searchWaitRecentTime.getAndSet(0);
          extraColumn[0] = String.format("%.1f",
              ((float) (waitTime - searchWaitTime) / waitTime) * 100.0);
        }
        return extraColumn;
      }
    }

    private final class BindUpdateStatsResultHandler
        extends UpdateStatsResultHandler<BindResult>
    {
      private BindUpdateStatsResultHandler(long eTime) {
        super(eTime);
      }

      @Override
      public void handleErrorResult(ErrorResultException error) {
        if(error.getResult().getResultCode() != ResultCode.INVALID_CREDENTIALS)
        {
          super.handleErrorResult(error);
        }
        else
        {
          failedRecentCount.getAndIncrement();
          invalidCredRecentCount.getAndIncrement();
        }
      }
    }

    private final class BindWorkerThread extends WorkerThread
    {
      private SearchRequest sr;
      private BindRequest br;

      private Object[] data;



      private BindWorkerThread(final AsynchronousConnection connection,
          final ConnectionFactory connectionFactory)
      {
        super(connection, connectionFactory);
      }



      @Override
      public FutureResult<?> performOperation(
          final AsynchronousConnection connection,
          final DataSource[] dataSources, final long startTime)
      {
        if (dataSources != null)
        {
          data = DataSource.generateData(dataSources, data);
          if(data.length == dataSources.length)
          {
            Object[] newData = new Object[data.length + 1];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
          }
        }
        if(filter != null && baseDN != null)
        {
          if (sr == null)
          {
            if (dataSources == null)
            {
              sr = Requests.newSearchRequest(baseDN, scope, filter, attributes);
            }
            else
            {
              sr = Requests.newSearchRequest(String.format(baseDN, data), scope,
                  String.format(filter, data), attributes);
            }
            sr.setDereferenceAliasesPolicy(dereferencesAliasesPolicy);
          }
          else if (dataSources != null)
          {
            sr.setFilter(String.format(filter, data));
            sr.setName(String.format(baseDN, data));
          }

          RecursiveFutureResult<SearchResultEntry, BindResult> future =
              new RecursiveFutureResult<SearchResultEntry, BindResult>(
                  new BindUpdateStatsResultHandler(startTime))
              {
                @Override
                protected FutureResult<? extends BindResult> chainResult(
                    SearchResultEntry innerResult,
                    ResultHandler<? super BindResult> resultHandler)
                    throws ErrorResultException
                {
                  searchWaitRecentTime.getAndAdd(System.nanoTime() - startTime);
                  if(data == null)
                  {
                    data = new Object[1];
                  }
                  data[data.length-1] = innerResult.getName().toString();
                  return performBind(connection, data, resultHandler);
                }
              };
          connection.searchSingleEntry(sr, future);
          return future;
        }
        else
        {
          return performBind(connection, data,
              new BindUpdateStatsResultHandler(startTime));
        }
      }

      private FutureResult<BindResult> performBind(
          final AsynchronousConnection connection,
          final Object[] data,
          final ResultHandler<? super BindResult> handler)
      {
        if(bindRequest instanceof SimpleBindRequest)
        {
          SimpleBindRequest o = (SimpleBindRequest)bindRequest;
          if(br == null)
          {
            br = Requests.copyOfSimpleBindRequest(o);
          }

          SimpleBindRequest sbr = (SimpleBindRequest)br;
          if (data != null && o.getName() != null)
          {
            sbr.setName(String.format(o.getName(), data));
          }
          if(successRecentCount.get() * ((float)invalidCredPercent/100) >
              invalidCredRecentCount.get())
          {
            sbr.setPassword("invalid-password".toCharArray());
          }
          else
          {
            sbr.setPassword(o.getPassword());
          }
        }
        else if(bindRequest instanceof DigestMD5SASLBindRequest)
        {
          DigestMD5SASLBindRequest o = (DigestMD5SASLBindRequest)bindRequest;
          if(br == null)
          {
            br = Requests.copyOfDigestMD5SASLBindRequest(o);
          }

          DigestMD5SASLBindRequest sbr = (DigestMD5SASLBindRequest)br;
          if (data != null)
          {
            if(o.getAuthenticationID() != null)
            {
              sbr.setAuthenticationID(
                  String.format(o.getAuthenticationID(), data));
            }
            if(o.getAuthorizationID() != null)
            {
              sbr.setAuthorizationID(
                  String.format(o.getAuthorizationID(), data));
            }
          }
          if(successRecentCount.get() * ((float)invalidCredPercent/100) >
              invalidCredRecentCount.get())
          {
            sbr.setPassword("invalid-password".toCharArray());
          }
          else
          {
            sbr.setPassword(o.getPassword());
          }
        }
        else if(bindRequest instanceof CRAMMD5SASLBindRequest)
        {
          CRAMMD5SASLBindRequest o = (CRAMMD5SASLBindRequest)bindRequest;
          if(br == null)
          {
            br = Requests.copyOfCRAMMD5SASLBindRequest(o);
          }

          CRAMMD5SASLBindRequest sbr = (CRAMMD5SASLBindRequest)br;
          if (data != null && o.getAuthenticationID() != null)
          {
            sbr.setAuthenticationID(
                String.format(o.getAuthenticationID(), data));
          }
          if(successRecentCount.get() * ((float)invalidCredPercent/100) >
              invalidCredRecentCount.get())
          {
            sbr.setPassword("invalid-password".toCharArray());
          }
          else
          {
            sbr.setPassword(o.getPassword());
          }
        }
        else if(bindRequest instanceof GSSAPISASLBindRequest)
        {
          GSSAPISASLBindRequest o = (GSSAPISASLBindRequest)bindRequest;
          if(br == null)
          {
            br = Requests.copyOfGSSAPISASLBindRequest(o);
          }

          GSSAPISASLBindRequest sbr = (GSSAPISASLBindRequest)br;
          if (data != null)
          {
            if(o.getAuthenticationID() != null)
            {
              sbr.setAuthenticationID(
                  String.format(o.getAuthenticationID(), data));
            }
            if(o.getAuthorizationID() != null)
            {
              sbr.setAuthorizationID(
                  String.format(o.getAuthorizationID(), data));
            }
          }
          if(successRecentCount.get() * ((float)invalidCredPercent/100) >
              invalidCredRecentCount.get())
          {
            sbr.setPassword("invalid-password".toCharArray());
          }
          else
          {
            sbr.setPassword(o.getPassword());
          }
        }
        else if(bindRequest instanceof ExternalSASLBindRequest)
        {
          ExternalSASLBindRequest o = (ExternalSASLBindRequest)bindRequest;
          if(br == null)
          {
            br = Requests.copyOfExternalSASLBindRequest(o);
          }

          ExternalSASLBindRequest sbr = (ExternalSASLBindRequest)br;
          if (data != null && o.getAuthorizationID() != null)
          {
            sbr.setAuthorizationID(String.format(o.getAuthorizationID(), data));
          }
        }
        else if(bindRequest instanceof PlainSASLBindRequest)
        {
          PlainSASLBindRequest o = (PlainSASLBindRequest)bindRequest;
          if(br == null)
          {
            br = Requests.copyOfPlainSASLBindRequest(o);
          }

          PlainSASLBindRequest sbr = (PlainSASLBindRequest)br;
          if (data != null)
          {
            if(o.getAuthenticationID() != null)
            {
              sbr.setAuthenticationID(
                  String.format(o.getAuthenticationID(), data));
            }
            if(o.getAuthorizationID() != null)
            {
              sbr.setAuthorizationID(
                  String.format(o.getAuthorizationID(), data));
            }
          }
          if(successRecentCount.get() * ((float)invalidCredPercent/100) >
              invalidCredRecentCount.get())
          {
            sbr.setPassword("invalid-password".toCharArray());
          }
          else
          {
            sbr.setPassword(o.getPassword());
          }
        }

        return connection.bind(br, handler);
      }
    }



    private String filter;

    private String baseDN;

    private SearchScope scope;

    private DereferenceAliasesPolicy dereferencesAliasesPolicy;

    private String[] attributes;

    private BindRequest bindRequest;

    private int invalidCredPercent;



    private BindPerformanceRunner(final ArgumentParser argParser,
        final ConsoleApplication app) throws ArgumentException
    {
      super(argParser, app, true, true, true);
    }



    @Override
    StatsThread newStatsThread()
    {
      return new BindStatsThread(filter != null && baseDN != null);
    }



    @Override
    WorkerThread newWorkerThread(final AsynchronousConnection connection,
        final ConnectionFactory connectionFactory)
    {
      return new BindWorkerThread(connection, connectionFactory);
    }
  }
  /**
   * The main method for AuthRate tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */

  public static void main(final String[] args)
  {
    final int retCode = mainAuthRate(args, System.in, System.out, System.err);
    System.exit(filterExitCode(retCode));
  }



  /**
   * Parses the provided command-line arguments and uses that information to run
   * the tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @return The error code.
   */

  static int mainAuthRate(final String[] args)
  {
    return mainAuthRate(args, System.in, System.out, System.err);
  }



  /**
   * Parses the provided command-line arguments and uses that information to run
   * the tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @param inStream
   *          The input stream to use for standard input, or <CODE>null</CODE>
   *          if standard input is not needed.
   * @param outStream
   *          The output stream to use for standard output, or <CODE>null</CODE>
   *          if standard output is not needed.
   * @param errStream
   *          The output stream to use for standard error, or <CODE>null</CODE>
   *          if standard error is not needed.
   * @return The error code.
   */

  static int mainAuthRate(final String[] args, final InputStream inStream,
      final OutputStream outStream, final OutputStream errStream)

  {
    return new AuthRate(inStream, outStream, errStream).run(args);
  }



  private BooleanArgument verbose;

  private BooleanArgument scriptFriendly;




  private AuthRate(final InputStream in, final OutputStream out,
      final OutputStream err)
  {
    super(in, out, err);

  }



  /**
   * Indicates whether or not the user has requested advanced mode.
   *
   * @return Returns <code>true</code> if the user has requested advanced mode.
   */
  @Override
  public boolean isAdvancedMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested interactive behavior.
   *
   * @return Returns <code>true</code> if the user has requested interactive
   *         behavior.
   */
  @Override
  public boolean isInteractive()
  {
    return false;
  }



  /**
   * Indicates whether or not this console application is running in its
   * menu-driven mode. This can be used to dictate whether output should go to
   * the error stream or not. In addition, it may also dictate whether or not
   * sub-menus should display a cancel option as well as a quit option.
   *
   * @return Returns <code>true</code> if this console application is running in
   *         its menu-driven mode.
   */
  @Override
  public boolean isMenuDrivenMode()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested quiet output.
   *
   * @return Returns <code>true</code> if the user has requested quiet output.
   */
  @Override
  public boolean isQuiet()
  {
    return false;
  }



  /**
   * Indicates whether or not the user has requested script-friendly output.
   *
   * @return Returns <code>true</code> if the user has requested script-friendly
   *         output.
   */
  @Override
  public boolean isScriptFriendly()
  {
    return scriptFriendly.isPresent();
  }



  /**
   * Indicates whether or not the user has requested verbose output.
   *
   * @return Returns <code>true</code> if the user has requested verbose output.
   */
  @Override
  public boolean isVerbose()
  {
    return verbose.isPresent();
  }



  private int run(final String[] args)
  {
    // Create the command-line argument parser for use with this
    // program.
    final LocalizableMessage toolDescription =
        INFO_AUTHRATE_TOOL_DESCRIPTION.get();
    final ArgumentParser argParser = new ArgumentParser(AuthRate.class
        .getName(), toolDescription, false, true, 0, 0,
        "[filter format string] [attributes ...]");

    ConnectionFactoryProvider connectionFactoryProvider;
    ConnectionFactory connectionFactory;
    BindPerformanceRunner runner;

    StringArgument baseDN;
    MultiChoiceArgument<SearchScope> searchScope;
    MultiChoiceArgument<DereferenceAliasesPolicy> dereferencePolicy;
    BooleanArgument showUsage;
    StringArgument propertiesFileArgument;
    BooleanArgument noPropertiesFileArgument;
    IntegerArgument invalidCredPercent;

    try
    {
      if(System.getProperty("org.opends.sdk.ldap.transport.linger") == null)
      {
        System.setProperty("org.opends.sdk.ldap.transport.linger", "0");
      }
      connectionFactoryProvider =
          new ConnectionFactoryProvider(argParser, this);
      runner = new BindPerformanceRunner(argParser, this);

      propertiesFileArgument = new StringArgument("propertiesFilePath", null,
          OPTION_LONG_PROP_FILE_PATH, false, false, true,
          INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
          INFO_DESCRIPTION_PROP_FILE_PATH.get());
      argParser.addArgument(propertiesFileArgument);
      argParser.setFilePropertiesArgument(propertiesFileArgument);

      noPropertiesFileArgument = new BooleanArgument(
          "noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
          INFO_DESCRIPTION_NO_PROP_FILE.get());
      argParser.addArgument(noPropertiesFileArgument);
      argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
          OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, getOutputStream());

      baseDN = new StringArgument("baseDN", OPTION_SHORT_BASEDN,
          OPTION_LONG_BASEDN, false, false, true, INFO_BASEDN_PLACEHOLDER.get(),
          null, null, INFO_SEARCHRATE_TOOL_DESCRIPTION_BASEDN.get());
      baseDN.setPropertyName(OPTION_LONG_BASEDN);
      argParser.addArgument(baseDN);

      searchScope = new MultiChoiceArgument<SearchScope>("searchScope", 's',
          "searchScope", false, true, INFO_SEARCH_SCOPE_PLACEHOLDER.get(),
          SearchScope.values(), false, INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE
              .get());
      searchScope.setPropertyName("searchScope");
      searchScope.setDefaultValue(SearchScope.WHOLE_SUBTREE);
      argParser.addArgument(searchScope);

      dereferencePolicy = new MultiChoiceArgument<DereferenceAliasesPolicy>(
          "derefpolicy", 'a', "dereferencePolicy", false, true,
          INFO_DEREFERENCE_POLICE_PLACEHOLDER.get(), DereferenceAliasesPolicy
              .values(), false, INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY
              .get());
      dereferencePolicy.setPropertyName("dereferencePolicy");
      dereferencePolicy.setDefaultValue(DereferenceAliasesPolicy.NEVER);
      argParser.addArgument(dereferencePolicy);

      invalidCredPercent = new IntegerArgument("invalidPassword", 'I',
        "invalidPassword", false, false, true, LocalizableMessage
            .raw("{invalidPassword}"), 0, null, true, 0, true, 100,
        LocalizableMessage
            .raw("Percent of bind operations with simulated " +
            "invalid password"));
      invalidCredPercent.setPropertyName("invalidPassword");
      argParser.addArgument(invalidCredPercent);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
          INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);

      scriptFriendly = new BooleanArgument("scriptFriendly", 'S',
          "scriptFriendly", INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());
      scriptFriendly.setPropertyName("scriptFriendly");
      argParser.addArgument(scriptFriendly);
    }
    catch (final ArgumentException ae)
    {
      final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae
          .getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);

      // If we should just display usage or version information,
      // then print it and exit.
      if (argParser.usageOrVersionDisplayed())
      {
        return 0;
      }

      connectionFactory =
          connectionFactoryProvider.getConnectionFactory();
      runner.validate();

      runner.bindRequest = connectionFactoryProvider.getBindRequest();
      if(runner.bindRequest == null)
      {
        throw new ArgumentException(LocalizableMessage.raw(
            "Authentication information must be provided to use this tool"));
      }
    }
    catch (final ArgumentException ae)
    {
      final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae
          .getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    final List<String> attributes = new LinkedList<String>();
    final ArrayList<String> filterAndAttributeStrings = argParser
        .getTrailingArguments();
    if (filterAndAttributeStrings.size() > 0)
    {
      // the list of trailing arguments should be structured as follow:
      // the first trailing argument is considered the filter, the other as
      // attributes.
      runner.filter = filterAndAttributeStrings.remove(0);

      // The rest are attributes
      for (final String s : filterAndAttributeStrings)
      {
        attributes.add(s);
      }
    }
    runner.attributes = attributes.toArray(new String[attributes.size()]);
    runner.baseDN = baseDN.getValue();
    try
    {
      runner.scope = searchScope.getTypedValue();
      runner.dereferencesAliasesPolicy = dereferencePolicy.getTypedValue();
      runner.invalidCredPercent = invalidCredPercent.getIntValue();
    }
    catch (final ArgumentException ex1)
    {
      println(ex1.getMessageObject());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Try it out to make sure the format string and data sources
    // match.
    final Object[] data = DataSource.generateData(runner.getDataSources(),
        null);
    try
    {
      if(runner.baseDN != null && runner.filter != null)
      {
        String.format(runner.filter, data);
        String.format(runner.baseDN, data);
      }
    }
    catch (final Exception ex1)
    {
      println(LocalizableMessage.raw("Error formatting filter or base DN: "
          + ex1.toString()));
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    return runner.run(connectionFactory);
  }
}

