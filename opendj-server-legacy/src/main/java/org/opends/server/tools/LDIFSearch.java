/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.*;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;

import com.forgerock.opendj.cli.*;

/**
 * This class provides a program that may be used to search LDIF files.  It is
 * modeled after the LDAPSearch tool, with the primary differencing being that
 * all of its data comes from LDIF rather than communicating over LDAP.
 * However, it does have a number of differences that allow it to perform
 * multiple operations in a single pass rather than requiring multiple passes
 * through the LDIF.
 */
public class LDIFSearch
{
  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.tools.LDIFSearch";

  /** The search scope string that will be used for baseObject searches. */
  private static final String SCOPE_STRING_BASE = "base";
  /** The search scope string that will be used for singleLevel searches. */
  private static final String SCOPE_STRING_ONE = "one";
  /** The search scope string that will be used for wholeSubtree searches. */
  private static final String SCOPE_STRING_SUB = "sub";
  /** The search scope string that will be used for subordinateSubtree searches. */
  private static final String SCOPE_STRING_SUBORDINATE = "subordinate";

  /**
   * Provides the command line arguments to the <CODE>mainSearch</CODE> method
   * so that they can be processed.
   *
   * @param  args  The command line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int exitCode = mainSearch(args, true, System.out, System.err);
    if (exitCode != 0)
    {
      System.exit(filterExitCode(exitCode));
    }
  }



  /**
   * Parses the provided command line arguments and performs the appropriate
   * search operation.
   *
   * @param  args              The command line arguments provided to this
   *                           program.
   * @param  initializeServer  True if server initialization should be done.
   * @param  outStream         The output stream to use for standard output, or
   *                           {@code null} if standard output is not needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           {@code null} if standard error is not needed.
   *
   * @return  The return code for this operation.  A value of zero indicates
   *          that all processing completed successfully.  A nonzero value
   *          indicates that some problem occurred during processing.
   */
  public static int mainSearch(String[] args, boolean initializeServer,
                               OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    LinkedHashSet<String> scopeStrings = new LinkedHashSet<>(4);
    scopeStrings.add(SCOPE_STRING_BASE);
    scopeStrings.add(SCOPE_STRING_ONE);
    scopeStrings.add(SCOPE_STRING_SUB);
    scopeStrings.add(SCOPE_STRING_SUBORDINATE);


    BooleanArgument     dontWrap;
    BooleanArgument     overwriteExisting;
    BooleanArgument     showUsage;
    StringArgument      filterFile;
    IntegerArgument     sizeLimit;
    IntegerArgument     timeLimit;
    MultiChoiceArgument scopeString;
    StringArgument      baseDNString;
    StringArgument      configClass;
    StringArgument      configFile;
    StringArgument      ldifFile;
    StringArgument      outputFile;


    LocalizableMessage toolDescription = INFO_LDIFSEARCH_TOOL_DESCRIPTION.get();
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false, true, 0, 0,
                                                  "[filter] [attributes ...]");
    argParser.setShortToolDescription(REF_SHORT_DESC_LDIFSEARCH.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    try
    {
      ldifFile = new StringArgument(
              "ldiffile", 'l', "ldifFile", false, true,
              true, INFO_LDIFFILE_PLACEHOLDER.get(), null, null,
              INFO_LDIFSEARCH_DESCRIPTION_LDIF_FILE.get());
      argParser.addArgument(ldifFile);

      baseDNString = new StringArgument(
              "basedn", OPTION_SHORT_BASEDN,
              OPTION_LONG_BASEDN, false, true,
              true, INFO_BASEDN_PLACEHOLDER.get(), "", null,
              INFO_LDIFSEARCH_DESCRIPTION_BASEDN.get());
      argParser.addArgument(baseDNString);

      scopeString = new MultiChoiceArgument(
              "scope", 's', "searchScope", false, false,
              true, INFO_SCOPE_PLACEHOLDER.get(), SCOPE_STRING_SUB,
              null, scopeStrings, false,
              INFO_LDIFSEARCH_DESCRIPTION_SCOPE.get());
      argParser.addArgument(scopeString);

      configFile = new StringArgument(
              "configfile", 'c', "configFile", false,
              false, true, INFO_CONFIGFILE_PLACEHOLDER.get(), null, null,
              INFO_DESCRIPTION_CONFIG_FILE.get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);

      configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                             OPTION_LONG_CONFIG_CLASS, false,
                             false, true, INFO_CONFIGCLASS_PLACEHOLDER.get(),
                             ConfigFileHandler.class.getName(), null,
                             INFO_DESCRIPTION_CONFIG_CLASS.get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);

      filterFile = new StringArgument("filterfile", 'f', "filterFile", false,
          false, true, INFO_FILTER_FILE_PLACEHOLDER.get(), null, null,
          INFO_LDIFSEARCH_DESCRIPTION_FILTER_FILE.get());
      argParser.addArgument(filterFile);

      outputFile = new StringArgument(
              "outputfile", 'o', "outputFile", false,
              false, true, INFO_OUTPUT_FILE_PLACEHOLDER.get(), null, null,
              INFO_LDIFSEARCH_DESCRIPTION_OUTPUT_FILE.get());
      argParser.addArgument(outputFile);

      overwriteExisting =
           new BooleanArgument(
                   "overwriteexisting", 'O',"overwriteExisting",
                   INFO_LDIFSEARCH_DESCRIPTION_OVERWRITE_EXISTING.get());
      argParser.addArgument(overwriteExisting);

      dontWrap = new BooleanArgument(
              "dontwrap", 'T', "dontWrap",
              INFO_LDIFSEARCH_DESCRIPTION_DONT_WRAP.get());
      argParser.addArgument(dontWrap);

      sizeLimit = new IntegerArgument(
              "sizelimit", 'z', "sizeLimit", false,
              false, true, INFO_SIZE_LIMIT_PLACEHOLDER.get(), 0, null,
              true, 0, false, 0,
              INFO_LDIFSEARCH_DESCRIPTION_SIZE_LIMIT.get());
      argParser.addArgument(sizeLimit);

      timeLimit = new IntegerArgument(
              "timelimit", 't', "timeLimit", false,
              false, true, INFO_TIME_LIMIT_PLACEHOLDER.get(), 0, null,
              true, 0, false, 0,
              INFO_LDIFSEARCH_DESCRIPTION_TIME_LIMIT.get());
      argParser.addArgument(timeLimit);


      showUsage = CommonArguments.getShowUsage();
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      err.println(message);
      return 1;
    }


    // Parse the command-line arguments provided to the program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(ae.getMessage());

      err.println(message);
      err.println(argParser.getUsage());
      return LDAPResultCode.CLIENT_SIDE_PARAM_ERROR;
    }


    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    // Make sure that at least one filter was provided.  Also get the attribute
    // list at the same time because it may need to be specified in the same
    // way.
    boolean            allUserAttrs        = false;
    boolean            allOperationalAttrs = false;
    //Return objectclass attribute unless analysis of the arguments determines
    //otherwise.
    boolean            includeObjectclassAttrs = true;
    final LinkedList<String> attributeNames = new LinkedList<>();
    LinkedList<String> objectClassNames = new LinkedList<>();
    LinkedList<String> filterStrings = new LinkedList<>();
    if (filterFile.isPresent())
    {
      BufferedReader in = null;
      try
      {
        String fileNameValue = filterFile.getValue();
        in = new BufferedReader(new FileReader(fileNameValue));
        String line = null;

        while ((line = in.readLine()) != null)
        {
          if(line.trim().equals(""))
          {
            // ignore empty lines.
            continue;
          }
          filterStrings.add(line);
        }
      } catch(Exception e)
      {
        err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
        return 1;
      }
      finally
      {
        close(in);
      }

      ArrayList<String> trailingArguments = argParser.getTrailingArguments();
      if (trailingArguments != null && !trailingArguments.isEmpty())
      {
        for (String attributeName : trailingArguments)
        {
          String lowerName = toLowerCase(attributeName);
          if (lowerName.equals("*"))
          {
            allUserAttrs = true;
          }
          else if (lowerName.equals("+"))
          {
            allOperationalAttrs = true;
          }
          else if (lowerName.startsWith("@"))
          {
            objectClassNames.add(lowerName.substring(1));
          }
          else
          {
            attributeNames.add(lowerName);
          }
        }
      }
    }
    else
    {
      ArrayList<String> trailingArguments = argParser.getTrailingArguments();
      if ((trailingArguments == null) || trailingArguments.isEmpty())
      {
        LocalizableMessage message = ERR_LDIFSEARCH_NO_FILTER.get();
        err.println(message);
        return 1;
      }
      else
      {
        Iterator<String> iterator = trailingArguments.iterator();

        filterStrings = new LinkedList<>();
        filterStrings.add(iterator.next());

        while (iterator.hasNext())
        {
          String lowerName = toLowerCase(iterator.next());
          if (lowerName.equals("*"))
          {
            allUserAttrs = true;
          }
          else if (lowerName.equals("+"))
          {
            allOperationalAttrs = true;
          }
          else if (lowerName.startsWith("@"))
          {
            objectClassNames.add(lowerName.substring(1));
          }
          else
          {
            attributeNames.add(lowerName);
          }
        }
      }
    }

    if (attributeNames.isEmpty()
        && objectClassNames.isEmpty()
        && !allOperationalAttrs)
    {
      // This will be true if no attributes were requested, which is effectively
      // all user attributes.  It will also be true if just "*" was included,
      // but the net result will be the same.
      allUserAttrs = true;
    }

    //Determine if objectclass attribute should be returned.
    if(!allUserAttrs) {
      //Single '+', never return objectclass.
      if(allOperationalAttrs && objectClassNames.isEmpty() &&
         attributeNames.isEmpty())
      {
        includeObjectclassAttrs=false;
      }
      //If "objectclass" isn't specified in the attributes to return, then
      //don't include objectclass attribute.
      if(!attributeNames.isEmpty() && objectClassNames.isEmpty() &&
         !attributeNames.contains("objectclass"))
      {
        includeObjectclassAttrs=false;
      }
    }


    // Bootstrap the Directory Server configuration for use as a client.
    DirectoryServer directoryServer = DirectoryServer.getInstance();

    // If we're to use the configuration then initialize it, along with the
    // schema.
    boolean checkSchema = configFile.isPresent();

    if(initializeServer) {
     DirectoryServer.bootstrapClient();

    if (checkSchema)
    {
      try
      {
        DirectoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        err.println(ERR_LDIFSEARCH_CANNOT_INITIALIZE_JMX.get(configFile.getValue(), e.getMessage()));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (Exception e)
      {
        err.println(ERR_LDIFSEARCH_CANNOT_INITIALIZE_CONFIG.get(configFile.getValue(), e.getMessage()));
        return 1;
      }

      try
      {
        directoryServer.initializeSchema();
      }
      catch (Exception e)
      {
        err.println(ERR_LDIFSEARCH_CANNOT_INITIALIZE_SCHEMA.get(configFile.getValue(), e.getMessage()));
        return 1;
      }
    }
    }

    // Choose the desired search scope.
    SearchScope searchScope;
    if (scopeString.isPresent())
    {
      String scopeStr = toLowerCase(scopeString.getValue());
      if (scopeStr.equals(SCOPE_STRING_BASE))
      {
        searchScope = SearchScope.BASE_OBJECT;
      }
      else if (scopeStr.equals(SCOPE_STRING_ONE))
      {
        searchScope = SearchScope.SINGLE_LEVEL;
      }
      else if (scopeStr.equals(SCOPE_STRING_SUBORDINATE))
      {
        searchScope = SearchScope.SUBORDINATES;
      }
      else
      {
        searchScope = SearchScope.WHOLE_SUBTREE;
      }
    }
    else
    {
      searchScope = SearchScope.WHOLE_SUBTREE;
    }


    // Create the list of filters that will be used to process the searches.
    LinkedList<SearchFilter> searchFilters = new LinkedList<>();
    for (String filterString : filterStrings)
    {
      try
      {
        searchFilters.add(SearchFilter.createFilterFromString(filterString));
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_LDIFSEARCH_CANNOT_PARSE_FILTER.get(
                filterString, e.getMessage());
        err.println(message);
        return 1;
      }
    }


    // Transform the attributes to return from strings to attribute types.
    LinkedHashSet<AttributeType> userAttributeTypes = new LinkedHashSet<>();
    LinkedHashSet<AttributeType> operationalAttributeTypes = new LinkedHashSet<>();
    for (String attributeName : attributeNames)
    {
      AttributeType t = DirectoryServer.getAttributeTypeOrDefault(attributeName);
      if (t.isOperational())
      {
        operationalAttributeTypes.add(t);
      }
      else
      {
        userAttributeTypes.add(t);
      }
    }

    for (String objectClassName : objectClassNames)
    {
      ObjectClass c = DirectoryServer.getObjectClass(objectClassName, true);
      for (AttributeType t : c.getRequiredAttributeChain())
      {
        if (t.isOperational())
        {
          operationalAttributeTypes.add(t);
        }
        else
        {
          userAttributeTypes.add(t);
        }
      }

      for (AttributeType t : c.getOptionalAttributeChain())
      {
        if (t.isOperational())
        {
          operationalAttributeTypes.add(t);
        }
        else
        {
          userAttributeTypes.add(t);
        }
      }
    }


    // Set the base DNs for the import config.
    LinkedList<DN> baseDNs = new LinkedList<>();
    if (baseDNString.isPresent())
    {
      for (String dnString : baseDNString.getValues())
      {
        try
        {
          baseDNs.add(DN.valueOf(dnString));
        }
        catch (Exception e)
        {
          LocalizableMessage message = ERR_LDIFSEARCH_CANNOT_PARSE_BASE_DN.get(
                  dnString, e.getMessage());
          err.println(message);
          return 1;
        }
      }
    }
    else
    {
      baseDNs.add(DN.rootDN());
    }


    // Get the time limit in milliseconds.
    long timeLimitMillis;
    try
    {
      if (timeLimit.isPresent())
      {
        timeLimitMillis = 1000L * timeLimit.getIntValue();
      }
      else
      {
        timeLimitMillis = 0;
      }
    }
    catch (Exception e)
    {
      err.println(ERR_LDIFSEARCH_CANNOT_PARSE_TIME_LIMIT.get(e));
      return 1;
    }


    // Convert the size limit to an integer.
    int sizeLimitValue;
    try
    {
      if (sizeLimit.isPresent())
      {
        sizeLimitValue = sizeLimit.getIntValue();
      }
      else
      {
        sizeLimitValue =0;
      }
    }
    catch (Exception e)
    {
      err.println(ERR_LDIFSEARCH_CANNOT_PARSE_SIZE_LIMIT.get(e));
      return 1;
    }


    // Create the LDIF import configuration that will be used to read the source
    // data.
    LDIFImportConfig importConfig;
    if (ldifFile.isPresent())
    {
      importConfig = new LDIFImportConfig(ldifFile.getValues());
    }
    else
    {
      importConfig = new LDIFImportConfig(System.in);
    }


    // Create the LDIF export configuration that will be used to write the
    // matching entries.
    LDIFExportConfig exportConfig;
    if (outputFile.isPresent())
    {
      if (overwriteExisting.isPresent())
      {
        exportConfig = new LDIFExportConfig(outputFile.getValue(),
                                            ExistingFileBehavior.OVERWRITE);
      }
      else
      {
        exportConfig = new LDIFExportConfig(outputFile.getValue(),
                                            ExistingFileBehavior.APPEND);
      }
    }
    else
    {
      exportConfig = new LDIFExportConfig(out);
    }

    exportConfig.setIncludeObjectClasses(includeObjectclassAttrs);
    if (dontWrap.isPresent())
    {
      exportConfig.setWrapColumn(0);
    }
    else
    {
      exportConfig.setWrapColumn(75);
    }


    // Create the LDIF reader/writer from the import/export config.
    LDIFReader reader;
    LDIFWriter writer;
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      err.println(ERR_LDIFSEARCH_CANNOT_CREATE_READER.get(e));
      return 1;
    }

    try
    {
      writer = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      close(reader);
      err.println(ERR_LDIFSEARCH_CANNOT_CREATE_WRITER.get(e));
      return 1;
    }


    // Start reading data from the LDIF reader.
    long startTime  = System.currentTimeMillis();
    long stopTime   = startTime + timeLimitMillis;
    long matchCount = 0;
    int  resultCode = LDAPResultCode.SUCCESS;
    while (true)
    {
      // If the time limit has been reached, then stop now.
      if ((timeLimitMillis > 0) && (System.currentTimeMillis() > stopTime))
      {
        resultCode = LDAPResultCode.TIME_LIMIT_EXCEEDED;

        LocalizableMessage message = WARN_LDIFSEARCH_TIME_LIMIT_EXCEEDED.get();
        err.println(message);
        break;
      }


      try
      {
        Entry entry = reader.readEntry(checkSchema);
        if (entry == null)
        {
          break;
        }


        // Check to see if the entry has an acceptable base and scope.
        boolean matchesBaseAndScope = false;
        for (DN baseDN : baseDNs)
        {
          if (entry.matchesBaseAndScope(baseDN, searchScope))
          {
            matchesBaseAndScope = true;
            break;
          }
        }

        if (! matchesBaseAndScope)
        {
          continue;
        }


        // Check to see if the entry matches any of the filters.
        boolean matchesFilter = false;
        for (SearchFilter filter : searchFilters)
        {
          if (filter.matchesEntry(entry))
          {
            matchesFilter = true;
            break;
          }
        }

        if (! matchesFilter)
        {
          continue;
        }


        // Prepare the entry to return to the client.
        if (! allUserAttrs)
        {
          Iterator<AttributeType> iterator =
               entry.getUserAttributes().keySet().iterator();
          while (iterator.hasNext())
          {
            if (! userAttributeTypes.contains(iterator.next()))
            {
              iterator.remove();
            }
          }
        }

        if (! allOperationalAttrs)
        {
          Iterator<AttributeType> iterator =
               entry.getOperationalAttributes().keySet().iterator();
          while (iterator.hasNext())
          {
            if (! operationalAttributeTypes.contains(iterator.next()))
            {
              iterator.remove();
            }
          }
        }


        // Write the entry to the client and increase the count.
        // FIXME -- Should we include a comment about which base+filter matched?
        writer.writeEntry(entry);
        writer.flush();

        matchCount++;
        if ((sizeLimitValue > 0) && (matchCount >= sizeLimitValue))
        {
          resultCode = LDAPResultCode.SIZE_LIMIT_EXCEEDED;

          LocalizableMessage message = WARN_LDIFSEARCH_SIZE_LIMIT_EXCEEDED.get();
          err.println(message);
          break;
        }
      }
      catch (LDIFException le)
      {
        if (le.canContinueReading())
        {
          LocalizableMessage message = ERR_LDIFSEARCH_CANNOT_READ_ENTRY_RECOVERABLE.get(
                  le.getMessage());
          err.println(message);
        }
        else
        {
          LocalizableMessage message = ERR_LDIFSEARCH_CANNOT_READ_ENTRY_FATAL.get(
                  le.getMessage());
          err.println(message);
          resultCode = LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
          break;
        }
      }
      catch (Exception e)
      {
        err.println(ERR_LDIFSEARCH_ERROR_DURING_PROCESSING.get(e));
        resultCode = LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR;
        break;
      }
    }

    close(reader, writer);

    return resultCode;
  }
}

