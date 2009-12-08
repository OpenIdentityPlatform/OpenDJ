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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.sdk.*;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.responses.SearchResultReference;




/**
 * A load generation tool that can be used to load a Directory Server
 * with Search requests using one or more LDAP connections.
 */
public final class SearchRate extends ConsoleApplication
{
  private BooleanArgument verbose;



  /**
   * The main method for SearchRate tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainSearchRate(args, System.in, System.out,
        System.err);

    if (retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }



  /**
   * Parses the provided command-line arguments and uses that
   * information to run the ldapsearch tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
   * @return The error code.
   */

  static int mainSearchRate(String[] args)
  {
    return mainSearchRate(args, System.in, System.out, System.err);
  }



  /**
   * Parses the provided command-line arguments and uses that
   * information to run the ldapsearch tool.
   *
   * @param args
   *          The command-line arguments provided to this program.
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

  static int mainSearchRate(String[] args, InputStream inStream,
      OutputStream outStream, OutputStream errStream)

  {
    return new SearchRate(inStream, outStream, errStream).run(args);
  }



  private SearchRate(InputStream in, OutputStream out, OutputStream err)
  {
    super(in, out, err);

  }



  private int run(String[] args)
  {
    // Create the command-line argument parser for use with this
    // program.
    LocalizableMessage toolDescription = LocalizableMessage
        .raw("This utility can be used to "
            + "measure search performance");
    // TODO: correct usage
    ArgumentParser argParser = new ArgumentParser(SearchRate.class
        .getName(), toolDescription, false, true, 1, 0,
        "[filter] [attributes ...]");

    ArgumentParserConnectionFactory connectionFactory;
    SearchPerformanceRunner runner;

    StringArgument baseDN;
    MultiChoiceArgument<SearchScope> searchScope;
    MultiChoiceArgument<DereferenceAliasesPolicy> dereferencePolicy;
    BooleanArgument showUsage;
    StringArgument propertiesFileArgument;
    BooleanArgument noPropertiesFileArgument;

    try
    {
      connectionFactory = new ArgumentParserConnectionFactory(
          argParser, this);
      runner = new SearchPerformanceRunner(argParser, this);

      propertiesFileArgument = new StringArgument("propertiesFilePath",
          null, OPTION_LONG_PROP_FILE_PATH, false, false, true,
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
          OPTION_LONG_BASEDN, true, false, true,
          INFO_BASEDN_PLACEHOLDER.get(), null, null,
          INFO_SEARCH_DESCRIPTION_BASEDN.get());
      baseDN.setPropertyName(OPTION_LONG_BASEDN);
      argParser.addArgument(baseDN);

      searchScope = new MultiChoiceArgument<SearchScope>("searchScope",
          's', "searchScope", false, true,
          INFO_SEARCH_SCOPE_PLACEHOLDER.get(), SearchScope.values(),
          false, INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE.get());
      searchScope.setPropertyName("searchScope");
      searchScope.setDefaultValue(SearchScope.WHOLE_SUBTREE);
      argParser.addArgument(searchScope);

      dereferencePolicy = new MultiChoiceArgument<DereferenceAliasesPolicy>(
          "derefpolicy", 'a', "dereferencePolicy", false, true,
          INFO_DEREFERENCE_POLICE_PLACEHOLDER.get(),
          DereferenceAliasesPolicy.values(), false,
          INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY.get());
      dereferencePolicy.setPropertyName("dereferencePolicy");
      dereferencePolicy.setDefaultValue(DereferenceAliasesPolicy.NEVER);
      argParser.addArgument(dereferencePolicy);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
          INFO_DESCRIPTION_VERBOSE.get());
      verbose.setPropertyName("verbose");
      argParser.addArgument(verbose);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      println(message);
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
      connectionFactory.validate();
      runner.validate();
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());
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

    List<String> attributes = new LinkedList<String>();
    ArrayList<String> filterAndAttributeStrings = argParser
        .getTrailingArguments();
    if (filterAndAttributeStrings.size() > 0)
    {
      // the list of trailing arguments should be structured as follow:
      // the first trailing argument is
      // considered the filter, the other as attributes.
      runner.filter = filterAndAttributeStrings.remove(0);
      // The rest are attributes
      for (String s : filterAndAttributeStrings)
      {
        attributes.add(s);
      }
    }
    runner.attributes = attributes
        .toArray(new String[attributes.size()]);
    runner.baseDN = baseDN.getValue();
    try
    {
      runner.scope = searchScope.getTypedValue();
      runner.dereferencesAliasesPolicy = dereferencePolicy
          .getTypedValue();
    }
    catch (ArgumentException ex1)
    {
      println(ex1.getMessageObject());
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    try
    {
      // Try it out to make sure the format string and data sources
      // match.
      Object[] data = DataSource.generateData(runner.getDataSources(),
          null);
      String.format(runner.filter, data);
      String.format(runner.baseDN, data);
    }
    catch (Exception ex1)
    {
      println(LocalizableMessage.raw("Error formatting filter or base DN: "
          + ex1.toString()));
      return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
    }

    return runner.run(connectionFactory);
  }



  private final AtomicInteger entryRecentCount = new AtomicInteger();



  private class SearchPerformanceRunner extends PerformanceRunner
  {
    private String filter;

    private String baseDN;

    private SearchScope scope;

    private DereferenceAliasesPolicy dereferencesAliasesPolicy;

    private String[] attributes;



    private SearchPerformanceRunner(ArgumentParser argParser,
        ConsoleApplication app) throws ArgumentException
    {
      super(argParser, app);
    }



    WorkerThread<?> newWorkerThread(AsynchronousConnection connection,
        ConnectionFactory<?> connectionFactory)
    {
      return new SearchWorkerThread(connection, connectionFactory);
    }



    StatsThread newStatsThread()
    {
      return new SearchStatsThread();
    }



    private class SearchStatsHandler extends
        UpdateStatsResultHandler<Result> implements
        SearchResultHandler<Void>
    {
      private SearchStatsHandler(long eTime)
      {
        super(eTime);
      }



      public void handleEntry(Void p, SearchResultEntry entry)
      {
        entryRecentCount.getAndIncrement();
      }



      public void handleReference(Void p,
          SearchResultReference reference)
      {
      }
    }



    private class SearchWorkerThread extends
        WorkerThread<SearchStatsHandler>
    {
      private SearchRequest sr;

      private Object[] data;



      private SearchWorkerThread(AsynchronousConnection connection,
          ConnectionFactory<?> connectionFactory)
      {
        super(connection, connectionFactory);
      }



      public SearchStatsHandler getHandler(long startTime)
      {
        return new SearchStatsHandler(startTime);
      }



      public ResultFuture<?> performOperation(
          AsynchronousConnection connection,
          SearchStatsHandler handler, DataSource[] dataSources)
      {
        if (sr == null)
        {
          if (dataSources == null)
          {
            sr = Requests.newSearchRequest(baseDN, scope, filter,
                attributes);
          }
          else
          {
            data = DataSource.generateData(dataSources, data);
            sr = Requests.newSearchRequest(String.format(baseDN, data),
                scope, String.format(filter, data), attributes);
          }
          sr.setDereferenceAliasesPolicy(dereferencesAliasesPolicy);
        }
        else if (dataSources != null)
        {
          data = DataSource.generateData(dataSources, data);
          sr.setFilter(String.format(filter, data));
          sr.setName(String.format(baseDN, data));
        }
        return connection.search(sr, handler, handler, null);
      }
    }



    private class SearchStatsThread extends StatsThread
    {
      private long totalEntryCount;

      private final String[] extraColumn;



      private SearchStatsThread()
      {
        super(new String[] { "Entries/Srch" });
        extraColumn = new String[1];
      }



      String[] getAdditionalColumns()
      {
        int entryCount = entryRecentCount.getAndSet(0);
        totalEntryCount += entryCount;
        extraColumn[0] = String.format("%.1f", (double) entryCount
            / successCount);
        return extraColumn;
      }
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
