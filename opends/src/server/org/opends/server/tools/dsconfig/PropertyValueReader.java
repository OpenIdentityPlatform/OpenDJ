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
import org.opends.messages.MessageBuilder;


import static org.opends.messages.ToolMessages.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.EnumPropertyDefinition;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionVisitor;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.UnknownPropertyDefinitionException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;



/**
 * A class responsible for interactively retrieving property values
 * from the console.
 */
final class PropertyValueReader {

  /**
   * A help call-back which displays help on a property.
   */
  private static final class PropertyHelpCallback implements HelpCallback {

    // The managed object.
    private final ManagedObject<?> mo;

    // The property definition.
    private final PropertyDefinition<?> pd;



    /**
     * Creates a new property help call-back.
     *
     * @param mo
     *          The managed object.
     * @param pd
     *          The property definition.
     */
    public PropertyHelpCallback(ManagedObject<?> mo, PropertyDefinition<?> pd) {
      this.mo = mo;
      this.pd = pd;
    }



    /**
     * {@inheritDoc}
     */
    public void display(ConsoleApplication app) {
      app.println();
      HelpSubCommandHandler.displayVerboseSingleProperty(mo
          .getManagedObjectDefinition(), pd.getName(), app.getErrorStream());
    }

  }



  /**
   * A menu call-back used for editing property values.
   */
  private interface MenuCallback {

    /**
     * Invoke the menu call-back.
     *
     * @param mo
     *          The managed object.
     * @param pd
     *          The property definition to be modified.
     * @param <T>
     *          The type of property to be edited.
     * @throws ArgumentException
     *           If the user input could not be retrieved for some
     *           reason.
     */
    <T> void invoke(ManagedObject<?> mo, PropertyDefinition<T> pd)
        throws ArgumentException;
  }



  /**
   * A menu call-back for adding values to a property.
   */
  private final class AddValueMenuCallback implements MenuCallback {

    /**
     * {@inheritDoc}
     */
    public <T> void invoke(ManagedObject<?> mo, PropertyDefinition<T> pd)
        throws ArgumentException {
      // TODO: display error if the value already exists.

      // TODO: for enumerations, only display the values which are not
      // already assigned.
      T value = read(mo, pd);
      SortedSet<T> values = mo.getPropertyValues(pd);
      values.add(value);
      mo.setPropertyValues(pd, values);
    }

  }



  /**
   * A menu call-back for removing values from a property.
   */
  private final class RemoveValueMenuCallback implements MenuCallback {

    /**
     * {@inheritDoc}
     */
    public <T> void invoke(ManagedObject<?> mo, PropertyDefinition<T> pd)
        throws ArgumentException {
      PropertyValuePrinter printer =
        new PropertyValuePrinter(null, null, false);
      SortedSet<T> values = mo.getPropertyValues(pd);

      List<Message> descriptions = new ArrayList<Message>(values.size());
      List<T> lvalues = new ArrayList<T>(values.size());

      for (T value : values) {
        descriptions.add(Message.raw(printer.print(pd, value)));
        lvalues.add(value);
      }

      Message promptMsg =
          INFO_DSCFG_VALUE_READER_PROMPT_REMOVE.get(pd.getName());
      T value = app.readChoice(promptMsg, descriptions, lvalues, null);
      values.remove(value);
      mo.setPropertyValues(pd, values);
    }

  }



  /**
   * A menu call-back for resetting a property back to its defaults.
   */
  private final class ResetValueMenuCallback implements MenuCallback {

    /**
     * {@inheritDoc}
     */
    public <T> void invoke(ManagedObject<?> mo, PropertyDefinition<T> pd)
        throws ArgumentException {
      mo.setPropertyValue(pd, null);
    }

  }



  /**
   * A menu call-back for setting a single-valued property.
   */
  private final class SetValueMenuCallback implements MenuCallback {

    /**
     * {@inheritDoc}
     */
    public <T> void invoke(ManagedObject<?> mo, PropertyDefinition<T> pd)
        throws ArgumentException {
      T value = read(mo, pd);
      mo.setPropertyValue(pd, value);
    }

  }



  /**
   * The reader implementation.
   */
  private final class Visitor extends PropertyDefinitionVisitor<String, Void> {

    // Any argument exception that was caught during processing.
    private ArgumentException ae = null;



    /**
     * Read a value from the console for the provided property
     * definition.
     *
     * @param pd
     *          The property definition.
     * @return Returns the string value.
     * @throws ArgumentException
     *           If the user input could not be retrieved for some
     *           reason.
     */
    public String read(PropertyDefinition<?> pd) throws ArgumentException {
      String result = pd.accept(this, null);

      if (result != null) {
        return result;
      } else if (ae != null) {
        throw ae;
      } else {
        throw new IllegalStateException(
            "No result and no ArgumentException caught");
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitBoolean(BooleanPropertyDefinition d, Void p) {
      List<String> values = Arrays.asList(new String[] {
          "false", "true"
      });
      List<Message> descriptions = Arrays.asList(new Message[] {
        Message.raw("false"), Message.raw("true")
      });
      try {
        Message promptMsg = INFO_DSCFG_VALUE_READER_PROMPT_SELECT_VALUE.get(
                d.getName());
        return app.readChoice(promptMsg, descriptions, values, null);
      } catch (ArgumentException e) {
        ae = e;
        return null;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> String visitEnum(EnumPropertyDefinition<E> d,
        Void p) {
      SortedMap<String, String> map = new TreeMap<String, String>();
      for (E value : EnumSet.allOf(d.getEnumClass())) {
        String s = String.format("%s : %s", value.toString(), d
            .getValueSynopsis(value));
        map.put(value.toString(), s);
      }

      List<String> descStrings = new ArrayList<String>(map.values());
      List<Message> descriptions = new ArrayList<Message>(descStrings.size());
      for (String s : descStrings) {
        descriptions.add(Message.raw(s));
      }

      List<String> values = new ArrayList<String>(map.keySet());
      try {
        Message promptMsg =
                INFO_DSCFG_VALUE_READER_PROMPT_SELECT_VALUE.get(d.getName());
        return app.readChoice(promptMsg, descriptions, values, null);
      } catch (ArgumentException e) {
        ae = e;
        return null;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String visitUnknown(PropertyDefinition<?> d, Void p)
        throws UnknownPropertyDefinitionException {
      try {
        Message promptMsg = INFO_DSCFG_VALUE_READER_PROMPT_ENTER_VALUE.get(
                d.getName());
        return app.readLineOfInput(promptMsg);
      } catch (ArgumentException e) {
        ae = e;
        return null;
      }
    }

  }

  // The application console.
  private final ConsoleApplication app;



  /**
   * Create a new property value reader which will read from the
   * provider application console.
   *
   * @param app
   *          The application console.
   */
  public PropertyValueReader(ConsoleApplication app) {
    this.app = app;
  }



  /**
   * Asks the user to input a single value for the provided property
   * definition. The value will be validated according to the
   * constraints of the property definition and its decoded value
   * returned.
   *
   * @param <T>
   *          The underlying type of the property definition.
   * @param mo
   *          The managed object.
   * @param pd
   *          The property definition.
   * @return Returns the validated string value.
   * @throws ArgumentException
   *           If the user input could not be retrieved for some
   *           reason.
   */
  public <T> T read(ManagedObject<?> mo, PropertyDefinition<T> pd)
      throws ArgumentException {
    while (true) {
      app.println();
      String value = pd.accept(new Visitor(), null);

      try {
        return pd.decodeValue(value);
      } catch (IllegalPropertyValueStringException e) {
        app.println();
        app.printMessage(Message.raw(
                ArgumentExceptionFactory.adaptPropertyException(e,
            mo.getManagedObjectDefinition()).getMessage()));
      }
    }
  }



  /**
   * Edit the properties of a managed object. Only the properties
   * listed in the provided collection will be accessible to the
   * client. It is up to the caller to ensure that the list of
   * properties does not include read-only, monitoring, hidden, or
   * advanced properties as appropriate.
   *
   * @param mo
   *          The managed object.
   * @param c
   *          The collection of properties which can be edited.
   * @throws ArgumentException
   *           If the user input could not be retrieved for some
   *           reason.
   */
  public void readAll(ManagedObject<?> mo, Collection<PropertyDefinition<?>> c)
      throws ArgumentException {
    // Get values for this missing mandatory property.
    for (PropertyDefinition<?> pd : c) {
      if (pd.hasOption(PropertyOption.MANDATORY)) {
        if (mo.getPropertyValues(pd).isEmpty()) {
          editProperty(mo, pd);
        }
      }
    }

    // Now let users modify remaining properties.
    boolean isFinished = false;
    PropertyValuePrinter valuePrinter = new PropertyValuePrinter(null, null,
        false);
    while (!isFinished) {
      // Display a menu allowing users to edit individual options.
      TableBuilder builder = new TableBuilder();
      builder.appendHeading();
      builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_NAME.get());
      builder.appendHeading(INFO_DSCFG_HEADING_PROPERTY_VALUE.get());

      int i = 0;
      List<PropertyDefinition<?>> pl = new ArrayList<PropertyDefinition<?>>(c);
      for (PropertyDefinition<?> pd : pl) {
        builder.startRow();
        builder.appendCell("[" + i + "]");
        builder.appendCell(pd.getName());

        Message values = getPropertyValuesAsString(mo, pd, valuePrinter);
        builder.appendCell(values);
        i++;
      }

      builder.startRow();
      builder.startRow();
      builder.appendCell("[" + i + "]");
      builder.appendCell(INFO_DSCFG_VALUE_READER_MENU_CONTINUE.get());

      // Display the menu.
      app.println();
      app.printMessage(INFO_DSCFG_VALUE_READER_MENU_TITLE.get(i));
      app.println();

      TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
      printer.setColumnWidth(2, 0);
      builder.print(printer);

      // Get the user input.
      final int size = i;
      Message promptMsg = INFO_DSCFG_GENERAL_CHOICE_PROMPT_NOHELP.get(i);
      ValidationCallback<Integer> validator =
        new ValidationCallback<Integer>() {

        public Integer validate(ConsoleApplication app, String input) {
          String ninput = input.trim();

          try {
            int j = Integer.parseInt(ninput);
            if (j < 1 || j > size) {
              throw new NumberFormatException();
            }
            return j;
          } catch (NumberFormatException e) {
            app.println();
            Message errMsg = ERR_DSCFG_ERROR_GENERAL_CHOICE.get(size);
            app.printMessage(errMsg);
            return null;
          }
        }
      };

      // Get the choice.
      int choice;
      try {
        choice = app.readValidatedInput(promptMsg, validator);
      } catch (ClientException e) {
        // Should never happen.
        throw new RuntimeException(e);
      }

      if (choice == size) {
        isFinished = true;
      } else {
        editProperty(mo, pl.get(choice));
      }
    }
  }



  // Interactively edit a property.
  private <T> void editProperty(ManagedObject<?> mo, PropertyDefinition<T> pd)
      throws ArgumentException {
    // If the property is mandatory then make sure we prompt for an
    // initial value.
    if (pd.hasOption(PropertyOption.MANDATORY)) {
      if (mo.getPropertyValues(pd).isEmpty()) {
        app.println();
        Message promptMsg = INFO_DSCFG_VALUE_READER_PROMPT_MANDATORY.get(
                pd.getName());
        app.printMessage(promptMsg);
        T value = read(mo, pd);
        mo.setPropertyValue(pd, value);
      }
    }

    boolean isFinished = false;
    while (!isFinished) {
      // Construct a list of menu options and their call-backs.
      List<Message> descriptions = new ArrayList<Message>();
      List<MenuCallback> callbacks = new ArrayList<MenuCallback>();

      if (pd.hasOption(PropertyOption.MULTI_VALUED)) {
        descriptions.add(INFO_DSCFG_VALUE_READER_MENU_ADD.get());
        callbacks.add(new AddValueMenuCallback());

        if (!mo.getPropertyValues(pd).isEmpty()) {
          descriptions.add(INFO_DSCFG_VALUE_READER_MENU_REMOVE.get());
          callbacks.add(new RemoveValueMenuCallback());
        }
      } else {
        descriptions.add(INFO_DSCFG_VALUE_READER_MENU_SET.get());
        callbacks.add(new SetValueMenuCallback());
      }

      if (!pd.hasOption(PropertyOption.MANDATORY)
          || !(pd.getDefaultBehaviorProvider()
              instanceof UndefinedDefaultBehaviorProvider)) {
        descriptions.add(INFO_DSCFG_VALUE_READER_MENU_RESET.get());
        callbacks.add(new ResetValueMenuCallback());
      }

      descriptions.add(INFO_DSCFG_VALUE_READER_MENU_CONTINUE.get());
      callbacks.add(null);

      // FIXME: display current values of the property.
      Message promptMsg = INFO_DSCFG_VALUE_READER_PROMPT_MODIFY_MENU.get(
              pd.getName());
      MenuCallback callback = app.readChoice(promptMsg, descriptions,
          callbacks, new PropertyHelpCallback(mo, pd));

      if (callback != null) {
        callback.invoke(mo, pd);
      } else {
        isFinished = true;
      }
    }
  }



  // Display the set of values associated with a property.
  private <T> Message getPropertyValuesAsString(ManagedObject<?> mo,
      PropertyDefinition<T> pd, PropertyValuePrinter valuePrinter) {
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



        public Message visitDefined(
            DefinedDefaultBehaviorProvider<T> d, Void p) {
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

      Message content = pd.getDefaultBehaviorProvider().accept(visitor, null);
      if (content == null) {
        return Message.raw("-");
      } else {
        return content;
      }
    } else {
      MessageBuilder sb = new MessageBuilder();
      boolean isFirst = true;
      for (T value : values) {
        if (!isFirst) {
          sb.append(", ");
        }
        sb.append(valuePrinter.print(pd, value));
        isFirst = false;
      }

      return sb.toMessage();
    }
  }
}
