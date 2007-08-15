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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsconfig;
import org.opends.messages.Message;



import static org.opends.messages.ToolMessages.*;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TablePrinter;
import org.opends.server.util.table.TextTablePrinter;



/**
 * A sub-command handler which is used to list existing managed
 * objects.
 * <p>
 * This sub-command implements the various list-xxx sub-commands.
 */
final class ListSubCommandHandler extends SubCommandHandler {

  /**
   * Creates a new list-xxx sub-command for an instantiable relation.
   *
   * @param app
   *          The console application.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The instantiable relation.
   * @return Returns the new list-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static ListSubCommandHandler create(ConsoleApplication app,
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
    return new ListSubCommandHandler(app, parser, p, r, r.getPluralName(), r
        .getUserFriendlyPluralName());
  }



  /**
   * Creates a new list-xxx sub-command for an optional relation.
   *
   * @param app
   *          The console application.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The optional relation.
   * @return Returns the new list-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static ListSubCommandHandler create(ConsoleApplication app,
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      OptionalRelationDefinition<?, ?> r) throws ArgumentException {
    return new ListSubCommandHandler(app, parser, p, r, r.getName(), r
        .getUserFriendlyName());
  }

  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The path of the parent managed object.
  private final ManagedObjectPath<?, ?> path;

  // The relation which should be listed.
  private final RelationDefinition<?, ?> relation;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;



  // Private constructor.
  private ListSubCommandHandler(ConsoleApplication app,
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      RelationDefinition<?, ?> r, String rname, Message rufn)
      throws ArgumentException {
    super(app);

    this.path = p;
    this.relation = r;

    // Create the sub-command.
    String name = "list-" + rname;
    Message desc = INFO_DSCFG_DESCRIPTION_SUBCMD_LIST.get(rufn);
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null, desc);

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, path, false);

    // Register arguments.
    registerPropertyNameArgument(this.subCommand);
    registerUnitSizeArgument(this.subCommand);
    registerUnitTimeArgument(this.subCommand);

    // Register the tags associated with the child managed objects.
    addTags(relation.getChildDefinition().getAllTags());
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SubCommand getSubCommand() {
    return subCommand;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int run()
      throws ArgumentException, ClientException {
    // Get the property names.
    Set<String> propertyNames = getPropertyNames();

    if (propertyNames.isEmpty()) {
      // Use a default set of properties.
      propertyNames = CLIProfile.getInstance().getDefaultListPropertyNames(
          relation);
    }

    PropertyValuePrinter valuePrinter = new PropertyValuePrinter(getSizeUnit(),
        getTimeUnit(), getConsoleApplication().isScriptFriendly());

    // Get the naming argument values.
    List<String> names = getNamingArgValues(namingArgs);

    Message ufn;
    if (relation instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<?, ?> irelation =
        (InstantiableRelationDefinition<?, ?>) relation;
      ufn = irelation.getUserFriendlyPluralName();
    } else {
      ufn = relation.getUserFriendlyName();
    }

    // List the children.
    ManagedObject<?> parent;
    try {
      parent = getManagedObject(path, names);
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_LIST_AUTHZ.get(ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
          msg);
    } catch (DefinitionDecodingException e) {

      ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_DDE.get(ufn, ufn, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (ManagedObjectDecodingException e) {

      ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_MODE.get(ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_LIST_CE.get(ufn, e.getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msg);
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_LIST_CME.get(ufn);
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (ManagedObjectNotFoundException e) {

      ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_MONFE.get(ufn);
      throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
    }

    SortedMap<String, ManagedObject<?>> children =
      new TreeMap<String, ManagedObject<?>>();
    if (relation instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<?, ?> irelation =
        (InstantiableRelationDefinition<?, ?>) relation;
      try {
        for (String s : parent.listChildren(irelation)) {
          try {
            children.put(s, parent.getChild(irelation, s));
          } catch (ManagedObjectNotFoundException e) {
            // Ignore - as it's been removed since we did the list.
          }
        }
      } catch (DefinitionDecodingException e) {
        // FIXME: just output this as a warnings (incl. the name) but
        // continue.
        Message msg = ERR_DSCFG_ERROR_LIST_DDE.get(ufn, ufn, ufn);
        throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
      } catch (ManagedObjectDecodingException e) {
        // FIXME: just output this as a warnings (incl. the name) but
        // continue.
        Message msg = ERR_DSCFG_ERROR_LIST_MODE.get(ufn);
        throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
      } catch (AuthorizationException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_AUTHZ.get(ufn);
        throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            msg);
      } catch (ConcurrentModificationException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_CME.get(ufn);
        throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
      } catch (CommunicationException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_CE.get(ufn, e.getMessage());
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
            msg);
      }
    } else if (relation instanceof OptionalRelationDefinition) {
      OptionalRelationDefinition<?, ?> orelation =
        (OptionalRelationDefinition<?, ?>) relation;
      try {
        if (parent.hasChild(orelation)) {
          ManagedObject<?> child = parent.getChild(orelation);
          children.put(child.getManagedObjectDefinition().getName(), child);
        } else {
          // Indicate that the managed object does not exist.
          throw new ManagedObjectNotFoundException();
        }
      } catch (AuthorizationException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_AUTHZ.get(ufn);
        throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            msg);
      } catch (DefinitionDecodingException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_DDE.get(ufn, ufn, ufn);
        throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
      } catch (ManagedObjectDecodingException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_MODE.get(ufn);
        throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
      } catch (ConcurrentModificationException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_CME.get(ufn);
        throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
      } catch (CommunicationException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_CE.get(ufn, e.getMessage());
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
            msg);
      } catch (ManagedObjectNotFoundException e) {
        Message msg = ERR_DSCFG_ERROR_LIST_MONFE.get(ufn);
        throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
      }
    }

    // Output the results.
    if (getConsoleApplication().isScriptFriendly()) {
      // Output just the names of the children.
      PrintStream out = getConsoleApplication().getOutputStream();
      for (String name : children.keySet()) {
        out.println(name);
      }
    } else {
      // Create a table of their properties.
      TableBuilder builder = new TableBuilder();
      builder.appendHeading(relation.getUserFriendlyName());
      builder
          .appendHeading(INFO_DSCFG_HEADING_COMPONENT_TYPE.get());
      if (!propertyNames.isEmpty()) {
      }
      for (String propertyName : propertyNames) {
        builder.appendHeading(Message.raw(propertyName));
      }
      builder.addSortKey(0);

      String baseType = relation.getName();
      String typeSuffix = "-" + baseType;
      for (String name : children.keySet()) {
        ManagedObject<?> child = children.get(name);
        ManagedObjectDefinition<?, ?> d = child.getManagedObjectDefinition();

        // First output the name.
        builder.startRow();
        builder.appendCell(name);

        // Output the managed object type in the form used in
        // create-xxx commands.
        String childType = d.getName();
        if (baseType.equals(childType)) {
          builder.appendCell("generic");
        } else if (childType.endsWith(typeSuffix)) {
          builder.appendCell(childType.substring(0, childType.length()
              - typeSuffix.length()));
        } else {
          builder.appendCell(childType);
        }

        // Now any requested properties.
        for (String propertyName : propertyNames) {
          try {
            PropertyDefinition<?> pd = d.getPropertyDefinition(propertyName);
            displayProperty(builder, child, pd, valuePrinter);
          } catch (IllegalArgumentException e) {
            // Assume this child managed object does not support this
            // property.
            if (getConsoleApplication().isScriptFriendly()) {
              builder.appendCell();
            } else {
              builder.appendCell("-");
            }
          }
        }
      }

      PrintStream out = getConsoleApplication().getOutputStream();
      if (getConsoleApplication().isScriptFriendly()) {
        TablePrinter printer = createScriptFriendlyTablePrinter(out);
        builder.print(printer);
      } else {
        TextTablePrinter printer = new TextTablePrinter(out);
        printer.setColumnSeparator(":");
        builder.print(printer);
      }
    }

    return 0;
  }



  // Display the set of values associated with a property.
  private <T> void displayProperty(TableBuilder builder, ManagedObject<?> mo,
      PropertyDefinition<T> pd, PropertyValuePrinter valuePrinter) {
    SortedSet<T> values = mo.getPropertyValues(pd);
    if (values.isEmpty()) {
      if (getConsoleApplication().isScriptFriendly()) {
        builder.appendCell();
      } else {
        builder.appendCell("-");
      }
    } else {
      StringBuilder sb = new StringBuilder();
      boolean isFirst = true;
      for (T value : values) {
        if (!isFirst) {
          sb.append(", ");
        }
        sb.append(valuePrinter.print(pd, value));
        isFirst = false;
      }

      builder.appendCell(sb.toString());
    }
  }
}
