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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Message;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
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
import org.opends.server.util.cli.MenuResult;



/**
 * A sub-command handler which is used to modify the properties of a
 * managed object.
 * <p>
 * This sub-command implements the various set-xxx-prop sub-commands.
 */
final class SetPropSubCommandHandler extends SubCommandHandler {

  /**
   * Type of modication being performed.
   */
  private static enum ModificationType {
    /**
     * Append a single value to the property.
     */
    ADD,

    /**
     * Remove a single value from the property.
     */
    REMOVE,

    /**
     * Append a single value to the property (first invocation removes
     * existing values).
     */
    SET;
  }

  /**
   * The value for the long option add.
   */
  private static final String OPTION_DSCFG_LONG_ADD = "add";

  /**
   * The value for the long option remove.
   */
  private static final String OPTION_DSCFG_LONG_REMOVE = "remove";

  /**
   * The value for the long option reset.
   */
  private static final String OPTION_DSCFG_LONG_RESET = "reset";

  /**
   * The value for the long option set.
   */
  private static final String OPTION_DSCFG_LONG_SET = "set";

  /**
   * The value for the short option add.
   */
  private static final Character OPTION_DSCFG_SHORT_ADD = null;

  /**
   * The value for the short option remove.
   */
  private static final Character OPTION_DSCFG_SHORT_REMOVE = null;

  /**
   * The value for the short option reset.
   */
  private static final Character OPTION_DSCFG_SHORT_RESET = null;

  /**
   * The value for the short option set.
   */
  private static final Character OPTION_DSCFG_SHORT_SET = null;



  /**
   * Creates a new set-xxx-prop sub-command for an instantiable
   * relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param path
   *          The parent managed object path.
   * @param r
   *          The instantiable relation.
   * @return Returns the new set-xxx-prop sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static SetPropSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      InstantiableRelationDefinition<?, ?> r) throws ArgumentException {
    return new SetPropSubCommandHandler(parser, path.child(r, "DUMMY"), r);
  }



  /**
   * Creates a new set-xxx-prop sub-command for an optional relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param path
   *          The parent managed object path.
   * @param r
   *          The optional relation.
   * @return Returns the new set-xxx-prop sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static SetPropSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      OptionalRelationDefinition<?, ?> r) throws ArgumentException {
    return new SetPropSubCommandHandler(parser, path.child(r), r);
  }



  /**
   * Creates a new set-xxx-prop sub-command for a singleton relation.
   *
   * @param parser
   *          The sub-command argument parser.
   * @param path
   *          The parent managed object path.
   * @param r
   *          The singleton relation.
   * @return Returns the new set-xxx-prop sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static SetPropSubCommandHandler create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      SingletonRelationDefinition<?, ?> r) throws ArgumentException {
    return new SetPropSubCommandHandler(parser, path.child(r), r);
  }

  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The path of the managed object.
  private final ManagedObjectPath<?, ?> path;

  // The argument which should be used to specify zero or more
  // property value adds.
  private final StringArgument propertyAddArgument;

  // The argument which should be used to specify zero or more
  // property value removes.
  private final StringArgument propertyRemoveArgument;

  // The argument which should be used to specify zero or more
  // property value resets.
  private final StringArgument propertyResetArgument;

  // The argument which should be used to specify zero or more
  // property value assignments.
  private final StringArgument propertySetArgument;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;



  // Private constructor.
  private SetPropSubCommandHandler(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> path,
      RelationDefinition<?, ?> r) throws ArgumentException {
    this.path = path;

    // Create the sub-command.
    String name = "set-" + r.getName() + "-prop";
    Message description = INFO_DSCFG_DESCRIPTION_SUBCMD_SETPROP.get(r
        .getChildDefinition().getUserFriendlyName());
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        description);

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, path, false);

    // Register common arguments.
    registerAdvancedModeArgument(this.subCommand,
        INFO_DSCFG_DESCRIPTION_ADVANCED_SET.get());

    // Create the --set argument.
    this.propertySetArgument = new StringArgument(OPTION_DSCFG_LONG_SET,
        OPTION_DSCFG_SHORT_SET, OPTION_DSCFG_LONG_SET, false, true, true,
        "{PROP:VAL}", null, null, INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
    this.subCommand.addArgument(this.propertySetArgument);

    // Create the --reset argument.
    this.propertyResetArgument = new StringArgument(OPTION_DSCFG_LONG_RESET,
        OPTION_DSCFG_SHORT_RESET, OPTION_DSCFG_LONG_RESET, false, true, true,
        "{PROP}", null, null, INFO_DSCFG_DESCRIPTION_RESET_PROP.get());
    this.subCommand.addArgument(this.propertyResetArgument);

    // Create the --add argument.
    this.propertyAddArgument = new StringArgument(OPTION_DSCFG_LONG_ADD,
        OPTION_DSCFG_SHORT_ADD, OPTION_DSCFG_LONG_ADD, false, true, true,
        "{PROP:VAL}", null, null, INFO_DSCFG_DESCRIPTION_ADD_PROP_VAL.get());
    this.subCommand.addArgument(this.propertyAddArgument);

    // Create the --remove argument.
    this.propertyRemoveArgument = new StringArgument(OPTION_DSCFG_LONG_REMOVE,
        OPTION_DSCFG_SHORT_REMOVE, OPTION_DSCFG_LONG_REMOVE, false, true, true,
        "{PROP:VAL}", null, null, INFO_DSCFG_DESCRIPTION_REMOVE_PROP_VAL.get());
    this.subCommand.addArgument(this.propertyRemoveArgument);

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
  @SuppressWarnings("unchecked")
  @Override
  public MenuResult<Integer> run(ConsoleApplication app,
      ManagementContextFactory factory) throws ArgumentException,
      ClientException, CLIException {
    // Get the naming argument values.
    List<String> names = getNamingArgValues(app, namingArgs);

    // Get the targeted managed object.
    Message ufn = path.getRelationDefinition().getUserFriendlyName();
    ManagementContext context = factory.getManagementContext(app);
    MenuResult<ManagedObject<?>> result;
    try {
      result = getManagedObject(app, context, path, names);
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_MODIFY_AUTHZ.get(ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (DefinitionDecodingException e) {
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_DDE.get(ufn, ufn, ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (ManagedObjectDecodingException e) {
      // FIXME: should not abort here. Instead, display the errors (if
      // verbose) and apply the changes to the partial managed object.
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_MODE.get(ufn);
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_MODIFY_CE.get(ufn, e.getMessage());
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_MODIFY_CME.get(ufn);
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (ManagedObjectNotFoundException e) {
      Message msg = ERR_DSCFG_ERROR_GET_CHILD_MONFE.get(ufn);
      throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
    }

    if (result.isQuit()) {
      if (!app.isMenuDrivenMode()) {
        // User chose to quit.
        Message msg = INFO_DSCFG_CONFIRM_MODIFY_FAIL.get(ufn);
        app.printVerboseMessage(msg);
      }
      return MenuResult.quit();
    } else if (result.isCancel()) {
      return MenuResult.cancel();
    }

    ManagedObject<?> child = result.getValue();
    ManagedObjectDefinition<?, ?> d = child.getManagedObjectDefinition();
    Map<String, ModificationType> lastModTypes =
      new HashMap<String, ModificationType>();
    Map<PropertyDefinition, Set> changes =
      new HashMap<PropertyDefinition, Set>();

    // Reset properties.
    for (String m : propertyResetArgument.getValues()) {
      // Check the property definition.
      PropertyDefinition<?> pd;
      try {
        pd = d.getPropertyDefinition(m);
      } catch (IllegalArgumentException e) {
        throw ArgumentExceptionFactory.unknownProperty(d, m);
      }

      // Mandatory properties which have no defined defaults cannot be
      // reset.
      if (pd.hasOption(PropertyOption.MANDATORY)) {
        if (pd.getDefaultBehaviorProvider()
            instanceof UndefinedDefaultBehaviorProvider) {
          throw ArgumentExceptionFactory.unableToResetMandatoryProperty(d, m,
              OPTION_DSCFG_LONG_SET);
        }
      }

      // Save the modification type.
      lastModTypes.put(m, ModificationType.SET);

      // Apply the modification.
      modifyPropertyValues(child, pd, changes, ModificationType.SET, null);
    }

    // Set properties.
    for (String m : propertySetArgument.getValues()) {
      // Parse the property "property:value".
      int sep = m.indexOf(':');

      if (sep < 0) {
        throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(m);
      }

      if (sep == 0) {
        throw ArgumentExceptionFactory.missingNameInPropertyArgument(m);
      }

      String propertyName = m.substring(0, sep);
      String value = m.substring(sep + 1, m.length());
      if (value.length() == 0) {
        throw ArgumentExceptionFactory.missingValueInPropertyArgument(m);
      }

      // Check the property definition.
      PropertyDefinition<?> pd;
      try {
        pd = d.getPropertyDefinition(propertyName);
      } catch (IllegalArgumentException e) {
        throw ArgumentExceptionFactory.unknownProperty(d, propertyName);
      }

      // Apply the modification.
      if (lastModTypes.containsKey(propertyName)) {
        modifyPropertyValues(child, pd, changes, ModificationType.ADD, value);
      } else {
        lastModTypes.put(propertyName, ModificationType.SET);
        modifyPropertyValues(child, pd, changes, ModificationType.SET, value);
      }
    }

    // Remove properties.
    for (String m : propertyRemoveArgument.getValues()) {
      // Parse the property "property:value".
      int sep = m.indexOf(':');

      if (sep < 0) {
        throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(m);
      }

      if (sep == 0) {
        throw ArgumentExceptionFactory.missingNameInPropertyArgument(m);
      }

      String propertyName = m.substring(0, sep);
      String value = m.substring(sep + 1, m.length());
      if (value.length() == 0) {
        throw ArgumentExceptionFactory.missingValueInPropertyArgument(m);
      }

      // Check the property definition.
      PropertyDefinition<?> pd;
      try {
        pd = d.getPropertyDefinition(propertyName);
      } catch (IllegalArgumentException e) {
        throw ArgumentExceptionFactory.unknownProperty(d, propertyName);
      }

      // Apply the modification.
      if (lastModTypes.containsKey(propertyName)) {
        if (lastModTypes.get(propertyName) == ModificationType.SET) {
          throw ArgumentExceptionFactory.incompatiblePropertyModification(m);
        }
      } else {
        lastModTypes.put(propertyName, ModificationType.REMOVE);
        modifyPropertyValues(child, pd, changes, ModificationType.REMOVE,
            value);
      }
    }

    // Add properties.
    for (String m : propertyAddArgument.getValues()) {
      // Parse the property "property:value".
      int sep = m.indexOf(':');

      if (sep < 0) {
        throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(m);
      }

      if (sep == 0) {
        throw ArgumentExceptionFactory.missingNameInPropertyArgument(m);
      }

      String propertyName = m.substring(0, sep);
      String value = m.substring(sep + 1, m.length());
      if (value.length() == 0) {
        throw ArgumentExceptionFactory.missingValueInPropertyArgument(m);
      }

      // Check the property definition.
      PropertyDefinition<?> pd;
      try {
        pd = d.getPropertyDefinition(propertyName);
      } catch (IllegalArgumentException e) {
        throw ArgumentExceptionFactory.unknownProperty(d, propertyName);
      }

      // Apply the modification.
      if (lastModTypes.containsKey(propertyName)) {
        if (lastModTypes.get(propertyName) == ModificationType.SET) {
          throw ArgumentExceptionFactory.incompatiblePropertyModification(m);
        }
      } else {
        lastModTypes.put(propertyName, ModificationType.ADD);
        modifyPropertyValues(child, pd, changes, ModificationType.ADD, value);
      }
    }

    // Commit the changes.
    for (PropertyDefinition<?> pd : changes.keySet()) {
      try {
        child.setPropertyValues(pd, changes.get(pd));
      } catch (PropertyException e) {
        throw ArgumentExceptionFactory.adaptPropertyException(e, d);
      }
    }

    // Interactively set properties if applicable.
    if (app.isInteractive()) {
      SortedSet<PropertyDefinition<?>> properties =
        new TreeSet<PropertyDefinition<?>>();

      for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
        if (pd.hasOption(PropertyOption.HIDDEN)) {
          continue;
        }

        if (!isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
          continue;
        }

        properties.add(pd);
      }

      PropertyValueEditor editor = new PropertyValueEditor(app);
      MenuResult<Void> result2 = editor.edit(child, properties, true);
      if (result2.isQuit()) {
        if (!app.isMenuDrivenMode()) {
          // User chose to cancel any changes.
          Message msg = INFO_DSCFG_CONFIRM_MODIFY_FAIL.get(ufn);
          app.printVerboseMessage(msg);
        }
        return MenuResult.quit();
      } else if (result2.isCancel()) {
        return MenuResult.cancel();
      }
    }

    try {
      child.commit();

      // Output success message.
      Message msg = INFO_DSCFG_CONFIRM_MODIFY_SUCCESS.get(ufn);
      app.printVerboseMessage(msg);
    } catch (MissingMandatoryPropertiesException e) {
      throw ArgumentExceptionFactory.adaptMissingMandatoryPropertiesException(
          e, d);
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_MODIFY_AUTHZ.get(ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_MODIFY_CME.get(ufn);
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (OperationRejectedException e) {
      Message msg = ERR_DSCFG_ERROR_MODIFY_ORE.get(ufn, e.getMessage());
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_MODIFY_CE.get(ufn, e.getMessage());
      throw new ClientException(LDAPResultCode.OPERATIONS_ERROR, msg);
    } catch (ManagedObjectAlreadyExistsException e) {
      // Should never happen.
      throw new IllegalStateException(e);
    }

    return MenuResult.success(0);
  }



  // Apply a single modification to the current change-set.
  @SuppressWarnings("unchecked")
  private <T> void modifyPropertyValues(ManagedObject<?> mo,
      PropertyDefinition<T> pd, Map<PropertyDefinition, Set> changes,
      ModificationType modType, String s) throws ArgumentException {
    Set<T> values = changes.get(pd);
    if (values == null) {
      values = mo.getPropertyValues(pd);
    }

    if (s == null || s.length() == 0) {
      // Reset back to defaults.
      values.clear();
    } else {
      T value;
      try {
        value = pd.decodeValue(s);
      } catch (IllegalPropertyValueStringException e) {
        throw ArgumentExceptionFactory.adaptPropertyException(e, mo
            .getManagedObjectDefinition());
      }

      switch (modType) {
      case ADD:
        values.add(value);
        break;
      case REMOVE:
        values.remove(value);
        break;
      case SET:
        values = new TreeSet<T>(pd);
        values.add(value);
        break;
      }
    }

    changes.put(pd, values);
  }
}
