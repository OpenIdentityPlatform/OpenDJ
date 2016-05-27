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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CommonArguments.*;
import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DirectoryServer.DirectoryServerVersionHandler;
import org.opends.server.loggers.JDKLogging;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.BuildVersion;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.TableBuilder;
import com.forgerock.opendj.cli.TextTablePrinter;

/**
 * This program provides a utility that may be used to list the backends in the
 * server, as well as to determine which backend holds a given entry.
 */
public class ListBackends
{
  /**
   * Parses the provided command-line arguments and uses that information to
   * list the backend information.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    int retCode = listBackends(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * list the backend information.
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
   * @return  A return code indicating whether the processing was successful.
   */
  public static int listBackends(String[] args, boolean initializeServer,
                                 OutputStream outStream, OutputStream errStream)
  {
    PrintStream out = NullOutputStream.wrapOrNullStream(outStream);
    PrintStream err = NullOutputStream.wrapOrNullStream(errStream);
    JDKLogging.disableLogging();

    // Define the command-line arguments that may be used with this program.
    BooleanArgument displayUsage = null;
    StringArgument  backendID    = null;
    StringArgument  baseDN       = null;
    StringArgument  configFile   = null;

    // Create the command-line argument parser for use with this program.
    LocalizableMessage toolDescription = INFO_LISTBACKENDS_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.ListBackends",
                            toolDescription, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_LIST_BACKENDS.get());
    argParser.setVersionHandler(new DirectoryServerVersionHandler());

    // Initialize all the command-line argument types and register them with the parser.
    try
    {
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('f')
                      .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      backendID =
              StringArgument.builder("backendID")
                      .shortIdentifier('n')
                      .description(INFO_LISTBACKENDS_DESCRIPTION_BACKEND_ID.get())
                      .multiValued()
                      .valuePlaceholder(INFO_BACKENDNAME_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      baseDN =
              StringArgument.builder(OPTION_LONG_BASEDN)
                      .shortIdentifier(OPTION_SHORT_BASEDN)
                      .description(INFO_LISTBACKENDS_DESCRIPTION_BASE_DN.get())
                      .multiValued()
                      .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);

      displayUsage = showUsageArgument();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage, out);
    }
    catch (ArgumentException ae)
    {
      printWrappedText(err, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(err, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
      return 1;
    }

    // If we should just display usage or version information,
    // then it's already been done so just return.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    // Make sure that the user did not provide both the backend ID and base DN
    // arguments.
    if (backendID.isPresent() && baseDN.isPresent())
    {
      printWrappedText(err, conflictingArgsErrorMessage(backendID, baseDN));
      return 1;
    }

    // Checks the version - if upgrade required, the tool is unusable
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      printWrappedText(err, e.getMessage());
      return 1;
    }

    if (initializeServer)
    {
      try
      {
        new DirectoryServer.InitializationBuilder(configFile.getValue())
            .initialize();
      }
      catch (Exception e)
      {
        printWrappedText(err, e.getLocalizedMessage());
        return 1;
      }
    }

    // Retrieve a list of the backends defined in the server.
    Map<String, Set<DN>> backends;
    try
    {
      backends = getBackends();
    }
    catch (ConfigException ce)
    {
      printWrappedText(err, ERR_LISTBACKENDS_CANNOT_GET_BACKENDS.get(ce.getMessage()));
      return 1;
    }
    catch (Exception e)
    {
      printWrappedText(err, ERR_LISTBACKENDS_CANNOT_GET_BACKENDS.get(getExceptionMessage(e)));
      return 1;
    }

    // See what action we need to take based on the arguments provided.  If the
    // backend ID argument was present, then list the base DNs for that backend.
    // If the base DN argument was present, then list the backend for that base
    // DN.  If no arguments were provided, then list all backends and base DNs.
    boolean invalidDn = false;
    if (baseDN.isPresent())
    {
      // Create a map from the base DNs of the backends to the corresponding backend ID.
      Map<DN, String> baseToIDMap = new TreeMap<>();
      for (String id : backends.keySet())
      {
        for (DN dn : backends.get(id))
        {
          baseToIDMap.put(dn, id);
        }
      }

      // Iterate through the base DN values specified by the user.  Determine
      // the backend for that entry, and whether the provided DN is a base DN
      // for that backend.
      for (String dnStr : baseDN.getValues())
      {
        DN dn;
        try
        {
          dn = DN.valueOf(dnStr);
        }
        catch (Exception e)
        {
          printWrappedText(err, ERR_LISTBACKENDS_INVALID_DN.get(dnStr, getExceptionMessage(e)));
          return 1;
        }

        String id = baseToIDMap.get(dn);
        if (id == null)
        {
          err.println(INFO_LISTBACKENDS_NOT_BASE_DN.get(dn));

          DN parentDN = dn.parent();
          while (true)
          {
            if (parentDN == null)
            {
              err.println(INFO_LISTBACKENDS_NO_BACKEND_FOR_DN.get(dn));
              invalidDn = true;
              break;
            }
            else
            {
              id = baseToIDMap.get(parentDN);
              if (id != null)
              {
                out.println(INFO_LISTBACKENDS_DN_BELOW_BASE.get(dn, parentDN, id));
                break;
              }
            }

            parentDN = parentDN.parent();
          }
        }
        else
        {
          out.println(INFO_LISTBACKENDS_BASE_FOR_ID.get(dn, id));
        }
      }
    }
    else
    {
      List<String> backendIDs = backendID.isPresent() ? backendID.getValues() : new LinkedList<>(backends.keySet());

      // Figure out the length of the longest backend ID and base DN defined in
      // the server.  We'll use that information to try to align the output.
      LocalizableMessage backendIDLabel = INFO_LISTBACKENDS_LABEL_BACKEND_ID.get();
      LocalizableMessage baseDNLabel = INFO_LISTBACKENDS_LABEL_BASE_DN.get();
      int    backendIDLength = 10;
      int    baseDNLength    = 7;

      Iterator<String> it = backendIDs.iterator();
      while (it.hasNext())
      {
        String id = it.next();
        Set<DN> baseDNs = backends.get(id);
        if (baseDNs == null)
        {
          printWrappedText(err, ERR_LISTBACKENDS_NO_SUCH_BACKEND.get(id));
          it.remove();
        }
        else
        {
          backendIDLength = Math.max(id.length(), backendIDLength);
          for (DN dn : baseDNs)
          {
            baseDNLength = Math.max(dn.toString().length(), baseDNLength);
          }
        }
      }

      if (backendIDs.isEmpty())
      {
        printWrappedText(err, ERR_LISTBACKENDS_NO_VALID_BACKENDS.get());
        return 1;
      }

      TableBuilder table = new TableBuilder();
      LocalizableMessage[] headers = {backendIDLabel, baseDNLabel};
      for (LocalizableMessage header : headers)
      {
        table.appendHeading(header);
      }
      for (String id : backendIDs)
      {
        table.startRow();
        table.appendCell(id);
        StringBuilder buf = new StringBuilder();

        Set<DN> baseDNs = backends.get(id);
        boolean isFirst = true;
        for (DN dn : baseDNs)
        {
          if (!isFirst)
          {
            buf.append(",");
          }
          else
          {
            isFirst = false;
          }
          if (dn.size() > 1)
          {
            buf.append("\"").append(dn).append("\"");
          }
          else
          {
            buf.append(dn);
          }
        }
        table.appendCell(buf.toString());
      }
      TextTablePrinter printer = new TextTablePrinter(out);
      printer.setColumnSeparator(LIST_TABLE_SEPARATOR);
      table.print(printer);
    }

    // If we've gotten here, then everything completed successfully.
    return invalidDn ? 1 : 0 ;
  }

  /**
   * Retrieves information about the backends configured in the Directory Server
   * mapped between the backend ID to the set of base DNs for that backend.
   *
   * @return  Information about the backends configured in the Directory Server.
   *
   * @throws  ConfigException  If a problem occurs while reading the server
   *                           configuration.
   */
  private static Map<String, Set<DN>> getBackends() throws ConfigException
  {
    // Get the base entry for all backend configuration.
    DN backendBaseDN = null;
    try
    {
      backendBaseDN = DN.valueOf(DN_BACKEND_BASE);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CANNOT_DECODE_BACKEND_BASE_DN.get(
          DN_BACKEND_BASE, getExceptionMessage(e));
      throw new ConfigException(message, e);
    }

    // Iterate through the immediate children, attempting to parse them as backends.
    Map<String, Set<DN>> backendMap = new TreeMap<>();
    ConfigurationHandler configHandler = DirectoryServer.getConfigurationHandler();
    for (DN childrenDn : configHandler.getChildren(backendBaseDN))
    {
      Entry configEntry = Converters.to(configHandler.getEntry(childrenDn));
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID = null;
      try
      {
        backendID = BackendToolUtils.getStringSingleValuedAttribute(configEntry, ATTR_BACKEND_ID);
        if (backendID == null)
        {
          continue;
        }
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_DETERMINE_BACKEND_ID.get(configEntry.getName(), getExceptionMessage(e));
        throw new ConfigException(message, e);
      }

      // Get the base DN attribute from the entry.  If there isn't one, then
      // just skip this entry.
      Set<DN> baseDNs = new TreeSet<>();
      try
      {
        List<Attribute> attributes = configEntry.getAttribute(ATTR_BACKEND_BASE_DN);
        if (!attributes.isEmpty())
        {
          Attribute attribute = attributes.get(0);
          for (ByteString byteString : attribute)
          {
            baseDNs.add(DN.valueOf(byteString.toString()));
          }
        }
      }
      catch (Exception e)
      {
        LocalizableMessage message = ERR_CANNOT_DETERMINE_BASES_FOR_BACKEND.get(
            configEntry.getName(), getExceptionMessage(e));
        throw new ConfigException(message, e);
      }

      backendMap.put(backendID, baseDNs);
    }

    return backendMap;
  }
}
