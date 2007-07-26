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



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;

import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

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
   * @param app
   *          The console application.
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
  public static GetPropSubCommandHandler create(ConsoleApplication app,
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
    return new GetPropSubCommandHandler(app, parser, path.child(r, "DUMMY"), r);
  }



  /**
   * Creates a new get-xxx-prop sub-command for an optional relation.
   *
   * @param app
   *          The console application.
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
  public static GetPropSubCommandHandler create(ConsoleApplication app,
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      OptionalRelationDefinition<?, ?> r) throws ArgumentException {
    return new GetPropSubCommandHandler(app, parser, path.child(r), r);
  }



  /**
   * Creates a new get-xxx-prop sub-command for a singleton relation.
   *
   * @param app
   *          The console application.
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
  public static GetPropSubCommandHandler create(ConsoleApplication app,
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      SingletonRelationDefinition<?, ?> r) throws ArgumentException {
    return new GetPropSubCommandHandler(app, parser, path.child(r), r);
  }

  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The path of the managed object.
  private final ManagedObjectPath<?, ?> path;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;



  // Private constructor.
  private GetPropSubCommandHandler(ConsoleApplication app,
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      RelationDefinition<?, ?> r) throws ArgumentException {
    super(app);

    this.path = path;

    // Create the sub-command.
    String name = "get-" + r.getName() + "-prop";
    int descriptionID = MSGID_DSCFG_DESCRIPTION_SUBCMD_GETPROP;
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        descriptionID, r.getChildDefinition().getUserFriendlyName());

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, path, false);

    // Register common arguments.
    registerPropertyNameArgument(this.subCommand);
    registerAdvancedModeArgument(this.subCommand,
        MSGID_DSCFG_DESCRIPTION_ADVANCED_GET, r.getUserFriendlyName());
    registerRecordModeArgument(this.subCommand);
    registerUnitSizeArgument(this.subCommand);
    registerUnitTimeArgument(this.subCommand);

    // Register the tags associated with the child managed objects.
    addTags(path.getManagedObjectDefinition().getAllTags());
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
  public int run() throws ArgumentException, ClientException {
    // Get the property names.
    Set<String> propertyNames = getPropertyNames();
    PropertyValuePrinter valuePrinter = new PropertyValuePrinter(getSizeUnit(),
        getTimeUnit(), getConsoleApplication().isScriptFriendly());

    // Get the naming argument values.
    List<String> names = getNamingArgValues(namingArgs);

    // Get the targeted managed object.
    ManagedObject<?> child;
    try {
      child = getManagedObject(path, names);
    } catch (AuthorizationException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_CHILD_AUTHZ;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
          msgID, msg);
    } catch (DefinitionDecodingException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_CHILD_DDE;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn, ufn, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msgID, msg);
    } catch (ManagedObjectDecodingException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_CHILD_MODE;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msgID, msg);
    } catch (CommunicationException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_CHILD_CE;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn, e.getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
          msg);
    } catch (ConcurrentModificationException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_CHILD_CME;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn);
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msgID,
          msg);
    } catch (ManagedObjectNotFoundException e) {
      int msgID = MSGID_DSCFG_ERROR_GET_CHILD_MONFE;
      String ufn = path.getManagedObjectDefinition().getUserFriendlyName();
      String msg = getMessage(msgID, ufn);
      throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msgID, msg);
    }

    // Validate the property names.
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
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_NAME));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_VALUE));
    builder.addSortKey(0);
    for (PropertyDefinition<?> pd : pdList) {
      if (pd.hasOption(PropertyOption.HIDDEN)) {
        continue;
      }

      if (!isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
        continue;
      }

      if (propertyNames.isEmpty() || propertyNames.contains(pd.getName())) {
        displayProperty(builder, child, pd, valuePrinter);
      }
    }

    PrintStream out = getConsoleApplication().getOutputStream();
    if (getConsoleApplication().isScriptFriendly()) {
      TablePrinter printer = createScriptFriendlyTablePrinter(out);
      builder.print(printer);
    } else {
      TextTablePrinter printer = new TextTablePrinter(out);
      printer.setColumnSeparator(":");
      printer.setColumnWidth(1, 0);
      builder.print(printer);
    }

    return 0;
  }



  // Display the set of values associated with a property.
  private <T> void displayProperty(TableBuilder builder, ManagedObject<?> mo,
      PropertyDefinition<T> pd, PropertyValuePrinter valuePrinter) {
    SortedSet<T> values = mo.getPropertyValues(pd);
    if (values.isEmpty()) {
      // There are no values or default values. Display the default
      // behavior for alias values.
      DefaultBehaviorProviderVisitor<T, String, Void> visitor =
        new DefaultBehaviorProviderVisitor<T, String, Void>() {

        public String visitAbsoluteInherited(
            AbsoluteInheritedDefaultBehaviorProvider<T> d, Void p) {
          // Should not happen - inherited default values are
          // displayed as normal values.
          throw new IllegalStateException();
        }



        public String visitAlias(AliasDefaultBehaviorProvider<T> d, Void p) {
          if (getConsoleApplication().isVerbose()) {
            return d.getSynopsis();
          } else {
            return null;
          }
        }



        public String visitDefined(DefinedDefaultBehaviorProvider<T> d,
            Void p) {
          // Should not happen - real default values are displayed as
          // normal values.
          throw new IllegalStateException();
        }



        public String visitRelativeInherited(
            RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
          // Should not happen - inherited default values are
          // displayed as normal values.
          throw new IllegalStateException();
        }



        public String visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
            Void p) {
          return null;
        }
      };

      builder.startRow();
      builder.appendCell(pd.getName());

      String content = pd.getDefaultBehaviorProvider().accept(visitor, null);
      if (content == null) {
        if (getConsoleApplication().isScriptFriendly()) {
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

        if (getConsoleApplication().isScriptFriendly()) {
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
