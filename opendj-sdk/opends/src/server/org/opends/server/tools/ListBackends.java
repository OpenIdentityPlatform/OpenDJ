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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.NullOutputStream;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;




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
      System.exit(retCode);
    }
  }



  /**
   * Parses the provided command-line arguments and uses that information to
   * list the backend information.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return  A return code indicating whether the processing was successful.
   */
  public static int listBackends(String[] args)
  {
    return listBackends(args, true, System.out, System.err);
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

    // Define the command-line arguments that may be used with this program.
    BooleanArgument displayUsage = null;
    StringArgument  backendID    = null;
    StringArgument  baseDN       = null;
    StringArgument  configClass  = null;
    StringArgument  configFile   = null;


    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_LISTBACKENDS_TOOL_DESCRIPTION);
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.tools.ListBackends",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass =
           new StringArgument("configclass", 'C', "configClass", true, false,
                              true, "{configClass}",
                              ConfigFileHandler.class.getName(), null,
                              MSGID_LISTBACKENDS_DESCRIPTION_CONFIG_CLASS);
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile =
           new StringArgument("configfile", 'f', "configFile", true, false,
                              true, "{configFile}", null, null,
                              MSGID_LISTBACKENDS_DESCRIPTION_CONFIG_FILE);
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      backendID = new StringArgument("backendid", 'n', "backendID", false,
                                     true, true, "{backendID}", null, null,
                                     MSGID_LISTBACKENDS_DESCRIPTION_BACKEND_ID);
      argParser.addArgument(backendID);


      baseDN = new StringArgument("basedn", 'b', "baseDN", false, true, true,
                                  "{baseDN}", null, null,
                                  MSGID_LISTBACKENDS_DESCRIPTION_BASE_DN);
      argParser.addArgument(baseDN);


      displayUsage = new BooleanArgument("help", 'H', "help",
                                         MSGID_LISTBACKENDS_DESCRIPTION_HELP);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage, out);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_LISTBACKENDS_CANNOT_INITIALIZE_ARGS;
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
      int    msgID   = MSGID_LISTBACKENDS_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }


    // If we should just display usage information, then it's already been done
    // so just return.
    if (displayUsage.isPresent())
    {
      return 0;
    }


    // Make sure that the user did not provide both the backend ID and base DN
    // arguments.
    if (backendID.isPresent() && baseDN.isPresent())
    {
      int    msgID   = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, backendID.getLongIdentifier(),
                                  baseDN.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // Perform the initial bootstrap of the Directory Server and process the
    // configuration.
    DirectoryServer directoryServer = DirectoryServer.getInstance();

    if (initializeServer)
    {
      try
      {
        directoryServer.bootstrapClient();
        directoryServer.initializeJMX();
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LISTBACKENDS_SERVER_BOOTSTRAP_ERROR;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      try
      {
        directoryServer.initializeConfiguration(configClass.getValue(),
                                                configFile.getValue());
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_LOAD_CONFIG;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }



      // Initialize the Directory Server schema elements.
      try
      {
        directoryServer.initializeSchema();
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ce.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (InitializationException ie)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, ie.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_LOAD_SCHEMA;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }


    // Retrieve a list of the backkends defined in the server.
    TreeMap<String,TreeSet<DN>> backends;
    try
    {
      backends = getBackends();
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_LISTBACKENDS_CANNOT_GET_BACKENDS;
      String message = getMessage(msgID, ce.getMessage());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LISTBACKENDS_CANNOT_GET_BACKENDS;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }


    // See what action we need to take based on the arguments provided.  If the
    // backend ID argument was present, then list the base DNs for that backend.
    // If the base DN argument was present, then list the backend for that base
    // DN.  If no arguments were provided, then list all backends and base DNs.
    if (baseDN.isPresent())
    {
      // Create a map from the base DNs of the backends to the corresponding
      // backend ID.
      TreeMap<DN,String> baseToIDMap = new TreeMap<DN,String>();
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
          dn = DN.decode(dnStr);
        }
        catch (DirectoryException de)
        {
          int    msgID   = MSGID_LISTBACKENDS_INVALID_DN;
          String message = getMessage(msgID, dnStr, de.getMessage());
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_LISTBACKENDS_INVALID_DN;
          String message = getMessage(msgID, dnStr,
                                      stackTraceToSingleLineString(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          return 1;
        }


        String id = baseToIDMap.get(dn);
        if (id == null)
        {
          int    msgID   = MSGID_LISTBACKENDS_NOT_BASE_DN;
          String message = getMessage(msgID, dn.toString());
          out.println(message);

          DN parentDN = dn.getParent();
          while (true)
          {
            if (parentDN == null)
            {
              msgID   = MSGID_LISTBACKENDS_NO_BACKEND_FOR_DN;
              message = getMessage(msgID, dn.toString());
              out.println(message);
              break;
            }
            else
            {
              id = baseToIDMap.get(parentDN);
              if (id != null)
              {
                msgID = MSGID_LISTBACKENDS_DN_BELOW_BASE;
                message = getMessage(msgID, dn.toString(), parentDN.toString(),
                                     id);
                out.println(message);
                break;
              }
            }

            parentDN = parentDN.getParent();
          }
        }
        else
        {
          int    msgID   = MSGID_LISTBACKENDS_BASE_FOR_ID;
          String message = getMessage(msgID, dn.toString(), id);
          out.println(message);
        }
      }
    }
    else
    {
      LinkedList<String> backendIDs;
      if (backendID.isPresent())
      {
        backendIDs = backendID.getValues();
      }
      else
      {
        backendIDs = new LinkedList<String>(backends.keySet());
      }

      // Figure out the length of the longest backend ID and base DN defined in
      // the server.  We'll use that information to try to align the output.
      String backendIDLabel  = getMessage(MSGID_LISTBACKENDS_LABEL_BACKEND_ID);
      String baseDNLabel     = getMessage(MSGID_LISTBACKENDS_LABEL_BASE_DN);
      int    backendIDLength = 10;
      int    baseDNLength    = 7;

      Iterator<String> iterator = backendIDs.iterator();
      while (iterator.hasNext())
      {
        String id = iterator.next();
        TreeSet<DN> baseDNs = backends.get(id);
        if (baseDNs == null)
        {
          int    msgID   = MSGID_LISTBACKENDS_NO_SUCH_BACKEND;
          String message = getMessage(msgID, id);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          iterator.remove();
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
        int    msgID   = MSGID_LISTBACKENDS_NO_VALID_BACKENDS;
        String message = getMessage(msgID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }

      // Print the header line and the separator line.
      out.print(backendIDLabel);
      for (int i=backendIDLabel.length(); i < backendIDLength+2; i++)
      {
        out.print(" ");
      }
      out.println(baseDNLabel);

      for (int i=0; i < backendIDLength; i++)
      {
        out.print("-");
      }
      out.print("  ");

      for (int i=0; i < baseDNLength; i++)
      {
        out.print("-");
      }
      out.println();


      // Iterate through the backends and the base DNs for each backend.
      for (String id : backendIDs)
      {
        out.print(id);
        for (int i=id.length(); i < backendIDLength+2; i++)
        {
          out.print(" ");
        }

        TreeSet<DN> baseDNs = backends.get(id);
        Iterator<DN> dnIterator = baseDNs.iterator();
        out.println(dnIterator.next().toString());
        while (dnIterator.hasNext())
        {
          for (int i=0; i < backendIDLength+2; i++)
          {
            out.print(" ");
          }
          out.println(dnIterator.next().toString());
        }
      }
    }


    // If we've gotten here, then everything completed successfully.
    return 0;
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
  private static TreeMap<String,TreeSet<DN>> getBackends()
          throws ConfigException
  {
    // Get the base entry for all backend configuration.
    DN backendBaseDN = null;
    try
    {
      backendBaseDN = DN.decode(DN_BACKEND_BASE);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_LISTBACKENDS_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE, de.getErrorMessage());
      throw new ConfigException(msgID, message, de);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LISTBACKENDS_CANNOT_DECODE_BACKEND_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }

    ConfigEntry baseEntry = null;
    try
    {
      baseEntry = DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_LISTBACKENDS_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE, ce.getMessage());
      throw new ConfigException(msgID, message, ce);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LISTBACKENDS_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE,
                                  stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }


    // Iterate through the immediate children, attempting to parse them as
    // backends.
    TreeMap<String,TreeSet<DN>> backendMap = new TreeMap<String,TreeSet<DN>>();
    for (ConfigEntry configEntry : baseEntry.getChildren().values())
    {
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
        StringConfigAttribute idStub =
             new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID),
                                       true, false, true);
        StringConfigAttribute idAttr =
             (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
        if (idAttr == null)
        {
          continue;
        }
        else
        {
          backendID = idAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    ce.getMessage());
        throw new ConfigException(msgID, message, ce);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        throw new ConfigException(msgID, message, e);
      }


      // Get the base DN attribute from the entry.  If there isn't one, then
      // just skip this entry.
      TreeSet<DN> baseDNs = new TreeSet<DN>();
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
        DNConfigAttribute baseDNStub =
             new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID),
                                   true, true, true);
        DNConfigAttribute baseDNAttr =
             (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
        if (baseDNAttr != null)
        {
          baseDNs.addAll(baseDNAttr.activeValues());
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LISTBACKENDS_CANNOT_DETERMINE_BASES_FOR_BACKEND;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                    stackTraceToSingleLineString(e));
        throw new ConfigException(msgID, message, e);
      }

      backendMap.put(backendID, baseDNs);
    }

    return backendMap;
  }
}

