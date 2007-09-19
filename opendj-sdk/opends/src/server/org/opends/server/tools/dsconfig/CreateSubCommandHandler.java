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
import static org.opends.server.tools.dsconfig.ArgumentExceptionFactory.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.messages.Message;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.HelpCallback;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.cli.ValidationCallback;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;



/**
 * A sub-command handler which is used to create new managed objects.
 * <p>
 * This sub-command implements the various create-xxx sub-commands.
 *
 * @param <C>
 *          The type of managed object which can be created.
 * @param <S>
 *          The type of server managed object which can be created.
 */
final class CreateSubCommandHandler<C extends ConfigurationClient,
    S extends Configuration> extends SubCommandHandler {

  /**
   * A property provider which uses the command-line arguments to
   * provide initial property values.
   */
  private static class MyPropertyProvider implements PropertyProvider {

    // Decoded set of properties.
    private final Map<PropertyDefinition<?>, Collection<?>> properties =
      new HashMap<PropertyDefinition<?>, Collection<?>>();



    /**
     * Create a new property provider using the provided set of
     * property value arguments.
     *
     * @param d
     *          The managed object definition.
     * @param namingPropertyDefinition
     *          The naming property definition if there is one.
     * @param args
     *          The property value arguments.
     * @throws ArgumentException
     *           If the property value arguments could not be parsed.
     */
    public MyPropertyProvider(ManagedObjectDefinition<?, ?> d,
        PropertyDefinition<?> namingPropertyDefinition, List<String> args)
        throws ArgumentException {
      for (String s : args) {
        // Parse the property "property:value".
        int sep = s.indexOf(':');

        if (sep < 0) {
          throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(s);
        }

        if (sep == 0) {
          throw ArgumentExceptionFactory.missingNameInPropertyArgument(s);
        }

        String propertyName = s.substring(0, sep);
        String value = s.substring(sep + 1, s.length());
        if (value.length() == 0) {
          throw ArgumentExceptionFactory.missingValueInPropertyArgument(s);
        }

        // Check the property definition.
        PropertyDefinition<?> pd;
        try {
          pd = d.getPropertyDefinition(propertyName);
        } catch (IllegalArgumentException e) {
          throw ArgumentExceptionFactory.unknownProperty(d, propertyName);
        }

        // Make sure that the user is not attempting to set the naming
        // property.
        if (pd.equals(namingPropertyDefinition)) {
          throw ArgumentExceptionFactory.unableToSetNamingProperty(d, pd);
        }

        // Add the value.
        addPropertyValue(d, pd, value);
      }
    }



    /**
     * Get the set of parsed property definitions that have values
     * specified.
     *
     * @return Returns the set of parsed property definitions that
     *         have values specified.
     */
    public Set<PropertyDefinition<?>> getProperties() {
      return properties.keySet();
    }



    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public <T> Collection<T> getPropertyValues(PropertyDefinition<T> d)
        throws IllegalArgumentException {
      Collection<T> values = (Collection<T>) properties.get(d);
      if (values == null) {
        return Collections.emptySet();
      } else {
        return values;
      }
    }



    // Add a single property value.
    @SuppressWarnings("unchecked")
    private <T> void addPropertyValue(ManagedObjectDefinition<?, ?> d,
        PropertyDefinition<T> pd, String s) throws ArgumentException {
      T value;
      try {
        value = pd.decodeValue(s);
      } catch (IllegalPropertyValueStringException e) {
        throw ArgumentExceptionFactory.adaptPropertyException(e, d);
      }

      Collection<T> values = (Collection<T>) properties.get(pd);
      if (values == null) {
        values = new LinkedList<T>();
      }
      values.add(value);

      if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
        PropertyException e = new PropertyIsSingleValuedException(pd);
        throw ArgumentExceptionFactory.adaptPropertyException(e, d);
      }

      properties.put(pd, values);
    }
  }



  /**
   * A help call-back which displays help about available component
   * types.
   */
  private final class TypeHelpCallback implements HelpCallback {

    /**
     * {@inheritDoc}
     */
    public void display(ConsoleApplication app) {
      // Create a table containing a description of each component
      // type.
      TableBuilder builder = new TableBuilder();

      builder.appendHeading(INFO_DSCFG_DESCRIPTION_CREATE_HELP_HEADING_TYPE
          .get());

      builder.appendHeading(INFO_DSCFG_DESCRIPTION_CREATE_HELP_HEADING_DESCR
          .get());

      boolean isFirst = true;
      for (ManagedObjectDefinition<?, ?> d : types.values()) {
        if (!isFirst) {
          builder.startRow();
          builder.startRow();
        } else {
          isFirst = false;
        }

        builder.startRow();
        builder.appendCell(d.getUserFriendlyName());
        builder.appendCell(d.getSynopsis());
        if (d.getDescription() != null) {
          builder.startRow();
          builder.startRow();
          builder.appendCell();
          builder.appendCell(d.getDescription());
        }
      }

      TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
      printer.setColumnWidth(1, 0);
      printer.setColumnSeparator(":");
      builder.print(printer);
      app.println();
      app.pressReturnToContinue();
    }
  }

  /**
   * The value for the -t argument which will be used for the most
   * generic managed object when it is instantiable.
   */
  private static final String GENERIC_TYPE = "generic";

  /**
   * The value for the long option set.
   */
  private static final String OPTION_DSCFG_LONG_SET = "set";

  /**
   * The value for the long option type.
   */
  private static final String OPTION_DSCFG_LONG_TYPE = "type";

  /**
   * The value for the short option property.
   */
  private static final Character OPTION_DSCFG_SHORT_SET = null;

  /**
   * The value for the short option type.
   */
  private static final Character OPTION_DSCFG_SHORT_TYPE = 't';



  /**
   * Creates a new create-xxx sub-command for an instantiable
   * relation.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param <S>
   *          The type of server managed object which can be created.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The instantiable relation.
   * @return Returns the new create-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
      CreateSubCommandHandler<C, S> create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      InstantiableRelationDefinition<C, S> r) throws ArgumentException {
    return new CreateSubCommandHandler<C, S>(parser, p, r, r
        .getNamingPropertyDefinition(), p.child(r, "DUMMY"));
  }



  /**
   * Creates a new create-xxx sub-command for an optional relation.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param <S>
   *          The type of server managed object which can be created.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The optional relation.
   * @return Returns the new create-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
      CreateSubCommandHandler<C, S> create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      OptionalRelationDefinition<C, S> r) throws ArgumentException {
    return new CreateSubCommandHandler<C, S>(parser, p, r, null, p.child(r));
  }



  /**
   * Create the provided managed object.
   *
   * @param app
   *          The console application.
   * @param context
   *          The management context.
   * @param mo
   *          The managed object to be created.
   * @return Returns a MenuResult.success() if the managed object was
   *         created successfully, or MenuResult.quit(), or
   *         MenuResult.cancel(), if the managed object was edited
   *         interactively and the user chose to quit or cancel.
   * @throws ClientException
   *           If an unrecoverable client exception occurred whilst
   *           interacting with the server.
   * @throws CLIException
   *           If an error occurred whilst interacting with the
   *           console.
   */
  public static MenuResult<Void> createManagedObject(ConsoleApplication app,
      ManagementContext context, ManagedObject<?> mo) throws ClientException,
      CLIException {
    ManagedObjectDefinition<?, ?> d = mo.getManagedObjectDefinition();
    Message ufn = d.getUserFriendlyName();

    while (true) {
      // Interactively set properties if applicable.
      if (app.isInteractive()) {
        SortedSet<PropertyDefinition<?>> properties =
          new TreeSet<PropertyDefinition<?>>();
        for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
          if (pd.hasOption(PropertyOption.HIDDEN)) {
            continue;
          }
          if (!app.isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
            continue;
          }
          properties.add(pd);
        }

        PropertyValueEditor editor = new PropertyValueEditor(app, context);
        MenuResult<Void> result = editor.edit(mo, properties, false);

        // Interactively enable/edit referenced components.
        if (result.isSuccess()) {
          result = checkReferences(app, context, mo);
          if (result.isAgain()) {
            // Edit again.
            continue;
          }
        }

        if (result.isQuit()) {
          if (!app.isMenuDrivenMode()) {
            // User chose to cancel any changes.
            Message msg = INFO_DSCFG_CONFIRM_CREATE_FAIL.get(ufn);
            app.printVerboseMessage(msg);
          }
          return MenuResult.quit();
        } else if (result.isCancel()) {
          return MenuResult.cancel();
        }
      }

      try {
        // Create the managed object.
        mo.commit();

        // Output success message.
        app.println();
        Message msg = INFO_DSCFG_CONFIRM_CREATE_SUCCESS.get(ufn);
        app.printVerboseMessage(msg);

        return MenuResult.success();
      } catch (MissingMandatoryPropertiesException e) {
        if (app.isInteractive()) {
          // If interactive, give the user the chance to fix the
          // problems.
          app.println();
          displayMissingMandatoryPropertyException(app, e);
          app.println();
          if (!app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
            return MenuResult.cancel();
          }
        } else {
          throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, e
              .getMessageObject(), e);
        }
      } catch (AuthorizationException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
        throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            msg);
      } catch (ConcurrentModificationException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(ufn);
        throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
      } catch (OperationRejectedException e) {
        if (app.isInteractive()) {
          // If interactive, give the user the chance to fix the
          // problems.
          app.println();
          displayOperationRejectedException(app, e);
          app.println();
          if (!app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
            return MenuResult.cancel();
          }
        } else {
          throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, e
              .getMessageObject(), e);
        }
      } catch (CommunicationException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e.getMessage());
        throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
      } catch (ManagedObjectAlreadyExistsException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_MOAEE.get(ufn);
        throw new ClientException(LDAPResultCode.ENTRY_ALREADY_EXISTS, msg);
      }
    }
  }



  // Check that any referenced components are enabled if
  // required.
  private static MenuResult<Void> checkReferences(ConsoleApplication app,
      ManagementContext context, ManagedObject<?> mo) throws ClientException,
      CLIException {
    ManagedObjectDefinition<?, ?> d = mo.getManagedObjectDefinition();
    Message ufn = d.getUserFriendlyName();

    for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
      if (pd instanceof AggregationPropertyDefinition) {
        AggregationPropertyDefinition<?, ?> apd =
          (AggregationPropertyDefinition<?, ?>) pd;

        // Skip this aggregation if it doesn't have an enable
        // property.
        BooleanPropertyDefinition tpd = apd
            .getTargetEnabledPropertyDefinition();
        if (tpd == null) {
          continue;
        }

        // Skip this aggregation if this managed object's enable
        // properties are not all true.
        boolean needsEnabling = true;
        for (BooleanPropertyDefinition bpd : apd
            .getSourceEnabledPropertyDefinitions()) {
          if (!mo.getPropertyValue(bpd)) {
            needsEnabling = false;
            break;
          }
        }
        if (!needsEnabling) {
          continue;
        }

        // The referenced component(s) must be enabled.
        for (String name : mo.getPropertyValues(apd)) {
          ManagedObjectPath<?, ?> path = apd.getChildPath(name);
          Message rufn = path.getManagedObjectDefinition()
              .getUserFriendlyName();
          ManagedObject<?> ref;
          try {
            ref = context.getManagedObject(path);
          } catch (AuthorizationException e) {
            Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
            throw new ClientException(
                LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
          } catch (DefinitionDecodingException e) {
            Message msg = ERR_DSCFG_ERROR_GET_CHILD_DDE.get(rufn, rufn, rufn);
            throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
          } catch (ManagedObjectDecodingException e) {
            // FIXME: should not abort here. Instead, display the
            // errors (if verbose) and apply the changes to the
            // partial managed object.
            Message msg = ERR_DSCFG_ERROR_GET_CHILD_MODE.get(rufn);
            throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg, e);
          } catch (CommunicationException e) {
            Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e.getMessage());
            throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
          } catch (ManagedObjectNotFoundException e) {
            Message msg = ERR_DSCFG_ERROR_GET_CHILD_MONFE.get(rufn);
            throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
          }

          while (!ref.getPropertyValue(tpd)) {
            boolean isBadReference = true;
            app.println();
            if (app.confirmAction(
                INFO_EDITOR_PROMPT_ENABLED_REFERENCED_COMPONENT.get(rufn, name,
                    ufn), true)) {
              ref.setPropertyValue(tpd, true);
              try {
                ref.commit();
                isBadReference = false;
              } catch (MissingMandatoryPropertiesException e) {
                // Give the user the chance to fix the problems.
                app.println();
                displayMissingMandatoryPropertyException(app, e);
                app.println();
                if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT.get(rufn), true)) {
                  // FIXME: edit the properties of the referenced
                  // object.
                  MenuResult<Void> result = SetPropSubCommandHandler
                      .modifyManagedObject(app, context, ref);
                  if (result.isQuit()) {
                    return result;
                  } else if (result.isSuccess()) {
                    // The referenced component was modified
                    // successfully, but may still be disabled.
                    isBadReference = false;
                  }
                }
              } catch (AuthorizationException e) {
                Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
                throw new ClientException(
                    LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
              } catch (ConcurrentModificationException e) {
                Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(ufn);
                throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION,
                    msg);
              } catch (OperationRejectedException e) {
                // Give the user the chance to fix the problems.
                app.println();
                displayOperationRejectedException(app, e);
                app.println();
                if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT.get(rufn), true)) {
                  MenuResult<Void> result = SetPropSubCommandHandler
                      .modifyManagedObject(app, context, ref);
                  if (result.isQuit()) {
                    return result;
                  } else if (result.isSuccess()) {
                    // The referenced component was modified
                    // successfully, but may still be disabled.
                    isBadReference = false;
                  }
                }
              } catch (CommunicationException e) {
                Message msg = ERR_DSCFG_ERROR_CREATE_CE
                    .get(ufn, e.getMessage());
                throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
              } catch (ManagedObjectAlreadyExistsException e) {
                // Should never happen.
                throw new IllegalStateException(e);
              }
            }

            // If the referenced component is still disabled because
            // the user refused to modify it, then give the used the
            // option of editing the referencing component.
            if (isBadReference) {
              app.println();
              app.println(ERR_SET_REFERENCED_COMPONENT_DISABLED.get(ufn, rufn));
              app.println();
              if (app
                  .confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
                return MenuResult.again();
              } else {
                return MenuResult.cancel();
              }
            }

            // If the referenced component is now enabled, then drop out.
            if (ref.getPropertyValue(tpd)) {
              break;
            }
          }
        }
      }
    }

    return MenuResult.success();
  }



  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The optional naming property definition.
  private final PropertyDefinition<?> namingPropertyDefinition;

  // The path of the parent managed object.
  private final ManagedObjectPath<?, ?> path;

  // The argument which should be used to specify zero or more
  // property values.
  private final StringArgument propertySetArgument;

  // The relation which should be used for creating children.
  private final RelationDefinition<C, S> relation;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;

  // The argument which should be used to specify the type of managed
  // object to be created.
  private final StringArgument typeArgument;

  // The set of instantiable managed object definitions and their
  // associated type option value.
  private final SortedMap<String,
      ManagedObjectDefinition<? extends C, ? extends S>> types;

  // The syntax of the type argument.
  private final String typeUsage;



  // Common constructor.
  private CreateSubCommandHandler(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      RelationDefinition<C, S> r, PropertyDefinition<?> pd,
      ManagedObjectPath<?, ?> c) throws ArgumentException {
    this.path = p;
    this.relation = r;
    this.namingPropertyDefinition = pd;

    // Create the sub-command.
    String name = "create-" + r.getName();
    Message description = INFO_DSCFG_DESCRIPTION_SUBCMD_CREATE.get(r
        .getChildDefinition().getUserFriendlyPluralName());
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        description);

    // Create the -t argument which is used to specify the type of
    // managed object to be created.
    this.types = getSubTypes(r.getChildDefinition());

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, c, true);

    // Create the --property argument which is used to specify
    // property values.
    this.propertySetArgument = new StringArgument(OPTION_DSCFG_LONG_SET,
        OPTION_DSCFG_SHORT_SET, OPTION_DSCFG_LONG_SET, false, true, true,
        "{PROP:VALUE}", null, null, INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
    this.subCommand.addArgument(this.propertySetArgument);

    // Build the -t option usage.
    StringBuilder builder = new StringBuilder();
    boolean isFirst = true;
    for (String s : types.keySet()) {
      if (!isFirst) {
        builder.append(" | ");
      }
      builder.append(s);
      isFirst = false;
    }
    this.typeUsage = builder.toString();

    if (!types.containsKey(GENERIC_TYPE)) {
      // The option is mandatory when non-interactive.
      this.typeArgument = new StringArgument("type", OPTION_DSCFG_SHORT_TYPE,
          OPTION_DSCFG_LONG_TYPE, false, false, true, "{TYPE}", null, null,
          INFO_DSCFG_DESCRIPTION_TYPE.get(r.getChildDefinition()
              .getUserFriendlyName(), typeUsage));
    } else {
      // The option has a sensible default "generic".
      this.typeArgument = new StringArgument("type", OPTION_DSCFG_SHORT_TYPE,
          OPTION_DSCFG_LONG_TYPE, false, false, true, "{TYPE}", GENERIC_TYPE,
          null, INFO_DSCFG_DESCRIPTION_TYPE_DEFAULT.get(r.getChildDefinition()
              .getUserFriendlyName(), GENERIC_TYPE, typeUsage));

      // Hide the option if it defaults to generic and generic is the
      // only possible value.
      if (types.size() == 1) {
        this.typeArgument.setHidden(true);
      }
    }
    this.subCommand.addArgument(this.typeArgument);

    // Register the tags associated with the child managed objects.
    addTags(relation.getChildDefinition().getAllTags());
  }



  /**
   * Gets the relation definition associated with the type of
   * component that this sub-command handles.
   *
   * @return Returns the relation definition associated with the type
   *         of component that this sub-command handles.
   */
  public RelationDefinition<?, ?> getRelationDefinition() {
    return relation;
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
    // Determine the type of managed object to be created.
    String typeName;

    if (!typeArgument.isPresent()) {
      if (app.isInteractive()) {
        // Let the user choose.

        // If there is only one choice then return immediately.
        if (types.size() == 1) {
          typeName = types.keySet().iterator().next();
        } else {
          MenuBuilder<String> builder = new MenuBuilder<String>(app);
          Message msg = INFO_DSCFG_CREATE_TYPE_PROMPT.get(relation
              .getChildDefinition().getUserFriendlyName());
          builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);
          builder.setPrompt(msg);

          for (String type : types.keySet()) {
            ManagedObjectDefinition<?, ?> d = types.get(type);
            Message option = d.getUserFriendlyName();
            if (type.equals(GENERIC_TYPE)) {
              option = INFO_DSCFG_GENERIC_TYPE_OPTION.get(option);
            }
            builder.addNumberedOption(option, MenuResult.success(type));
          }
          builder.addHelpOption(new TypeHelpCallback());
          if (app.isMenuDrivenMode()) {
            builder.addCancelOption(true);
          }
          builder.addQuitOption();

          Menu<String> menu = builder.toMenu();
          app.println();
          app.println();
          MenuResult<String> result = menu.run();
          if (result.isSuccess()) {
            typeName = result.getValue();
          } else if (result.isCancel()) {
            return MenuResult.cancel();
          } else {
            // Must be quit.
            if (!app.isMenuDrivenMode()) {
              msg = INFO_DSCFG_CONFIRM_CREATE_FAIL.get(relation
                  .getUserFriendlyName());
              app.printVerboseMessage(msg);
            }
            return MenuResult.quit();
          }
        }
      } else if (typeArgument.getDefaultValue() != null) {
        typeName = typeArgument.getDefaultValue();
      } else {
        throw ArgumentExceptionFactory
            .missingMandatoryNonInteractiveArgument(typeArgument);
      }
    } else {
      typeName = typeArgument.getValue();
    }

    ManagedObjectDefinition<? extends C, ? extends S> d = types.get(typeName);
    if (d == null) {
      throw ArgumentExceptionFactory.unknownSubType(relation, typeName,
          typeUsage);
    }
    Message ufn = d.getUserFriendlyName();

    // Get the naming argument values.
    List<String> names = getNamingArgValues(app, namingArgs);

    // Encode the provided properties.
    List<String> propertyArgs = propertySetArgument.getValues();
    MyPropertyProvider provider = new MyPropertyProvider(d,
        namingPropertyDefinition, propertyArgs);

    // Add the child managed object.
    ManagementContext context = factory.getManagementContext(app);
    MenuResult<ManagedObject<?>> result;
    try {
      result = getManagedObject(app, context, path, names);
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (DefinitionDecodingException e) {
      Message pufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_DDE.get(pufn, pufn, pufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (ManagedObjectDecodingException e) {
      Message pufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_MODE.get(pufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg, e);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e
          .getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msg);
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(ufn);
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (ManagedObjectNotFoundException e) {
      Message pufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_MONFE.get(pufn);
      throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
    }

    if (result.isQuit()) {
      if (!app.isMenuDrivenMode()) {
        // User chose to cancel creation.
        Message msg = INFO_DSCFG_CONFIRM_CREATE_FAIL.get(ufn);
        app.printVerboseMessage(msg);
      }
      return MenuResult.quit();
    } else if (result.isCancel()) {
      // Must be menu driven, so no need for error message.
      return MenuResult.cancel();
    }

    ManagedObject<?> parent = result.getValue();
    ManagedObject<? extends C> child;
    List<DefaultBehaviorException> exceptions =
      new LinkedList<DefaultBehaviorException>();
    if (relation instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<C, S> irelation =
        (InstantiableRelationDefinition<C, S>) relation;
      String name = names.get(names.size() - 1);
      if (name == null) {
        if (app.isInteractive()) {
          child = createChildInteractively(app, parent, irelation,
              d, exceptions);
        } else {
          throw ArgumentExceptionFactory
              .missingMandatoryNonInteractiveArgument(namingArgs.get(names
                  .size() - 1));
        }
      } else {
        try {
          child = parent.createChild(irelation, d, name,
              exceptions);
        } catch (IllegalManagedObjectNameException e) {
          throw ArgumentExceptionFactory
              .adaptIllegalManagedObjectNameException(e, d);
        }
      }
    } else {
      OptionalRelationDefinition<C, S> orelation =
        (OptionalRelationDefinition<C, S>) relation;
      child = parent.createChild(orelation, d, exceptions);
    }

    // FIXME: display any default behavior exceptions in verbose
    // mode.

    // Set any properties specified on the command line.
    for (PropertyDefinition<?> pd : provider.getProperties()) {
      setProperty(child, provider, pd);
    }

    // Now the command line changes have been made, create the managed
    // object interacting with the user to fix any problems if
    // required.
    MenuResult<Void> result2 = createManagedObject(app, context, child);
    if (result2.isCancel()) {
      return MenuResult.cancel();
    } else if (result2.isQuit()) {
      return MenuResult.quit();
    } else {
      return MenuResult.success(0);
    }
  }



  // Interactively create the child by prompting for the name.
  private ManagedObject<? extends C> createChildInteractively(
      ConsoleApplication app, final ManagedObject<?> parent,
      final InstantiableRelationDefinition<C, S> irelation,
      final ManagedObjectDefinition<? extends C, ? extends S> d,
      final List<DefaultBehaviorException> exceptions) throws CLIException {
    ValidationCallback<ManagedObject<? extends C>> validator =
      new ValidationCallback<ManagedObject<? extends C>>() {

      public ManagedObject<? extends C> validate(ConsoleApplication app,
          String input) throws CLIException {
        ManagedObject<? extends C> child;

        // First attempt to create the child, this will guarantee that
        // the name is acceptable.
        try {
          child = parent.createChild(irelation, d, input, exceptions);
        } catch (IllegalManagedObjectNameException e) {
          CLIException ae = ArgumentExceptionFactory
              .adaptIllegalManagedObjectNameException(e, d);
          app.println();
          app.println(ae.getMessageObject());
          app.println();
          return null;
        }

        // Make sure that there are not any other children with the
        // same name.
        try {
          // Attempt to retrieve a child using this name.
          parent.getChild(irelation, input);
        } catch (AuthorizationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(irelation
              .getUserFriendlyName());
          throw new CLIException(msg);
        } catch (ConcurrentModificationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(irelation
              .getUserFriendlyName());
          throw new CLIException(msg);
        } catch (CommunicationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(irelation
              .getUserFriendlyName(), e.getMessage());
          throw new CLIException(msg);
        } catch (DefinitionDecodingException e) {
          // Do nothing.
        } catch (ManagedObjectDecodingException e) {
          // Do nothing.
        } catch (ManagedObjectNotFoundException e) {
          // The child does not already exist so this name is ok.
          return child;
        }

        // A child with the specified name must already exist.
        Message msg = ERR_DSCFG_ERROR_CREATE_NAME_ALREADY_EXISTS.get(relation
            .getUserFriendlyName(), input);
        app.println();
        app.println(msg);
        app.println();
        return null;
      }

    };

    app.println();
    app.println();

    // Display additional help if the name is a naming property.
    Message ufn = d.getUserFriendlyName();
    PropertyDefinition<?> pd = irelation.getNamingPropertyDefinition();
    if (pd != null) {
      app.println(INFO_DSCFG_CREATE_NAME_PROMPT_NAMING.get(ufn, pd.getName()));

      app.println();
      app.println(pd.getSynopsis(), 4);

      if (pd.getDescription() != null) {
        app.println();
        app.println(pd.getDescription(), 4);
      }

      PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(
          true);
      app.println();
      app.println(INFO_EDITOR_HEADING_SYNTAX.get(b.getUsage(pd)), 4);
      app.println();

      return app.readValidatedInput(INFO_DSCFG_CREATE_NAME_PROMPT_NAMING_CONT
          .get(ufn), validator);
    } else {
      return app.readValidatedInput(INFO_DSCFG_CREATE_NAME_PROMPT.get(ufn),
          validator);
    }
  }



  // Generate the type name - definition mapping table.
  @SuppressWarnings("unchecked")
  private SortedMap<String, ManagedObjectDefinition<? extends C, ? extends S>>
      getSubTypes(AbstractManagedObjectDefinition<C, S> d) {
    SortedMap<String, ManagedObjectDefinition<? extends C, ? extends S>> map;
    map =
      new TreeMap<String, ManagedObjectDefinition<? extends C, ? extends S>>();

    // If the top-level definition is instantiable, we use the value
    // "generic".
    if (d instanceof ManagedObjectDefinition) {
      ManagedObjectDefinition<? extends C, ? extends S> mod =
        (ManagedObjectDefinition<? extends C, ? extends S>) d;
      map.put(GENERIC_TYPE, mod);
    }

    // Process its sub-definitions.
    String suffix = "-" + d.getName();
    for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d
        .getAllChildren()) {
      if (c instanceof ManagedObjectDefinition) {
        ManagedObjectDefinition<? extends C, ? extends S> mod =
          (ManagedObjectDefinition<? extends C, ? extends S>) c;

        // For the type name we shorten it, if possible, by stripping
        // off the trailing part of the name which matches the
        // base-type.
        String name = mod.getName();
        if (name.endsWith(suffix)) {
          name = name.substring(0, name.length() - suffix.length());
        }

        map.put(name, mod);
      }
    }

    return map;
  }



  // Set a property's initial values.
  private <T> void setProperty(ManagedObject<?> mo,
      MyPropertyProvider provider, PropertyDefinition<T> pd) {
    Collection<T> values = provider.getPropertyValues(pd);

    // This cannot fail because the property values have already been
    // validated.
    mo.setPropertyValues(pd, values);
  }
}
