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



import static org.opends.messages.DSConfigMessages.*;

import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.opends.messages.Message;
import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TablePrinter;
import org.opends.server.util.table.TextTablePrinter;



/**
 * A sub-command handler which is used to retrieve the properties of a
 * managed object.
 * <p>
 * This sub-command implements the various get-xxx-prop sub-commands.
 */
final class GetPropSubCommandHandler extends SubCommandHandler {

  /**
   * Creates a new get-xxx-prop sub-command for an instantiable
   * relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param path
   *          The parent managed object path.
   * @param r
   *          The instantiable relation.
   * @return Returns the new get-xxx-prop sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static GetPropSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
    return new GetPropSubCommandHandler(parser, path.child(r, "DUMMY"), r);
  }



  /**
   * Creates a new get-xxx-prop sub-command for an optional relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param path
   *          The parent managed object path.
   * @param r
   *          The optional relation.
   * @return Returns the new get-xxx-prop sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static GetPropSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      OptionalRelationDefinition<?, ?> r) throws ArgumentException {
    return new GetPropSubCommandHandler(parser, path.child(r), r);
  }



  /**
   * Creates a new get-xxx-prop sub-command for a singleton relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param path
   *          The parent managed object path.
   * @param r
   *          The singleton relation.
   * @return Returns the new get-xxx-prop sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static GetPropSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      SingletonRelationDefinition<?, ?> r) throws ArgumentException {
    return new GetPropSubCommandHandler(parser, path.child(r), r);
  }

  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The path of the managed object.
  private final ManagedObjectPath<?, ?> path;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;



  // Private constructor.
  private GetPropSubCommandHandler(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      RelationDefinition<?, ?> r) throws ArgumentException {
    this.path = path;

    // Create the sub-command.
    String name = "get-" + r.getName() + "-prop";
    Message message = INFO_DSCFG_DESCRIPTION_SUBCMD_GETPROP.get(r
        .getChildDefinition().getUserFriendlyName());
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null, message);

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, path, false);

    // Register common arguments.
    registerPropertyNameArgument(this.subCommand);
    registerRecordModeArgument(this.subCommand);
    registerUnitSizeArgument(this.subCommand);
    registerUnitTimeArgument(this.subCommand);

    // Register the tags associated with the child managed objects.
    addTags(path.getManagedObjectDefinition().getAllTags());
  }



  /**
   * Gets the relation definition associated with the type of
   * component that this sub-command handles.
   *
   * @return Returns the relation definition associated with the type
   *         of component that this sub-command handles.
   */
  public RelationDefinition<?, ?> getRelationDefinition() {
    return path.getRelationDefinition();
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
  public MenuResult<Integer> run(ConsoleApplication app,
      ManagementContextFactory factory) throws ArgumentException,
      ClientException, CLIException {
    // Get the property names.
    Set<String> propertyNames = getPropertyNames();
    PropertyValuePrinter valuePrinter = new PropertyValuePrinter(getSizeUnit(),
        getTimeUnit(), app.isScriptFriendly());

    // Get the naming argument values.
    List<String> names = getNamingArgValues(app, namingArgs);

    // Get the targeted managed object.
    Message ufn = path.getRelationDefinition().getUserFriendlyName();
    ManagementContext context = factory.getManagementContext(app);
    MenuResult<ManagedObject<?>> result;
    try {
      result = getManagedObject(app, context, path, names);
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_AUTHZ.get(ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (DefinitionDecodingException e) {
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_DDE.get(ufn, ufn, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (ManagedObjectDecodingException e) {
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_MODE.get(ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_CE.get(ufn, e.getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msg);
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_CME.get(ufn);
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (ManagedObjectNotFoundException e) {
       Message msg = ERR_DSCFG_ERROR_GET_CHILD_MONFE.get(ufn);
      throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
    }

    if (result.isQuit()) {
      return MenuResult.quit();
    } else if (result.isCancel()) {
      return MenuResult.cancel();
    }

    // Validate the property names.
    ManagedObject<?> child = result.getValue();
    ManagedObjectDefinition<?, ?> d = child.getManagedObjectDefinition();
    Collection<PropertyDefinition<?>> pdList;
    if (propertyNames.isEmpty()) {
      pdList = d.getAllPropertyDefinitions();
    } else {
      pdList = new LinkedList<PropertyDefinition<?>>();
      for (String name : propertyNames) {
        try {
          pdList.add(d.getPropertyDefinition(name));
        } catch (IllegalArgumentException e) {
          throw ArgumentExceptionFactory.unknownProperty(d, name);
        }
      }
    }

    // Now output its properties.
    TableBuilder builder = new TableBuilder();
    builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_NAME.get());
    builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_VALUE.get());
    builder.addSortKey(0);
    for (PropertyDefinition<?> pd : pdList) {
      if (pd.hasOption(PropertyOption.HIDDEN)) {
        continue;
      }

      if (!app.isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
        continue;
      }

      if (propertyNames.isEmpty() || propertyNames.contains(pd.getName())) {
        displayProperty(app, builder, child, pd, valuePrinter);
      }
    }

    PrintStream out = app.getOutputStream();
    if (app.isScriptFriendly()) {
      TablePrinter printer = createScriptFriendlyTablePrinter(out);
      builder.print(printer);
    } else {
      TextTablePrinter printer = new TextTablePrinter(out);
      printer.setColumnSeparator(":");
      printer.setColumnWidth(1, 0);
      builder.print(printer);
    }

    return MenuResult.success(0);
  }



  // Display the set of values associated with a property.
  private <T> void displayProperty(final ConsoleApplication app,
      TableBuilder builder, ManagedObject<?> mo, PropertyDefinition<T> pd,
      PropertyValuePrinter valuePrinter) {
    SortedSet<T> values = mo.getPropertyValues(pd);
    if (values.isEmpty()) {
      // There are no values or default values. Display the default
      // behavior for alias values.
      DefaultBehaviorProviderVisitor<T, Message, Void> visitor =
        new DefaultBehaviorProviderVisitor<T, Message, Void>() {

        public Message visitAbsoluteInherited(
            AbsoluteInheritedDefaultBehaviorProvider<T> d, Void p) {
          // Should not happen - inherited default values are
          // displayed as normal values.
          throw new IllegalStateException();
        }



        public Message visitAlias(AliasDefaultBehaviorProvider<T> d, Void p) {
          if (app.isVerbose()) {
            return d.getSynopsis();
          } else {
            return null;
          }
        }



        public Message visitDefined(DefinedDefaultBehaviorProvider<T> d,
            Void p) {
          // Should not happen - real default values are displayed as
          // normal values.
          throw new IllegalStateException();
        }



        public Message visitRelativeInherited(
            RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
          // Should not happen - inherited default values are
          // displayed as normal values.
          throw new IllegalStateException();
        }



        public Message visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
            Void p) {
          return null;
        }
      };

      builder.startRow();
      builder.appendCell(pd.getName());

      Message content = pd.getDefaultBehaviorProvider().accept(visitor, null);
      if (content == null) {
        if (app.isScriptFriendly()) {
          builder.appendCell();
        } else {
          builder.appendCell("-");
        }
      } else {
        builder.appendCell(content);
      }
    } else {
      if (isRecordMode()) {
        for (T value : values) {
          builder.startRow();
          builder.appendCell(pd.getName());
          builder.appendCell(valuePrinter.print(pd, value));
        }
      } else {
        builder.startRow();
        builder.appendCell(pd.getName());

        if (app.isScriptFriendly()) {
          for (T value : values) {
            builder.appendCell(valuePrinter.print(pd, value));
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
  }
}
