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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsconfig;



import static org.opends.messages.DSConfigMessages.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.EnumPropertyDefinition;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.PropertyDefinitionVisitor;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.UnknownPropertyDefinitionException;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.util.Validator;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.HelpCallback;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuCallback;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;



/**
 * Common methods used for interactively editing properties.
 */
final class PropertyValueEditor {

  /**
   * A menu call-back which can be used to dynamically create new
   * components when configuring aggregation based properties.
   */
  private final class CreateComponentCallback
      <C extends ConfigurationClient, S extends Configuration>
      implements MenuCallback<String> {

    // The aggregation property definition.
    private final AggregationPropertyDefinition<C, S> pd;



    // Creates a new component create call-back for the provided
    // aggregation property definition.
    private CreateComponentCallback(AggregationPropertyDefinition<C, S> pd) {
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    public MenuResult<String> invoke(ConsoleApplication app)
        throws CLIException {
      try {
        // First get the parent managed object.
        InstantiableRelationDefinition<?, ?> rd = pd.getRelationDefinition();
        ManagedObjectPath<?, ?> path = pd.getParentPath();
        Message ufn = rd.getUserFriendlyName();

        ManagedObject<?> parent;
        try {
          parent = context.getManagedObject(path);
        } catch (AuthorizationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
          throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              msg);
        } catch (DefinitionDecodingException e) {
          Message pufn = path.getManagedObjectDefinition()
              .getUserFriendlyName();
          Message msg = ERR_DSCFG_ERROR_GET_PARENT_DDE.get(pufn, pufn, pufn);
          throw new ClientException(LDAPResultCode.OTHER, msg);
        } catch (ManagedObjectDecodingException e) {
          Message pufn = path.getManagedObjectDefinition()
              .getUserFriendlyName();
          Message msg = ERR_DSCFG_ERROR_GET_PARENT_MODE.get(pufn);
          throw new ClientException(LDAPResultCode.OTHER, msg, e);
        } catch (CommunicationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e.getMessage());
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
              msg);
        } catch (ManagedObjectNotFoundException e) {
          Message pufn = path.getManagedObjectDefinition()
              .getUserFriendlyName();
          Message msg = ERR_DSCFG_ERROR_GET_PARENT_MONFE.get(pufn);
          if (app.isInteractive()) {
            app.println();
            app.printVerboseMessage(msg);
            return MenuResult.cancel();
          } else {
            throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
          }
        }

        // Now let the user create the child component.
        app.println();
        app.println();
        return CreateSubCommandHandler.createManagedObject(app, context,
            parent, rd);
      } catch (ClientException e) {
        // FIXME: should really do something better with the exception
        // handling here. For example, if a authz or communications
        // exception occurs then the application should exit.
        app.println();
        app.println(e.getMessageObject());
        app.println();
        app.pressReturnToContinue();
        return MenuResult.cancel();
      }
    }
  }



  /**
   * A help call-back which displays a description and summary of a
   * component and its properties.
   */
  private static final class ComponentHelpCallback implements HelpCallback {

    // The managed object being edited.
    private final ManagedObject<?> mo;

    // The properties that can be edited.
    private final Collection<PropertyDefinition<?>> properties;



    // Creates a new component helper for the specified property.
    private ComponentHelpCallback(ManagedObject<?> mo,
        Collection<PropertyDefinition<?>> c) {
      this.mo = mo;
      this.properties = c;
    }



    /**
     * {@inheritDoc}
     */
    public void display(ConsoleApplication app) {
      app.println();
      HelpSubCommandHandler.displaySingleComponent(app, mo
          .getManagedObjectDefinition(), properties);
      app.println();
      app.pressReturnToContinue();
    }
  }



  /**
   * A simple interface for querying and retrieving common default
   * behavior properties.
   */
  private static final class DefaultBehaviorQuery<T> {

    /**
     * The type of default behavior.
     */
    private enum Type {
      /**
       * Alias default behavior.
       */
      ALIAS,

      /**
       * Defined default behavior.
       */
      DEFINED,

      /**
       * Inherited default behavior.
       */
      INHERITED,

      /**
       * Undefined default behavior.
       */
      UNDEFINED;
    };



    /**
     * Create a new default behavior query object based on the provied
     * property definition.
     *
     * @param <T>
     *          The type of property definition.
     * @param pd
     *          The property definition.
     * @return The default behavior query object.
     */
    public static <T> DefaultBehaviorQuery<T> query(PropertyDefinition<T> pd) {
      DefaultBehaviorProviderVisitor<T, DefaultBehaviorQuery<T>,
        PropertyDefinition<T>> visitor =
          new DefaultBehaviorProviderVisitor<T, DefaultBehaviorQuery<T>,
          PropertyDefinition<T>>() {

        /**
         * {@inheritDoc}
         */
        public DefaultBehaviorQuery<T> visitAbsoluteInherited(
            AbsoluteInheritedDefaultBehaviorProvider<T> d,
            PropertyDefinition<T> p) {
          AbstractManagedObjectDefinition<?, ?> mod = d
              .getManagedObjectDefinition();
          String propertyName = d.getPropertyName();
          PropertyDefinition<?> pd2 = mod.getPropertyDefinition(propertyName);

          DefaultBehaviorQuery<?> query = query(pd2);
          return new DefaultBehaviorQuery<T>(Type.INHERITED, query
              .getAliasDescription());
        }



        /**
         * {@inheritDoc}
         */
        public DefaultBehaviorQuery<T> visitAlias(
            AliasDefaultBehaviorProvider<T> d, PropertyDefinition<T> p) {
          return new DefaultBehaviorQuery<T>(Type.ALIAS, d.getSynopsis());
        }



        /**
         * {@inheritDoc}
         */
        public DefaultBehaviorQuery<T> visitDefined(
            DefinedDefaultBehaviorProvider<T> d, PropertyDefinition<T> p) {
          return new DefaultBehaviorQuery<T>(Type.DEFINED, null);
        }



        /**
         * {@inheritDoc}
         */
        public DefaultBehaviorQuery<T> visitRelativeInherited(
            RelativeInheritedDefaultBehaviorProvider<T> d,
            PropertyDefinition<T> p) {
          AbstractManagedObjectDefinition<?, ?> mod = d
              .getManagedObjectDefinition();
          String propertyName = d.getPropertyName();
          PropertyDefinition<?> pd2 = mod.getPropertyDefinition(propertyName);

          DefaultBehaviorQuery<?> query = query(pd2);
          return new DefaultBehaviorQuery<T>(Type.INHERITED, query
              .getAliasDescription());
        }



        /**
         * {@inheritDoc}
         */
        public DefaultBehaviorQuery<T> visitUndefined(
            UndefinedDefaultBehaviorProvider<T> d, PropertyDefinition<T> p) {
          return new DefaultBehaviorQuery<T>(Type.UNDEFINED, null);
        }
      };

      return pd.getDefaultBehaviorProvider().accept(visitor, pd);
    }

    // The description of the behavior if it is an alias default
    // behavior.
    private final Message aliasDescription;

    // The type of behavior.
    private final Type type;



    // Private constructor.
    private DefaultBehaviorQuery(Type type, Message aliasDescription) {
      this.type = type;
      this.aliasDescription = aliasDescription;
    }



    /**
     * Gets the detailed description of this default behavior if it is
     * an alias default behavior or if it inherits from an alias
     * default behavior.
     *
     * @return Returns the detailed description of this default
     *         behavior if it is an alias default behavior or if it
     *         inherits from an alias default behavior, otherwise
     *         <code>null</code>.
     */
    public Message getAliasDescription() {
      return aliasDescription;
    }



    /**
     * Determines whether or not the default behavior is alias.
     *
     * @return Returns <code>true</code> if the default behavior is
     *         alias.
     */
    public boolean isAlias() {
      return type == Type.ALIAS;
    }



    /**
     * Determines whether or not the default behavior is defined.
     *
     * @return Returns <code>true</code> if the default behavior is
     *         defined.
     */
    public boolean isDefined() {
      return type == Type.DEFINED;
    }



    /**
     * Determines whether or not the default behavior is inherited.
     *
     * @return Returns <code>true</code> if the default behavior is
     *         inherited.
     */
    public boolean isInherited() {
      return type == Type.INHERITED;
    }



    /**
     * Determines whether or not the default behavior is undefined.
     *
     * @return Returns <code>true</code> if the default behavior is
     *         undefined.
     */
    public boolean isUndefined() {
      return type == Type.UNDEFINED;
    }

  }



  /**
   * A property definition visitor which initializes mandatory
   * properties.
   */
  private final class MandatoryPropertyInitializer extends
      PropertyDefinitionVisitor<MenuResult<Void>, Void> implements
      MenuCallback<Void> {

    // Any exception that was caught during processing.
    private CLIException e = null;

    // The managed object being edited.
    private final ManagedObject<?> mo;

    // The property to be edited.
    private final PropertyDefinition<?> pd;



    // Creates a new property editor for the specified property.
    private MandatoryPropertyInitializer(ManagedObject<?> mo,
        PropertyDefinition<?> pd) {
      this.mo = mo;
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    public MenuResult<Void> invoke(ConsoleApplication app)
        throws CLIException {
      displayPropertyHeader(app, pd);

      MenuResult<Void> result = pd.accept(this, null);

      if (e != null) {
        throw e;
      } else {
        return result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends ConfigurationClient, S extends Configuration>
        MenuResult<Void> visitAggregation(
        AggregationPropertyDefinition<C, S> d, Void p) {
      MenuBuilder<String> builder = new MenuBuilder<String>(app);
      builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);

      InstantiableRelationDefinition<C, S> rd = d.getRelationDefinition();
      if (d.hasOption(PropertyOption.MULTI_VALUED)) {
        builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_COMPONENT_MULTI.get(rd
            .getUserFriendlyPluralName(), d.getName()));
        builder.setAllowMultiSelect(true);
      } else {
        builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_COMPONENT_SINGLE.get(rd
            .getUserFriendlyName(), d.getName()));
      }

      // Create a list of possible names.
      Set<String> values = new TreeSet<String>(d);
      ManagedObjectPath<?,?> path = d.getParentPath();
      try {
        values.addAll(Arrays.asList(context.listManagedObjects(path, rd)));
      } catch (AuthorizationException e) {
        this.e = new CLIException(e.getMessageObject());
        return MenuResult.quit();
      } catch (ManagedObjectNotFoundException e) {
        this.e = new CLIException(e.getMessageObject());
        return MenuResult.cancel();
      } catch (CommunicationException e) {
        this.e = new CLIException(e.getMessageObject());
        return MenuResult.quit();
      }

      for (String value : values) {
        Message option = getPropertyValues(d, Collections.singleton(value));
        builder.addNumberedOption(option, MenuResult.success(value));
      }
      MenuCallback<String> callback = new CreateComponentCallback<C, S>(d);
      builder.addNumberedOption(INFO_EDITOR_OPTION_CREATE_A_NEW_COMPONENT
          .get(rd.getUserFriendlyName()), callback);

      builder.addHelpOption(new PropertyHelpCallback(mo
          .getManagedObjectDefinition(), d));
      if (app.isMenuDrivenMode()) {
        builder.addCancelOption(true);
      }
      builder.addQuitOption();

      Menu<String> menu = builder.toMenu();
      try {
        app.println();
        MenuResult<String> result = menu.run();

        if (result.isQuit()) {
          return MenuResult.quit();
        } else if (result.isCancel()) {
          return MenuResult.cancel();
        } else {
          Collection<String> newValues = result.getValues();
          SortedSet<String> oldValues =
            new TreeSet<String>(mo.getPropertyValues(d));
          mo.setPropertyValues(d, newValues);
          isLastChoiceReset = false;
          registerModification(d, new TreeSet<String>(newValues),
              oldValues);
          return MenuResult.success();
        }
      } catch (CLIException e) {
        this.e = e;
        return MenuResult.cancel();
      }
    }



    /**
     * /** {@inheritDoc}
     */
    @Override
    public MenuResult<Void> visitBoolean(BooleanPropertyDefinition d, Void p) {
      MenuBuilder<Boolean> builder = new MenuBuilder<Boolean>(app);

      builder
          .setPrompt(INFO_EDITOR_PROMPT_SELECT_VALUE_SINGLE.get(d.getName()));

      builder
          .addNumberedOption(INFO_VALUE_TRUE.get(), MenuResult.success(true));
      builder.addNumberedOption(INFO_VALUE_FALSE.get(), MenuResult
          .success(false));

      builder.addHelpOption(new PropertyHelpCallback(mo
          .getManagedObjectDefinition(), d));
      if (app.isMenuDrivenMode()) {
        builder.addCancelOption(true);
      }
      builder.addQuitOption();

      Menu<Boolean> menu = builder.toMenu();
      try {
        app.println();
        MenuResult<Boolean> result = menu.run();

        if (result.isQuit()) {
          return MenuResult.quit();
        } else if (result.isCancel()) {
          return MenuResult.cancel();
        } else {
          Collection<Boolean> newValues = result.getValues();
          SortedSet<Boolean> oldValues = new TreeSet<Boolean>(
              mo.getPropertyValues(d));
          mo.setPropertyValues(d, newValues);
          isLastChoiceReset = false;
          registerModification(d, new TreeSet<Boolean>(newValues),
              oldValues);
          return MenuResult.success();
        }
      } catch (CLIException e) {
        this.e = e;
        return MenuResult.cancel();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> MenuResult<Void> visitEnum(
        EnumPropertyDefinition<E> d, Void x) {
      MenuBuilder<E> builder = new MenuBuilder<E>(app);
      builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);

      if (d.hasOption(PropertyOption.MULTI_VALUED)) {
        builder
            .setPrompt(INFO_EDITOR_PROMPT_SELECT_VALUE_MULTI.get(d.getName()));
        builder.setAllowMultiSelect(true);
      } else {
        builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_VALUE_SINGLE
            .get(d.getName()));
      }

      Set<E> values = new TreeSet<E>(d);
      values.addAll(EnumSet.allOf(d.getEnumClass()));
      for (E value : values) {
        Message option = getPropertyValues(d, Collections.singleton(value));
        builder.addNumberedOption(option, MenuResult.success(value));
      }

      builder.addHelpOption(new PropertyHelpCallback(mo
          .getManagedObjectDefinition(), d));
      if (app.isMenuDrivenMode()) {
        builder.addCancelOption(true);
      }
      builder.addQuitOption();

      Menu<E> menu = builder.toMenu();
      try {
        app.println();
        MenuResult<E> result = menu.run();

        if (result.isQuit()) {
          return MenuResult.quit();
        } else if (result.isCancel()) {
          return MenuResult.cancel();
        } else {
          Collection<E> newValues = result.getValues();
          SortedSet<E> oldValues = new TreeSet<E>(mo.getPropertyValues(d));
          mo.setPropertyValues(d, newValues);
          isLastChoiceReset = false;
          registerModification(d, new TreeSet<E>(newValues), oldValues);
          return MenuResult.success();
        }
      } catch (CLIException e) {
        this.e = e;
        return MenuResult.cancel();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <T> MenuResult<Void> visitUnknown(PropertyDefinition<T> d,
        Void p) throws UnknownPropertyDefinitionException {
      app.println();
      displayPropertySyntax(app, d);

      // Set the new property value(s).
      try {
        SortedSet<T> values = readPropertyValues(app,
            mo.getManagedObjectDefinition(), d);
        SortedSet<T> oldValues = new TreeSet<T>(mo.getPropertyValues(d));
        mo.setPropertyValues(d, values);
        isLastChoiceReset = false;
        registerModification(d, values, oldValues);
        return MenuResult.success();
      } catch (CLIException e) {
        this.e = e;
        return MenuResult.cancel();
      }
    }

  }



  /**
   * A menu call-back for editing a modifiable multi-valued property.
   */
  private final class MultiValuedPropertyEditor extends
      PropertyDefinitionVisitor<MenuResult<Boolean>, Void>
      implements MenuCallback<Boolean> {

    // Any exception that was caught during processing.
    private CLIException e = null;

    // The managed object being edited.
    private final ManagedObject<?> mo;

    // The property to be edited.
    private final PropertyDefinition<?> pd;



    // Creates a new property editor for the specified property.
    private MultiValuedPropertyEditor(ManagedObject<?> mo,
        PropertyDefinition<?> pd) {
      Validator.ensureTrue(pd.hasOption(PropertyOption.MULTI_VALUED));

      this.mo = mo;
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    public MenuResult<Boolean> invoke(ConsoleApplication app)
        throws CLIException {
      displayPropertyHeader(app, pd);

      MenuResult<Boolean> result = pd.accept(this, null);
      if (e != null) {
        throw e;
      } else {
        return result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends ConfigurationClient, S extends Configuration>
        MenuResult<Boolean> visitAggregation(
        final AggregationPropertyDefinition<C, S> d, Void p) {
      final SortedSet<String> defaultValues = mo.getPropertyDefaultValues(d);
      final SortedSet<String> oldValues = mo.getPropertyValues(d);
      final SortedSet<String> currentValues = mo.getPropertyValues(d);
      final InstantiableRelationDefinition<C, S> rd = d.getRelationDefinition();
      final Message ufpn = rd.getUserFriendlyPluralName();

      boolean isFirst = true;
      while (true) {
        if (!isFirst) {
          app.println();
          app.println(INFO_EDITOR_HEADING_CONFIGURE_PROPERTY_CONT.get(d
              .getName()));
        } else {
          isFirst = false;
        }

        if (currentValues.size() > 1) {
          app.println();
          app.println(INFO_EDITOR_HEADING_COMPONENT_SUMMARY.get(d.getName(),
              ufpn));
          app.println();
          displayPropertyValues(app, d, currentValues);
        }

        // Create a list of possible names.
        final Set<String> values = new TreeSet<String>(d);
        ManagedObjectPath<?,?> path = d.getParentPath();
        try {
          values.addAll(Arrays.asList(context.listManagedObjects(path, rd)));
        } catch (AuthorizationException e) {
          this.e = new CLIException(e.getMessageObject());
          return MenuResult.quit();
        } catch (ManagedObjectNotFoundException e) {
          this.e = new CLIException(e.getMessageObject());
          return MenuResult.cancel();
        } catch (CommunicationException e) {
          this.e = new CLIException(e.getMessageObject());
          return MenuResult.quit();
        }

        // Create the add values call-back.
        MenuCallback<Boolean> addCallback = null;
        values.removeAll(currentValues);
        if (!values.isEmpty()) {
          addCallback = new MenuCallback<Boolean>() {

            public MenuResult<Boolean> invoke(ConsoleApplication app)
                throws CLIException {
              MenuBuilder<String> builder = new MenuBuilder<String>(app);

              builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_COMPONENTS_ADD
                  .get(ufpn));
              builder.setAllowMultiSelect(true);
              builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);

              for (String value : values) {
                Message svalue = getPropertyValues(d, Collections
                    .singleton(value));
                builder.addNumberedOption(svalue, MenuResult.success(value));
              }
              MenuCallback<String> callback = new CreateComponentCallback<C, S>(
                  d);
              builder.addNumberedOption(
                  INFO_EDITOR_OPTION_CREATE_A_NEW_COMPONENT.get(rd
                      .getUserFriendlyName()), callback);

              if (values.size() > 1) {
                // No point in having this option if there's only one
                // possible value.
                builder.addNumberedOption(INFO_EDITOR_OPTION_ADD_ALL_COMPONENTS
                    .get(ufpn), MenuResult.success(values));
              }

              builder.addHelpOption(new PropertyHelpCallback(mo
                  .getManagedObjectDefinition(), d));

              builder.addCancelOption(true);
              builder.addQuitOption();

              app.println();
              app.println();
              Menu<String> menu = builder.toMenu();
              MenuResult<String> result = menu.run();

              if (result.isSuccess()) {
                // Set the new property value(s).
                Collection<String> addedValues = result.getValues();
                currentValues.addAll(addedValues);

                isLastChoiceReset = false;
                app.println();
                app.pressReturnToContinue();
                return MenuResult.success(false);
              } else if (result.isCancel()) {
                app.println();
                app.pressReturnToContinue();
                return MenuResult.success(false);
              } else {
                return MenuResult.quit();
              }
            }

          };
        }

        // Create the remove values call-back.
        MenuCallback<Boolean> removeCallback = new MenuCallback<Boolean>() {

          public MenuResult<Boolean> invoke(ConsoleApplication app)
              throws CLIException {
            MenuBuilder<String> builder = new MenuBuilder<String>(app);

            builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_COMPONENTS_REMOVE
                .get(ufpn));
            builder.setAllowMultiSelect(true);
            builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);

            for (String value : currentValues) {
              Message svalue = getPropertyValues(d, Collections
                  .singleton(value));
              builder.addNumberedOption(svalue, MenuResult.success(value));
            }

            builder.addHelpOption(new PropertyHelpCallback(mo
                .getManagedObjectDefinition(), d));

            builder.addCancelOption(true);
            builder.addQuitOption();

            app.println();
            app.println();
            Menu<String> menu = builder.toMenu();
            MenuResult<String> result = menu.run();

            if (result.isSuccess()) {
              // Set the new property value(s).
              Collection<String> removedValues = result.getValues();
              currentValues.removeAll(removedValues);
              isLastChoiceReset = false;
              app.println();
              app.pressReturnToContinue();
              return MenuResult.success(false);
            } else if (result.isCancel()) {
              app.println();
              app.pressReturnToContinue();
              return MenuResult.success(false);
            } else {
              return MenuResult.quit();
            }
          }

        };

        MenuResult<Boolean> result = runMenu(d, app, defaultValues, oldValues,
            currentValues, addCallback, removeCallback);
        if (!result.isAgain()) {
          return result;
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends Enum<T>> MenuResult<Boolean> visitEnum(
        final EnumPropertyDefinition<T> d, Void p) {
      final SortedSet<T> defaultValues = mo.getPropertyDefaultValues(d);
      final SortedSet<T> oldValues = mo.getPropertyValues(d);
      final SortedSet<T> currentValues = mo.getPropertyValues(d);

      boolean isFirst = true;
      while (true) {
        if (!isFirst) {
          app.println();
          app.println(INFO_EDITOR_HEADING_CONFIGURE_PROPERTY_CONT.get(d
              .getName()));
        } else {
          isFirst = false;
        }

        if (currentValues.size() > 1) {
          app.println();
          app.println(INFO_EDITOR_HEADING_VALUES_SUMMARY.get(d.getName()));
          app.println();
          displayPropertyValues(app, d, currentValues);
        }

        // Create the add values call-back.
        MenuCallback<Boolean> addCallback = null;

        final EnumSet<T> values = EnumSet.allOf(d.getEnumClass());
        values.removeAll(currentValues);

        if (!values.isEmpty()) {
          addCallback = new MenuCallback<Boolean>() {

            public MenuResult<Boolean> invoke(ConsoleApplication app)
                throws CLIException {
              MenuBuilder<T> builder = new MenuBuilder<T>(app);

              builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_VALUES_ADD.get());
              builder.setAllowMultiSelect(true);
              builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);

              for (T value : values) {
                Message svalue = getPropertyValues(d, Collections
                    .singleton(value));
                builder.addNumberedOption(svalue, MenuResult.success(value));
              }

              if (values.size() > 1) {
                // No point in having this option if there's only one
                // possible value.
                builder.addNumberedOption(INFO_EDITOR_OPTION_ADD_ALL_VALUES
                    .get(), MenuResult.success(values));
              }

              builder.addHelpOption(new PropertyHelpCallback(mo
                  .getManagedObjectDefinition(), d));

              builder.addCancelOption(true);
              builder.addQuitOption();

              app.println();
              app.println();
              Menu<T> menu = builder.toMenu();
              MenuResult<T> result = menu.run();

              if (result.isSuccess()) {

                // Set the new property value(s).
                Collection<T> addedValues = result.getValues();
                currentValues.addAll(addedValues);
                isLastChoiceReset = false;
                app.println();
                app.pressReturnToContinue();
                return MenuResult.success(false);
              } else if (result.isCancel()) {
                app.println();
                app.pressReturnToContinue();
                return MenuResult.success(false);
              } else {
                return MenuResult.quit();
              }
            }

          };
        }

        // Create the remove values call-back.
        MenuCallback<Boolean> removeCallback = new MenuCallback<Boolean>() {

          public MenuResult<Boolean> invoke(ConsoleApplication app)
              throws CLIException {
            MenuBuilder<T> builder = new MenuBuilder<T>(app);

            builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_VALUES_REMOVE.get());
            builder.setAllowMultiSelect(true);
            builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);

            for (T value : currentValues) {
              Message svalue = getPropertyValues(d, Collections
                  .singleton(value));
              builder.addNumberedOption(svalue, MenuResult.success(value));
            }

            builder.addHelpOption(new PropertyHelpCallback(mo
                .getManagedObjectDefinition(), d));

            builder.addCancelOption(true);
            builder.addQuitOption();

            app.println();
            app.println();
            Menu<T> menu = builder.toMenu();
            MenuResult<T> result = menu.run();

            if (result.isSuccess()) {
              // Set the new property value(s).
              Collection<T> removedValues = result.getValues();
              currentValues.removeAll(removedValues);
              isLastChoiceReset = false;
              app.println();
              app.pressReturnToContinue();
              return MenuResult.success(false);
            } else if (result.isCancel()) {
              app.println();
              app.pressReturnToContinue();
              return MenuResult.success(false);
            } else {
              return MenuResult.quit();
            }
          }

        };

        MenuResult<Boolean> result = runMenu(d, app, defaultValues, oldValues,
            currentValues, addCallback, removeCallback);
        if (!result.isAgain()) {
          return result;
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <T> MenuResult<Boolean> visitUnknown(final PropertyDefinition<T> d,
        Void p) {
      app.println();
      displayPropertySyntax(app, d);

      final SortedSet<T> defaultValues = mo.getPropertyDefaultValues(d);
      final SortedSet<T> oldValues = mo.getPropertyValues(d);
      final SortedSet<T> currentValues = mo.getPropertyValues(d);

      boolean isFirst = true;
      while (true) {
        if (!isFirst) {
          app.println();
          app.println(INFO_EDITOR_HEADING_CONFIGURE_PROPERTY_CONT.get(d
              .getName()));
        } else {
          isFirst = false;
        }

        if (currentValues.size() > 1) {
          app.println();
          app.println(INFO_EDITOR_HEADING_VALUES_SUMMARY.get(d.getName()));
          app.println();
          displayPropertyValues(app, d, currentValues);
        }

        // Create the add values call-back.
        MenuCallback<Boolean> addCallback = new MenuCallback<Boolean>() {

          public MenuResult<Boolean> invoke(ConsoleApplication app)
              throws CLIException {
            app.println();
            SortedSet<T> previousValues = new TreeSet<T>(currentValues);
            readPropertyValues(app, mo.getManagedObjectDefinition(), d,
                currentValues);
            SortedSet<T> addedValues = new TreeSet<T>(currentValues);
            addedValues.removeAll(previousValues);
            isLastChoiceReset = false;
            return MenuResult.success(false);
          }

        };

        // Create the remove values call-back.
        MenuCallback<Boolean> removeCallback = new MenuCallback<Boolean>() {

          public MenuResult<Boolean> invoke(ConsoleApplication app)
              throws CLIException {
            MenuBuilder<T> builder = new MenuBuilder<T>(app);

            builder.setPrompt(INFO_EDITOR_PROMPT_SELECT_VALUES_REMOVE.get());
            builder.setAllowMultiSelect(true);
            builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);

            for (T value : currentValues) {
              Message svalue = getPropertyValues(d, Collections
                  .singleton(value));
              builder.addNumberedOption(svalue, MenuResult.success(value));
            }

            builder.addHelpOption(new PropertyHelpCallback(mo
                .getManagedObjectDefinition(), d));

            builder.addCancelOption(true);
            builder.addQuitOption();

            app.println();
            app.println();
            Menu<T> menu = builder.toMenu();
            MenuResult<T> result = menu.run();

            if (result.isSuccess()) {
              // Set the new property value(s).
              Collection<T> removedValues = result.getValues();
              currentValues.removeAll(removedValues);
              isLastChoiceReset = false;
              app.println();
              app.pressReturnToContinue();
              return MenuResult.success(false);
            } else if (result.isCancel()) {
              app.println();
              app.pressReturnToContinue();
              return MenuResult.success(false);
            } else {
              return MenuResult.quit();
            }
          }

        };

        MenuResult<Boolean> result = runMenu(d, app, defaultValues, oldValues,
            currentValues, addCallback, removeCallback);
        if (!result.isAgain()) {
          return result;
        }
      }
    }



    /**
     * Generate an appropriate menu option for a property which asks
     * the user whether or not they want to keep the property's
     * current settings.
     */
    private <T> Message getKeepDefaultValuesMenuOption(
        PropertyDefinition<T> pd, SortedSet<T> defaultValues,
        SortedSet<T> oldValues, SortedSet<T> currentValues) {
      DefaultBehaviorQuery<T> query = DefaultBehaviorQuery.query(pd);

      boolean isModified = !currentValues.equals(oldValues);
      boolean isDefault = currentValues.equals(defaultValues);

      if (isModified) {
        switch (currentValues.size()) {
        case 0:
          if (query.isAlias()) {
            return INFO_EDITOR_OPTION_USE_DEFAULT_ALIAS.get(query
                .getAliasDescription());
          } else if (query.isInherited()) {
            if (query.getAliasDescription() != null) {
              return INFO_EDITOR_OPTION_USE_DEFAULT_INHERITED_ALIAS.get(query
                  .getAliasDescription());
            } else {
              return INFO_EDITOR_OPTION_USE_DEFAULT_INHERITED_ALIAS_UNDEFINED
                  .get();
            }
          } else {
            return INFO_EDITOR_OPTION_LEAVE_UNDEFINED.get();
          }
        case 1:
          Message svalue = getPropertyValues(pd, currentValues);
          if (isDefault) {
            if (query.isInherited()) {
              return INFO_EDITOR_OPTION_USE_INHERITED_DEFAULT_VALUE.get(svalue);
            } else {
              return INFO_EDITOR_OPTION_USE_DEFAULT_VALUE.get(svalue);
            }
          } else {
            return INFO_EDITOR_OPTION_USE_VALUE.get(svalue);
          }
        default:
          if (isDefault) {
            if (query.isInherited()) {
              return INFO_EDITOR_OPTION_USE_INHERITED_DEFAULT_VALUES.get();
            } else {
              return INFO_EDITOR_OPTION_USE_DEFAULT_VALUES.get();
            }
          } else {
            return INFO_EDITOR_OPTION_USE_VALUES.get();
          }
        }
      } else {
        switch (currentValues.size()) {
        case 0:
          if (query.isAlias()) {
            return INFO_EDITOR_OPTION_KEEP_DEFAULT_ALIAS.get(query
                .getAliasDescription());
          } else if (query.isInherited()) {
            if (query.getAliasDescription() != null) {
              return INFO_EDITOR_OPTION_KEEP_DEFAULT_INHERITED_ALIAS.get(query
                  .getAliasDescription());
            } else {
              return INFO_EDITOR_OPTION_KEEP_DEFAULT_INHERITED_ALIAS_UNDEFINED
                  .get();
            }
          } else {
            return INFO_EDITOR_OPTION_LEAVE_UNDEFINED.get();
          }
        case 1:
          Message svalue = getPropertyValues(pd, currentValues);
          if (isDefault) {
            if (query.isInherited()) {
              return INFO_EDITOR_OPTION_KEEP_INHERITED_DEFAULT_VALUE
                  .get(svalue);
            } else {
              return INFO_EDITOR_OPTION_KEEP_DEFAULT_VALUE.get(svalue);
            }
          } else {
            return INFO_EDITOR_OPTION_KEEP_VALUE.get(svalue);
          }
        default:
          if (isDefault) {
            if (query.isInherited()) {
              return INFO_EDITOR_OPTION_KEEP_INHERITED_DEFAULT_VALUES.get();
            } else {
              return INFO_EDITOR_OPTION_KEEP_DEFAULT_VALUES.get();
            }
          } else {
            return INFO_EDITOR_OPTION_KEEP_VALUES.get();
          }
        }
      }
    }



    /**
     * Generate an appropriate menu option which should be used in the
     * case where a property can be reset to its default behavior.
     */
    private <T> Message getResetToDefaultValuesMenuOption(
        PropertyDefinition<T> pd, SortedSet<T> defaultValues,
        SortedSet<T> currentValues) {
      DefaultBehaviorQuery<T> query = DefaultBehaviorQuery.query(pd);
      boolean isMandatory = pd.hasOption(PropertyOption.MANDATORY);

      if (!isMandatory && query.isAlias()) {
        return INFO_EDITOR_OPTION_RESET_DEFAULT_ALIAS.get(query
            .getAliasDescription());
      } else if (query.isDefined()) {
        // Only show this option if the current value is different
        // to the default.
        if (!currentValues.equals(defaultValues)) {
          Message svalue = getPropertyValues(pd, defaultValues);
          if (defaultValues.size() > 1) {
            return INFO_EDITOR_OPTION_RESET_DEFAULT_VALUES.get(svalue);
          } else {
            return INFO_EDITOR_OPTION_RESET_DEFAULT_VALUE.get(svalue);
          }
        } else {
          return null;
        }
      } else if (!isMandatory && query.isInherited()) {
        if (defaultValues.isEmpty()) {
          if (query.getAliasDescription() != null) {
            return INFO_EDITOR_OPTION_RESET_DEFAULT_INHERITED_ALIAS.get(query
                .getAliasDescription());
          } else {
            return INFO_EDITOR_OPTION_RESET_DEFAULT_INHERITED_ALIAS_UNDEFINED
                .get();
          }
        } else {
          Message svalue = getPropertyValues(pd, defaultValues);
          if (defaultValues.size() > 1) {
            return INFO_EDITOR_OPTION_RESET_INHERITED_DEFAULT_VALUES
                .get(svalue);
          } else {
            return INFO_EDITOR_OPTION_RESET_INHERITED_DEFAULT_VALUE.get(svalue);
          }
        }
      } else if (!isMandatory && query.isUndefined()) {
        return INFO_EDITOR_OPTION_LEAVE_UNDEFINED.get();
      } else {
        return null;
      }
    }



    // Common menu processing.
    private <T> MenuResult<Boolean> runMenu(final PropertyDefinition<T> d,
        ConsoleApplication app, final SortedSet<T> defaultValues,
        final SortedSet<T> oldValues, final SortedSet<T> currentValues,
        MenuCallback<Boolean> addCallback,
        MenuCallback<Boolean> removeCallback) {
      // Construct a menu of actions.
      MenuBuilder<Boolean> builder = new MenuBuilder<Boolean>(app);
      builder.setPrompt(INFO_EDITOR_PROMPT_MODIFY_MENU.get(d.getName()));

      // First option is for leaving the property unchanged or
      // applying changes, but only if the state of the property is
      // valid.
      if (!(d.hasOption(PropertyOption.MANDATORY) && currentValues.isEmpty())) {
        MenuResult<Boolean> result;
        if (!oldValues.equals(currentValues)) {
          result = MenuResult.success(true);
        } else {
          result = MenuResult.<Boolean> cancel();
        }

        Message option = getKeepDefaultValuesMenuOption(d, defaultValues,
            oldValues, currentValues);
        builder.addNumberedOption(option, result);
        builder.setDefault(Message.raw("1"), result);
      }

      // Add an option for adding some values.
      if (addCallback != null) {
        int i = builder.addNumberedOption(
            INFO_EDITOR_OPTION_ADD_ONE_OR_MORE_VALUES.get(), addCallback);
        if (d.hasOption(PropertyOption.MANDATORY) && currentValues.isEmpty()) {
          builder.setDefault(Message.raw("%d", i), addCallback);
        }
      }

      // Add options for removing values if applicable.
      if (!currentValues.isEmpty()) {
        builder.addNumberedOption(INFO_EDITOR_OPTION_REMOVE_ONE_OR_MORE_VALUES
            .get(), removeCallback);
      }

      // Add options for removing all values and for resetting the
      // property to its default behavior.
      Message resetOption = null;
      if (!currentValues.equals(defaultValues)) {
        resetOption = getResetToDefaultValuesMenuOption(d, defaultValues,
            currentValues);
      }

      if (!currentValues.isEmpty()) {
        if (resetOption == null || !defaultValues.isEmpty()) {
          MenuCallback<Boolean> callback = new MenuCallback<Boolean>() {

            public MenuResult<Boolean> invoke(ConsoleApplication app)
                throws CLIException {
              isLastChoiceReset = false;
              currentValues.clear();
              app.println();
              app.pressReturnToContinue();
              return MenuResult.success(false);
            }

          };

          builder.addNumberedOption(INFO_EDITOR_OPTION_REMOVE_ALL_VALUES.get(),
              callback);
        }
      }

      if (resetOption != null) {
        MenuCallback<Boolean> callback = new MenuCallback<Boolean>() {

          public MenuResult<Boolean> invoke(ConsoleApplication app)
              throws CLIException {
            currentValues.clear();
            currentValues.addAll(defaultValues);
            isLastChoiceReset = true;
            app.println();
            app.pressReturnToContinue();
            return MenuResult.success(false);
          }

        };

        builder.addNumberedOption(resetOption, callback);
      }

      // Add an option for undoing any changes.
      if (!oldValues.equals(currentValues)) {
        MenuCallback<Boolean> callback = new MenuCallback<Boolean>() {

          public MenuResult<Boolean> invoke(ConsoleApplication app)
              throws CLIException {
            currentValues.clear();
            currentValues.addAll(oldValues);
            isLastChoiceReset = false;
            app.println();
            app.pressReturnToContinue();
            return MenuResult.success(false);
          }

        };

        builder.addNumberedOption(INFO_EDITOR_OPTION_REVERT_CHANGES.get(),
            callback);
      }

      builder.addHelpOption(new PropertyHelpCallback(mo
          .getManagedObjectDefinition(), d));
      builder.addQuitOption();

      Menu<Boolean> menu = builder.toMenu();
      MenuResult<Boolean> result;
      try {
        app.println();
        result = menu.run();
      } catch (CLIException e) {
        this.e = e;
        return null;
      }

      if (result.isSuccess()) {
        if (result.getValue() == true) {

          // Set the new property value(s).
          mo.setPropertyValues(d, currentValues);

          registerModification(d, currentValues, oldValues);

          app.println();
          app.pressReturnToContinue();
          return MenuResult.success(false);
        } else {
          // Continue until cancel/apply changes.
          app.println();
          return MenuResult.again();
        }
      } else if (result.isCancel()) {
        app.println();
        app.pressReturnToContinue();
        return MenuResult.success(false);
      } else {
        return MenuResult.quit();
      }
    }
  }



  /**
   * A help call-back which displays a description and summary of a
   * single property.
   */
  private static final class PropertyHelpCallback implements HelpCallback {

    // The managed object definition.
    private final ManagedObjectDefinition<?, ?> d;

    // The property to be edited.
    private final PropertyDefinition<?> pd;



    // Creates a new property helper for the specified property.
    private PropertyHelpCallback(ManagedObjectDefinition<?, ?> d,
        PropertyDefinition<?> pd) {
      this.d = d;
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    public void display(ConsoleApplication app) {
      app.println();
      HelpSubCommandHandler.displayVerboseSingleProperty(app, d, pd.getName());
      app.println();
      app.pressReturnToContinue();
    }
  }



  /**
   * A menu call-back for viewing a read-only properties.
   */
  private final class ReadOnlyPropertyViewer extends
      PropertyDefinitionVisitor<MenuResult<Boolean>, Void> implements
      MenuCallback<Boolean> {

    // Any exception that was caught during processing.
    private CLIException e = null;

    // The managed object being edited.
    private final ManagedObject<?> mo;

    // The property to be edited.
    private final PropertyDefinition<?> pd;



    // Creates a new property editor for the specified property.
    private ReadOnlyPropertyViewer(ManagedObject<?> mo,
        PropertyDefinition<?> pd) {
      this.mo = mo;
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    public MenuResult<Boolean> invoke(ConsoleApplication app)
        throws CLIException {
      MenuResult<Boolean> result = pd.accept(this, null);
      if (e != null) {
        throw e;
      } else {
        return result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <T> MenuResult<Boolean> visitUnknown(PropertyDefinition<T> pd,
        Void p) {
      SortedSet<T> values = mo.getPropertyValues(pd);

      app.println();
      app.println();
      switch (values.size()) {
      case 0:
        // Only alias, undefined, or inherited alias or undefined
        // properties should apply here.
        DefaultBehaviorQuery<T> query = DefaultBehaviorQuery.query(pd);
        Message aliasDescription = query.getAliasDescription();
        if (aliasDescription == null) {
          app.println(INFO_EDITOR_HEADING_READ_ONLY_ALIAS_UNDEFINED.get(pd
              .getName()));
        } else {
          app.println(INFO_EDITOR_HEADING_READ_ONLY_ALIAS.get(pd.getName(),
              aliasDescription));
        }
        break;
      case 1:
        Message svalue = getPropertyValues(pd, mo);
        app.println(INFO_EDITOR_HEADING_READ_ONLY_VALUE.get(pd.getName(),
            svalue));
        break;
      default:
        app.println(INFO_EDITOR_HEADING_READ_ONLY_VALUES.get(pd.getName()));
        app.println();
        displayPropertyValues(app, pd, values);
        break;
      }

      app.println();
      boolean result;
      try {
        result = app.confirmAction(INFO_EDITOR_PROMPT_READ_ONLY.get(), false);
      } catch (CLIException e) {
        this.e = e;
        return null;
      }

      if (result) {
        app.println();
        HelpSubCommandHandler.displayVerboseSingleProperty(app, mo
            .getManagedObjectDefinition(), pd.getName());
        app.println();
        app.pressReturnToContinue();
      }

      return MenuResult.again();
    }
  }



  /**
   * A menu call-back for editing a modifiable single-valued property.
   */
  private final class SingleValuedPropertyEditor extends
      PropertyDefinitionVisitor<MenuResult<Boolean>, Void>
      implements MenuCallback<Boolean> {

    // Any exception that was caught during processing.
    private CLIException e = null;

    // The managed object being edited.
    private final ManagedObject<?> mo;

    // The property to be edited.
    private final PropertyDefinition<?> pd;



    // Creates a new property editor for the specified property.
    private SingleValuedPropertyEditor(ManagedObject<?> mo,
        PropertyDefinition<?> pd) {
      Validator.ensureTrue(!pd.hasOption(PropertyOption.MULTI_VALUED));

      this.mo = mo;
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    public MenuResult<Boolean> invoke(ConsoleApplication app)
        throws CLIException {
      displayPropertyHeader(app, pd);

      MenuResult<Boolean> result = pd.accept(this, null);
      if (e != null) {
        throw e;
      } else {
        return result;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends ConfigurationClient, S extends Configuration>
        MenuResult<Boolean> visitAggregation(
        AggregationPropertyDefinition<C, S> d, Void p) {
      // Construct a menu of actions.
      MenuBuilder<String> builder = new MenuBuilder<String>(app);
      builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);
      builder.setPrompt(INFO_EDITOR_PROMPT_MODIFY_MENU.get(d.getName()));

      DefaultBehaviorQuery<String> query = DefaultBehaviorQuery.query(d);
      SortedSet<String> currentValues = mo.getPropertyValues(d);
      SortedSet<String> defaultValues = mo.getPropertyDefaultValues(d);
      String currentValue = currentValues.isEmpty() ? null : currentValues
          .first();
      String defaultValue = defaultValues.isEmpty() ? null : defaultValues
          .first();

      // First option is for leaving the property unchanged.
      Message option = getKeepDefaultValuesMenuOption(d);
      builder.addNumberedOption(option, MenuResult.<String> cancel());
      builder.setDefault(Message.raw("1"), MenuResult.<String> cancel());

      // Create a list of possible names.
      final Set<String> values = new TreeSet<String>(d);
      ManagedObjectPath<?,?> path = d.getParentPath();
      InstantiableRelationDefinition<C, S> rd = d.getRelationDefinition();
      try {
        values.addAll(Arrays.asList(context.listManagedObjects(path, rd)));
      } catch (AuthorizationException e) {
        this.e = new CLIException(e.getMessageObject());
        return MenuResult.quit();
      } catch (ManagedObjectNotFoundException e) {
        this.e = new CLIException(e.getMessageObject());
        return MenuResult.cancel();
      } catch (CommunicationException e) {
        this.e = new CLIException(e.getMessageObject());
        return MenuResult.quit();
      }

      final Message ufn = rd.getUserFriendlyName();
      for (String value : values) {
        if (currentValue != null && d.compare(value, currentValue) == 0) {
          // This option is unnecessary.
          continue;
        }

        Message svalue = getPropertyValues(d, Collections.singleton(value));
        if (value.equals(defaultValue) && query.isDefined()) {
          option = INFO_EDITOR_OPTION_CHANGE_TO_DEFAULT_COMPONENT.get(ufn,
              svalue);
        } else {
          option = INFO_EDITOR_OPTION_CHANGE_TO_COMPONENT.get(ufn, svalue);
        }

        builder.addNumberedOption(option, MenuResult.success(value));
      }
      MenuCallback<String> callback = new CreateComponentCallback<C, S>(d);
      builder.addNumberedOption(INFO_EDITOR_OPTION_CREATE_A_NEW_COMPONENT
          .get(ufn), callback);

      // Third option is to reset the value back to its default.
      if (mo.isPropertyPresent(d) && !query.isDefined()) {
        option = getResetToDefaultValuesMenuOption(d);
        if (option != null) {
          builder.addNumberedOption(option, MenuResult.<String> success());
        }
      }

      return runMenu(d, builder);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public MenuResult<Boolean> visitBoolean(BooleanPropertyDefinition d,
        Void p) {
      // Construct a menu of actions.
      MenuBuilder<Boolean> builder = new MenuBuilder<Boolean>(app);
      builder.setPrompt(INFO_EDITOR_PROMPT_MODIFY_MENU.get(d.getName()));

      DefaultBehaviorQuery<Boolean> query = DefaultBehaviorQuery.query(d);
      SortedSet<Boolean> currentValues = mo.getPropertyValues(d);
      SortedSet<Boolean> defaultValues = mo.getPropertyDefaultValues(d);

      Boolean currentValue = currentValues.isEmpty() ? null : currentValues
          .first();
      Boolean defaultValue = defaultValues.isEmpty() ? null : defaultValues
          .first();

      // First option is for leaving the property unchanged.
      Message option = getKeepDefaultValuesMenuOption(d);
      builder.addNumberedOption(option, MenuResult.<Boolean> cancel());
      builder.setDefault(Message.raw("1"), MenuResult.<Boolean> cancel());

      // The second (and possibly third) option is to always change
      // the property's value.
      if (currentValue == null || currentValue == false) {
        Message svalue = getPropertyValues(d, Collections.singleton(true));

        if (defaultValue != null && defaultValue == true) {
          option = INFO_EDITOR_OPTION_CHANGE_TO_DEFAULT_VALUE.get(svalue);
        } else {
          option = INFO_EDITOR_OPTION_CHANGE_TO_VALUE.get(svalue);
        }

        builder.addNumberedOption(option, MenuResult.success(true));
      }

      if (currentValue == null || currentValue == true) {
        Message svalue = getPropertyValues(d, Collections.singleton(false));

        if (defaultValue != null && defaultValue == false) {
          option = INFO_EDITOR_OPTION_CHANGE_TO_DEFAULT_VALUE.get(svalue);
        } else {
          option = INFO_EDITOR_OPTION_CHANGE_TO_VALUE.get(svalue);
        }

        builder.addNumberedOption(option, MenuResult.success(false));
      }

      // Final option is to reset the value back to its default.
      if (mo.isPropertyPresent(d) && !query.isDefined()) {
        option = getResetToDefaultValuesMenuOption(d);
        if (option != null) {
          builder.addNumberedOption(option, MenuResult.<Boolean> success());
        }
      }

      return runMenu(d, builder);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> MenuResult<Boolean> visitEnum(
        EnumPropertyDefinition<E> d, Void p) {
      // Construct a menu of actions.
      MenuBuilder<E> builder = new MenuBuilder<E>(app);
      builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);
      builder.setPrompt(INFO_EDITOR_PROMPT_MODIFY_MENU.get(d.getName()));

      DefaultBehaviorQuery<E> query = DefaultBehaviorQuery.query(d);
      SortedSet<E> currentValues = mo.getPropertyValues(d);
      SortedSet<E> defaultValues = mo.getPropertyDefaultValues(d);
      E currentValue = currentValues.isEmpty() ? null : currentValues.first();
      E defaultValue = defaultValues.isEmpty() ? null : defaultValues.first();

      // First option is for leaving the property unchanged.
      Message option = getKeepDefaultValuesMenuOption(d);
      builder.addNumberedOption(option, MenuResult.<E> cancel());
      builder.setDefault(Message.raw("1"), MenuResult.<E> cancel());

      // Create options for changing to other values.
      Set<E> values = new TreeSet<E>(d);
      values.addAll(EnumSet.allOf(d.getEnumClass()));
      for (E value : values) {
        if (value.equals(currentValue) && query.isDefined()) {
          // This option is unnecessary.
          continue;
        }

        Message svalue = getPropertyValues(d, Collections.singleton(value));

        if (value.equals(defaultValue) && query.isDefined()) {
          option = INFO_EDITOR_OPTION_CHANGE_TO_DEFAULT_VALUE.get(svalue);
        } else {
          option = INFO_EDITOR_OPTION_CHANGE_TO_VALUE.get(svalue);
        }

        builder.addNumberedOption(option, MenuResult.success(value));
      }

      // Third option is to reset the value back to its default.
      if (mo.isPropertyPresent(d) && !query.isDefined()) {
        option = getResetToDefaultValuesMenuOption(d);
        if (option != null) {
          builder.addNumberedOption(option, MenuResult.<E> success());
        }
      }

      return runMenu(d, builder);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <T> MenuResult<Boolean> visitUnknown(final PropertyDefinition<T> d,
        Void p) {
      app.println();
      displayPropertySyntax(app, d);

      // Construct a menu of actions.
      MenuBuilder<T> builder = new MenuBuilder<T>(app);
      builder.setPrompt(INFO_EDITOR_PROMPT_MODIFY_MENU.get(d.getName()));

      // First option is for leaving the property unchanged.
      Message option = getKeepDefaultValuesMenuOption(d);
      builder.addNumberedOption(option, MenuResult.<T> cancel());
      builder.setDefault(Message.raw("1"), MenuResult.<T> cancel());

      // The second option is to always change the property's value.
      builder.addNumberedOption(INFO_EDITOR_OPTION_CHANGE_VALUE.get(),
          new MenuCallback<T>() {

            public MenuResult<T> invoke(ConsoleApplication app)
                throws CLIException {
              app.println();
              Set<T> values = readPropertyValues(app, mo
                  .getManagedObjectDefinition(), d);
              return MenuResult.success(values);
            }

          });

      // Third option is to reset the value back to its default.
      if (mo.isPropertyPresent(d)) {
        option = getResetToDefaultValuesMenuOption(d);
        if (option != null) {
          builder.addNumberedOption(option, MenuResult.<T> success());
        }
      }

      return runMenu(d, builder);
    }



    /**
     * Generate an appropriate menu option for a property which asks
     * the user whether or not they want to keep the property's
     * current settings.
     */
    private <T> Message getKeepDefaultValuesMenuOption(
        PropertyDefinition<T> pd) {
      DefaultBehaviorQuery<T> query = DefaultBehaviorQuery.query(pd);
      SortedSet<T> currentValues = mo.getPropertyValues(pd);
      SortedSet<T> defaultValues = mo.getPropertyDefaultValues(pd);

      if (query.isDefined() && currentValues.equals(defaultValues)) {
        Message svalue = getPropertyValues(pd, currentValues);
        return INFO_EDITOR_OPTION_KEEP_DEFAULT_VALUE.get(svalue);
      } else if (mo.isPropertyPresent(pd)) {
        Message svalue = getPropertyValues(pd, currentValues);
        return INFO_EDITOR_OPTION_KEEP_VALUE.get(svalue);
      } else if (query.isAlias()) {
        return INFO_EDITOR_OPTION_KEEP_DEFAULT_ALIAS.get(query
            .getAliasDescription());
      } else if (query.isInherited()) {
        if (defaultValues.isEmpty()) {
          if (query.getAliasDescription() != null) {
            return INFO_EDITOR_OPTION_KEEP_DEFAULT_INHERITED_ALIAS.get(query
                .getAliasDescription());
          } else {
            return INFO_EDITOR_OPTION_KEEP_DEFAULT_INHERITED_ALIAS_UNDEFINED
                .get();
          }
        } else {
          Message svalue = getPropertyValues(pd, defaultValues);
          return INFO_EDITOR_OPTION_KEEP_INHERITED_DEFAULT_VALUE.get(svalue);
        }
      } else {
        return INFO_EDITOR_OPTION_LEAVE_UNDEFINED.get();
      }
    }



    /**
     * Generate an appropriate menu option which should be used in the
     * case where a property can be reset to its default behavior.
     */
    private <T> Message getResetToDefaultValuesMenuOption(
        PropertyDefinition<T> pd) {
      DefaultBehaviorQuery<T> query = DefaultBehaviorQuery.query(pd);
      SortedSet<T> currentValues = mo.getPropertyValues(pd);
      SortedSet<T> defaultValues = mo.getPropertyDefaultValues(pd);

      boolean isMandatory = pd.hasOption(PropertyOption.MANDATORY);

      if (!isMandatory && query.isAlias()) {
        return INFO_EDITOR_OPTION_RESET_DEFAULT_ALIAS.get(query
            .getAliasDescription());
      } else if (query.isDefined()) {
        // Only show this option if the current value is different
        // to the default.
        if (!currentValues.equals(defaultValues)) {
          Message svalue = getPropertyValues(pd, defaultValues);
          return INFO_EDITOR_OPTION_RESET_DEFAULT_VALUE.get(svalue);
        } else {
          return null;
        }
      } else if (!isMandatory && query.isInherited()) {
        if (defaultValues.isEmpty()) {
          if (query.getAliasDescription() != null) {
            return INFO_EDITOR_OPTION_RESET_DEFAULT_INHERITED_ALIAS.get(query
                .getAliasDescription());
          } else {
            return INFO_EDITOR_OPTION_RESET_DEFAULT_INHERITED_ALIAS_UNDEFINED
                .get();
          }
        } else {
          Message svalue = getPropertyValues(pd, defaultValues);
          return INFO_EDITOR_OPTION_RESET_INHERITED_DEFAULT_VALUE.get(svalue);
        }
      } else if (!isMandatory && query.isUndefined()) {
        return INFO_EDITOR_OPTION_LEAVE_UNDEFINED.get();
      } else {
        return null;
      }
    }



    // Common menu processing.
    private <T> MenuResult<Boolean> runMenu(final PropertyDefinition<T> d,
        MenuBuilder<T> builder) throws IllegalPropertyValueException,
        PropertyIsSingleValuedException, PropertyIsReadOnlyException,
        PropertyIsMandatoryException, IllegalArgumentException {
      builder.addHelpOption(new PropertyHelpCallback(mo
          .getManagedObjectDefinition(), d));
      builder.addQuitOption();

      Menu<T> menu = builder.toMenu();
      MenuResult<T> result;
      try {
        app.println();
        result = menu.run();
      } catch (CLIException e) {
        this.e = e;
        return null;
      }

      if (result.isSuccess()) {
        // Set the new property value(s).
        Collection<T> newValues = result.getValues();
        SortedSet<T> oldValues = new TreeSet<T>(mo.getPropertyValues(d));
        mo.setPropertyValues(d, newValues);
        if (newValues.size() > 0)
        {
          isLastChoiceReset = false;
        }
        else
        {
          // There are no newValues when we do a reset.
          isLastChoiceReset = true;
        }
        registerModification(d, new TreeSet<T>(newValues), oldValues);
        app.println();
        app.pressReturnToContinue();
        return MenuResult.success(false);
      } else if (result.isCancel()) {
        app.println();
        app.pressReturnToContinue();
        return MenuResult.success(false);
      } else {
        return MenuResult.quit();
      }
    }
  }



  // Display a title and a description of the property.
  private static void displayPropertyHeader(ConsoleApplication app,
      PropertyDefinition<?> pd) {
    app.println();
    app.println();
    app.println(INFO_EDITOR_HEADING_CONFIGURE_PROPERTY.get(pd.getName()));
    app.println();
    app.println(pd.getSynopsis(), 4);
    if (pd.getDescription() != null) {
      app.println();
      app.println(pd.getDescription(), 4);
    }
  }



  // Display a property's syntax.
  private static <T> void displayPropertySyntax(ConsoleApplication app,
      PropertyDefinition<T> d) throws IllegalArgumentException {
    PropertyDefinitionUsageBuilder b = new PropertyDefinitionUsageBuilder(true);

    TableBuilder builder = new TableBuilder();
    builder.startRow();
    builder.appendCell(INFO_EDITOR_HEADING_SYNTAX.get());
    builder.appendCell(b.getUsage(d));

    TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
    printer.setDisplayHeadings(false);
    printer.setIndentWidth(4);
    printer.setColumnWidth(1, 0);
    builder.print(printer);
  }



  // Display a table of property values.
  private static <T> void displayPropertyValues(ConsoleApplication app,
      PropertyDefinition<T> pd, Collection<T> values)
      throws IllegalArgumentException {
    TableBuilder builder = new TableBuilder();
    PropertyValuePrinter valuePrinter = new PropertyValuePrinter(null,
        null, false);

    int sz = values.size();
    boolean useMultipleColumns = (sz >= MULTI_COLUMN_THRESHOLD);
    int rows = sz;
    if (useMultipleColumns) {
      // Display in two columns the first column should contain
      // half the values. If there are an odd number of columns
      // then the first column should contain an additional value
      // (e.g. if there are 23 values, the first column should
      // contain 12 values and the second column 11 values).
      rows /= 2;
      rows += sz % 2;
    }

    List<T> vl = new ArrayList<T>(values);
    for (int i = 0, j = rows; i < rows; i++, j++) {
      builder.startRow();
      builder.appendCell("*)");
      builder.appendCell(valuePrinter.print(pd, vl.get(i)));

      if (useMultipleColumns && (j < sz)) {
        builder.appendCell();
        builder.appendCell("*)");
        builder.appendCell(valuePrinter.print(pd, vl.get(j)));
      }
    }

    TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
    printer.setDisplayHeadings(false);
    printer.setIndentWidth(4);
    printer.setColumnWidth(1, 0);
    if (useMultipleColumns) {
      printer.setColumnWidth(2, 2);
      printer.setColumnWidth(4, 0);
    }
    builder.print(printer);
  }



  // Display the set of values associated with a property.
  private static <T> Message getPropertyValues(PropertyDefinition<T> pd,
      Collection<T> values) {
    if (values.isEmpty()) {
      // There are no values or default values. Display the default
      // behavior for alias values.
      DefaultBehaviorQuery<T> query = DefaultBehaviorQuery.query(pd);
      Message content = query.getAliasDescription();
      if (content == null) {
        return Message.raw("-");
      } else {
        return content;
      }
    } else {
      PropertyValuePrinter printer =
        new PropertyValuePrinter(null, null, false);
      MessageBuilder builder = new MessageBuilder();

      boolean isFirst = true;
      for (T value : values) {
        if (!isFirst) {
          builder.append(", ");
        }
        builder.append(printer.print(pd, value));
        isFirst = false;
      }

      return builder.toMessage();
    }
  }



  // Display the set of values associated with a property.
  private static <T> Message getPropertyValues(
      PropertyDefinition<T> pd,
      ManagedObject<?> mo) {
    SortedSet<T> values = mo.getPropertyValues(pd);
    return getPropertyValues(pd, values);
  }



  // Read new values for a property.
  private static <T> SortedSet<T> readPropertyValues(ConsoleApplication app,
      ManagedObjectDefinition<?, ?> d, PropertyDefinition<T> pd)
      throws CLIException {
    SortedSet<T> values = new TreeSet<T>(pd);
    readPropertyValues(app, d, pd, values);
    return values;
  }



  // Add values to a property.
  private static <T> void readPropertyValues(ConsoleApplication app,
      ManagedObjectDefinition<?, ?> d, PropertyDefinition<T> pd,
      SortedSet<T> values) throws CLIException {
    // Make sure there is at least one value if mandatory and empty.
    if (values.isEmpty()) {
      while (true) {
        try {
          Message prompt;

          if (pd.hasOption(PropertyOption.MANDATORY)) {
            prompt = INFO_EDITOR_PROMPT_READ_FIRST_VALUE.get(pd.getName());
          } else {
            prompt = INFO_EDITOR_PROMPT_READ_FIRST_VALUE_OPTIONAL.get(pd
                .getName());
          }

          app.println();
          String s = app.readLineOfInput(prompt);
          if (s.trim().length() == 0) {
            if (!pd.hasOption(PropertyOption.MANDATORY)) {
              return;
            }
          }

          T value = pd.decodeValue(s);
          if (values.contains(value)) {
            // Prevent addition of duplicates.
            app.println();
            app.println(ERR_EDITOR_READ_FIRST_DUPLICATE.get(s));
          } else {
            values.add(value);
          }

          break;
        } catch (IllegalPropertyValueStringException e) {
          app.println();
          app.println(ArgumentExceptionFactory.adaptPropertyException(e, d)
              .getMessageObject());
        }
      }
    }

    if (pd.hasOption(PropertyOption.MULTI_VALUED)) {
      // Prompt for more values if multi-valued.
      while (true) {
        try {
          Message prompt = INFO_EDITOR_PROMPT_READ_NEXT_VALUE.get(pd.getName());

          app.println();
          String s = app.readLineOfInput(prompt);
          if (s.trim().length() == 0) {
            return;
          }

          T value = pd.decodeValue(s);
          if (values.contains(value)) {
            // Prevent addition of duplicates.
            app.println();
            app.println(ERR_EDITOR_READ_NEXT_DUPLICATE.get(s));
          } else {
            values.add(value);
          }
        } catch (IllegalPropertyValueStringException e) {
          app.println();
          app.println(ArgumentExceptionFactory.adaptPropertyException(e, d)
              .getMessageObject());
          app.println();
        }
      }
    }
  }

  // The threshold above which choice menus should be displayed in
  // multiple columns.
  private static final int MULTI_COLUMN_THRESHOLD = 8;

  // The application console.
  private final ConsoleApplication app;

  // The management context.
  private final ManagementContext context;

  // The modifications performed: we assume that at most there is one
  // modification per property definition.
  private final List<PropertyEditorModification> mods =
    new ArrayList<PropertyEditorModification>();

  // Whether the last type of choice made by the user in a menu is a
  // reset
  private boolean isLastChoiceReset;



  /**
   * Create a new property value editor which will read from the
   * provided application console.
   *
   * @param app
   *          The application console.
   * @param context
   *          The management context.
   */
  public PropertyValueEditor(ConsoleApplication app,
      ManagementContext context) {
    this.app = app;
    this.context = context;
  }



  /**
   * Interactively edits the properties of a managed object. Only the
   * properties listed in the provided collection will be accessible
   * to the client. It is up to the caller to ensure that the list of
   * properties does not include read-only, monitoring, hidden, or
   * advanced properties as appropriate.
   *
   * @param mo
   *          The managed object.
   * @param c
   *          The collection of properties which can be edited.
   * @param isCreate
   *          Flag indicating whether or not the managed object is
   *          being created. If it is then read-only properties will
   *          be modifiable.
   * @return Returns {@code MenuResult.success()} if the changes made
   *         to the managed object should be applied, or
   *         {@code MenuResult.cancel()} if the user to chose to
   *         cancel any changes, or {@code MenuResult.quit()} if the
   *         user chose to quit the application.
   * @throws CLIException
   *           If the user input could not be retrieved for some
   *           reason.
   */
  public MenuResult<Void> edit(ManagedObject<?> mo,
      Collection<PropertyDefinition<?>> c, boolean isCreate)
      throws CLIException {

    // Get values for this missing mandatory property.
    for (PropertyDefinition<?> pd : c) {
      if (pd.hasOption(PropertyOption.MANDATORY)) {
        if (mo.getPropertyValues(pd).isEmpty()) {
          MandatoryPropertyInitializer mpi = new MandatoryPropertyInitializer(
              mo, pd);
          MenuResult<Void> result = mpi.invoke(app);
          if (!result.isSuccess()) {
            return result;
          }
        }
      }
    }

    while (true) {
      // Construct the main menu.
      MenuBuilder<Boolean> builder = new MenuBuilder<Boolean>(app);

      Message ufn = mo.getManagedObjectDefinition().getUserFriendlyName();
      builder.setPrompt(INFO_EDITOR_HEADING_CONFIGURE_COMPONENT.get(ufn));

      Message heading1 = INFO_DSCFG_HEADING_PROPERTY_NAME.get();
      Message heading2 = INFO_DSCFG_HEADING_PROPERTY_VALUE.get();
      builder.setColumnHeadings(heading1, heading2);
      builder.setColumnWidths(null, 0);

      // Create an option for editing/viewing each property.
      for (PropertyDefinition<?> pd : c) {
        // Determine whether this property should be modifiable.
        boolean isReadOnly = false;

        if (pd.hasOption(PropertyOption.MONITORING)) {
          isReadOnly = true;
        }

        if (!isCreate && pd.hasOption(PropertyOption.READ_ONLY)) {
          isReadOnly = true;
        }

        // Create the appropriate property action.
        MenuCallback<Boolean> callback;
        if (pd.hasOption(PropertyOption.MULTI_VALUED)) {
          if (isReadOnly) {
            callback = new ReadOnlyPropertyViewer(mo, pd);
          } else {
            callback = new MultiValuedPropertyEditor(mo, pd);
          }
        } else {
          if (isReadOnly) {
            callback = new ReadOnlyPropertyViewer(mo, pd);
          } else {
            callback = new SingleValuedPropertyEditor(mo, pd);
          }
        }

        // Create the numeric option.
        Message values = getPropertyValues(pd, mo);
        builder.addNumberedOption(Message.raw("%s", pd.getName()), callback,
            values);
      }

      // Add a help option which displays a summary of the managed
      // object's definition.
      HelpCallback helpCallback = new ComponentHelpCallback(mo, c);
      builder.addHelpOption(helpCallback);

      // Add an option to apply the changes.
      if (isCreate) {
        builder.addCharOption(INFO_EDITOR_OPTION_FINISH_KEY.get(),
            INFO_EDITOR_OPTION_FINISH_CREATE_COMPONENT.get(ufn), MenuResult
                .success(true));
      } else {
        builder.addCharOption(INFO_EDITOR_OPTION_FINISH_KEY.get(),
            INFO_EDITOR_OPTION_FINISH_MODIFY_COMPONENT.get(ufn), MenuResult
                .success(true));
      }

      builder.setDefault(INFO_EDITOR_OPTION_FINISH_KEY.get(), MenuResult
          .success(true));

      // Add options for canceling and quitting.
      if (app.isMenuDrivenMode()) {
        builder.addCancelOption(false);
      }
      builder.addQuitOption();

      // Run the menu - success indicates that any changes should be
      // committed.
      app.println();
      app.println();
      Menu<Boolean> menu = builder.toMenu();
      MenuResult<Boolean> result = menu.run();

      if (result.isSuccess()) {
        if (result.getValue()) {
          return MenuResult.<Void>success();
        }
      } else if (result.isCancel()) {
        return MenuResult.cancel();
      } else {
        return MenuResult.quit();
      }
    }
  }

  /**
   * Register the modification in the list of modifications.
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param pd the property definition.
   * @param newValues the resulting values of the property once the
   * modification is applied.
   * @param previousValues the values we had before the modification is applied
   * (these are not necessarily the *original* values if we already have other
   * modifications applied to the same property).
   */
  private <T> void registerModification(PropertyDefinition<T> pd,
      SortedSet<T> newValues, SortedSet<T> previousValues)
  {

    if (isLastChoiceReset)
    {
      registerResetModification(pd, previousValues);
    }
    else if (!newValues.equals(previousValues))
    {
      if (newValues.containsAll(previousValues))
      {
        registerAddModification(pd, newValues, previousValues);
      }
      else if (previousValues.containsAll(newValues))
      {
        registerRemoveModification(pd, newValues, previousValues);
      }
      else
      {
        registerSetModification(pd, newValues, previousValues);
      }
    }
  }

  /**
   * Register a reset modification in the list of modifications.
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param pd the property definition.
   * @param previousValues the values we had before the modification is applied
   * (these are not necessarily the *original* values if we already have other
   * modifications applied to the same property).
   */
  private <T> void registerResetModification(PropertyDefinition<T> pd,
      SortedSet<T> previousValues)
  {
    PropertyEditorModification<?> mod = getModification(pd);
    SortedSet<T> originalValues;
    if (mod != null)
    {
      originalValues = new TreeSet<T>(pd);
      castAndAddValues(originalValues, mod.getOriginalValues(), pd);
      removeModification(mod);
    }
    else
    {
      originalValues = new TreeSet<T>(previousValues);
    }

    addModification(PropertyEditorModification.createResetModification(pd,
        originalValues));
  }

  /**
   * Register a set modification in the list of modifications.
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param pd the property definition.
   * @param newValues the resulting values of the property once the
   * modification is applied.
   * @param previousValues the values we had before the modification is applied
   * (these are not necessarily the *original* values if we already have other
   * modifications applied to the same property).
   */
  private <T> void registerSetModification(PropertyDefinition<T> pd,
      SortedSet<T> newValues, SortedSet<T> previousValues)
  {
    PropertyEditorModification<?> mod = getModification(pd);
    SortedSet<T> originalValues;
    if (mod != null)
    {
      originalValues = new TreeSet<T>(pd);
      castAndAddValues(originalValues, mod.getOriginalValues(), pd);
      removeModification(mod);
    }
    else
    {
      originalValues = new TreeSet<T>(previousValues);
    }
    addModification(PropertyEditorModification.createSetModification(pd,
        newValues, originalValues));
  }

  /**
   * Register an add modification in the list of modifications.
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param pd the property definition.
   * @param newValues the resulting values of the property once the
   * modification is applied.
   * @param previousValues the values we had before the modification is applied
   * (these are not necessarily the *original* values if we already have other
   * modifications applied to the same property).
   */
  private <T> void registerAddModification(PropertyDefinition<T> pd,
      SortedSet<T> newValues, SortedSet<T> previousValues)
  {
    PropertyEditorModification<?> mod = getModification(pd);
    PropertyEditorModification<T> newMod;
    SortedSet<T> originalValues;
    if (mod != null)
    {
      originalValues = new TreeSet<T>(pd);
      castAndAddValues(originalValues, mod.getOriginalValues(), pd);
      if (mod.getType() == PropertyEditorModification.Type.ADD)
      {
        SortedSet<T> addedValues = new TreeSet<T>(newValues);
        addedValues.removeAll(originalValues);
        newMod = PropertyEditorModification.createAddModification(pd,
            addedValues, originalValues);
      }
      else
      {
        newMod = PropertyEditorModification.createSetModification(pd,
            new TreeSet<T>(newValues), originalValues);
      }
      removeModification(mod);
    }
    else
    {
      originalValues = new TreeSet<T>(previousValues);
      SortedSet<T> addedValues = new TreeSet<T>(newValues);
      addedValues.removeAll(originalValues);
      newMod = PropertyEditorModification.createAddModification(pd,
          addedValues, originalValues);
    }
    addModification(newMod);
  }

  /**
   * Register a remove modification in the list of modifications.
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param pd the property definition.
   * @param newValues the resulting values of the property once the
   * modification is applied.
   * @param previousValues the values we had before the modification is applied
   * (these are not necessarily the *original* values if we already have other
   * modifications applied to the same property).
   */
  private <T> void registerRemoveModification(PropertyDefinition<T> pd,
      SortedSet<T> newValues, SortedSet<T> previousValues)
  {
    PropertyEditorModification<?> mod = getModification(pd);
    PropertyEditorModification<T> newMod;
    SortedSet<T> originalValues;
    if (mod != null)
    {
      originalValues = new TreeSet<T>(pd);
      castAndAddValues(originalValues, mod.getOriginalValues(), pd);
      if (newValues.isEmpty())
      {
        newMod = PropertyEditorModification.createRemoveModification(pd,
            originalValues, originalValues);
      }
      else if (mod.getType() == PropertyEditorModification.Type.REMOVE)
      {
        SortedSet<T> removedValues = new TreeSet<T>(originalValues);
        removedValues.removeAll(newValues);
        newMod = PropertyEditorModification.createRemoveModification(pd,
            removedValues, originalValues);
      }
      else
      {
        newMod = PropertyEditorModification.createSetModification(pd,
            new TreeSet<T>(newValues), originalValues);
      }
      removeModification(mod);
    }
    else
    {
      originalValues = new TreeSet<T>(previousValues);
      SortedSet<T> removedValues = new TreeSet<T>(originalValues);
      removedValues.removeAll(newValues);
      newMod = PropertyEditorModification.createRemoveModification(pd,
          removedValues, originalValues);
    }
    addModification(newMod);
  }

  /**
   * Returns the modifications that have been applied during the last call of
   * the method PropertyValueEditor.edit.
   * @return the modifications that have been applied during the last call of
   * the method PropertyValueEditor.edit.
   */
  public Collection<PropertyEditorModification> getModifications()
  {
    return mods;
  }

  /**
   * Clears the list of modifications.
   */
  public void resetModifications()
  {
    mods.clear();
  }

  /**
   * Adds a modification to the list of modifications that have been performed.
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param mod the modification to be added.
   */
  private <T> void addModification(PropertyEditorModification<T> mod)
  {
    mods.add(mod);
  }

  /**
   * Removes a modification from the list of modifications that have been
   * performed.
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param mod the modification to be removed.
   */
  private <T> boolean removeModification(PropertyEditorModification<T> mod)
  {
    return mods.remove(mod);
  }

  /**
   * Returns the modification associated with a given property definition:
   * we assume that we have only one modification per property definition (in
   * the worst case we merge the modifications and generate a unique set
   * modification).
   * @param <T> The type of the underlying property associated with the
   * modification.
   * @param pd the property definition.
   * @return the modification associated with the provided property definition
   * and <CODE>null</CODE> if no modification could be found.
   */
  private <T> PropertyEditorModification<?> getModification(
      PropertyDefinition<T> pd)
  {
    PropertyEditorModification<?> mod = null;

    for (PropertyEditorModification<?> m : mods)
    {
      if (pd.equals(m.getPropertyDefinition()))
      {
        mod = m;
        break;
      }
    }

    return mod;
  }

  /**
   * This method is required to avoid compilation warnings.  It basically adds
   * the contents of a collection to another collection by explicitly casting
   * its values.  This is done because the method getModification() returns
   * au undefined type.
   * @param <T>  The type of the destination values.
   * @param destination the collection that we want to update.
   * @param source the collection whose values we want to add (and cast) to the
   * source collection.
   * @param pd the PropertyDefinition we use to do the casting.
   * @throws ClassCastException if an error occurs during the cast of the
   * objects.
   */
  private <T> void castAndAddValues(Collection<T> destination,
      Collection<?> source, PropertyDefinition<T> pd) throws ClassCastException
  {
    for (Object o : source)
    {
      destination.add(pd.castValue(o));
    }
  }
}

