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
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AdministratorAction;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.EnumPropertyDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.PropertyDefinitionVisitor;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.StringPropertyDefinition;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.UnknownPropertyDefinitionException;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TablePrinter;
import org.opends.server.util.table.TextTablePrinter;



/**
 * A sub-command handler which is used to display help about managed
 * objects and their properties.
 * <p>
 * This sub-command implements the help-properties sub-command.
 */
final class HelpSubCommandHandler extends SubCommandHandler {

  /**
   * This class is used to print the default behavior of a property.
   */
  private static class DefaultBehaviorPrinter {

    /**
     * The default behavior printer visitor implementation.
     *
     * @param <T>
     *          The property type.
     */
    private static class DefaultVisitor<T> implements
        DefaultBehaviorProviderVisitor<T, String, PropertyDefinition<T>> {

      /**
       * {@inheritDoc}
       */
      public String visitAbsoluteInherited(
          AbsoluteInheritedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        return String.format(FIELD_INHERITS_ABSOLUTE, d.getPropertyName(), d
            .getManagedObjectPath().getRelationDefinition()
            .getUserFriendlyName());
      }



      /**
       * {@inheritDoc}
       */
      public String visitAlias(AliasDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        return d.getSynopsis();
      }



      /**
       * {@inheritDoc}
       */
      public String visitDefined(DefinedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        StringBuilder builder = new StringBuilder();
        PropertyValuePrinter printer = new PropertyValuePrinter(null, null,
            false);
        boolean isFirst = true;
        for (String s : d.getDefaultValues()) {
          if (!isFirst) {
            builder.append(", "); //$NON-NLS-1$
          }

          T value = p.decodeValue(s);
          builder.append(printer.print(p, value));
        }

        return builder.toString();
      }



      /**
       * {@inheritDoc}
       */
      public String visitRelativeInherited(
          RelativeInheritedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        if (d.getRelativeOffset() == 0) {
          return String.format(FIELD_INHERITS_RELATIVE_THIS, d
              .getPropertyName(), d.getManagedObjectDefinition()
              .getUserFriendlyName());
        } else {
          return String.format(FIELD_INHERITS_RELATIVE_PARENT, d
              .getPropertyName(), d.getManagedObjectDefinition()
              .getUserFriendlyName());
        }
      }



      /**
       * {@inheritDoc}
       */
      public String visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
          PropertyDefinition<T> p) {
        return FIELD_UNDEFINED;
      }

    }



    /**
     * Create a new default behavior printer.
     */
    public DefaultBehaviorPrinter() {
      // No implementation required.
    }



    /**
     * Get a user-friendly description of a property's default
     * behavior.
     *
     * @param <T>
     *          The type of the property definition.
     * @param pd
     *          The property definition.
     * @return Returns the user-friendly description of a property's
     *         default behavior.
     */
    public <T> String print(PropertyDefinition<T> pd) {
      DefaultVisitor<T> v = new DefaultVisitor<T>();
      return pd.getDefaultBehaviorProvider().accept(v, pd);
    }
  }



  /**
   * This class is used to print detailed syntax information about a
   * property.
   */
  private static class SyntaxPrinter {

    /**
     * The syntax printer visitor implementation.
     */
    private static class Visitor extends
        PropertyDefinitionVisitor<Void, PrintStream> {

      // Private constructor.
      private Visitor() {
        // No implementation required.
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public <E extends Enum<E>> Void visitEnum(EnumPropertyDefinition<E> d,
          PrintStream p) {
        displayUsage(p, FIELD_ENUM);
        p.println();

        TableBuilder builder = new TableBuilder();
        boolean isFirst = true;
        for (E value : EnumSet.<E> allOf(d.getEnumClass())) {
          if (!isFirst) {
            builder.startRow();
          }

          builder.startRow();
          builder.appendCell();
          builder.appendCell();
          builder.appendCell(value.toString());
          builder.appendCell(HEADING_SEPARATOR);
          builder.appendCell(d.getValueSynopsis(value));

          isFirst = false;
        }

        TextTablePrinter factory = new TextTablePrinter(p);
        factory.setDisplayHeadings(false);
        factory.setColumnWidth(0, HEADING_WIDTH);
        factory.setColumnWidth(1, HEADING_SEPARATOR.length());
        factory.setColumnWidth(4, 0);
        factory.setPadding(0);

        builder.print(factory);

        return null;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public Void visitString(StringPropertyDefinition d, PrintStream p) {
        PropertyDefinitionUsageBuilder usageBuilder =
          new PropertyDefinitionUsageBuilder(true);

        TableBuilder builder = new TableBuilder();
        builder.startRow();
        builder.appendCell(HEADING_SYNTAX);
        builder.appendCell(HEADING_SEPARATOR);
        builder.appendCell(usageBuilder.getUsage(d));

        if (d.getPattern() != null) {
          builder.startRow();
          builder.startRow();
          builder.appendCell();
          builder.appendCell();
          builder.appendCell(d.getPatternSynopsis());
        }

        TextTablePrinter factory = new TextTablePrinter(p);
        factory.setDisplayHeadings(false);
        factory.setColumnWidth(0, HEADING_WIDTH);
        factory.setColumnWidth(2, 0);
        factory.setPadding(0);

        builder.print(factory);

        return null;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public Void visitUnknown(PropertyDefinition<?> d, PrintStream p)
          throws UnknownPropertyDefinitionException {
        PropertyDefinitionUsageBuilder usageBuilder =
          new PropertyDefinitionUsageBuilder(true);
        displayUsage(p, usageBuilder.getUsage(d));

        return null;
      }



      // Common usage.
      private void displayUsage(PrintStream p, String usage) {
        TableBuilder builder = new TableBuilder();
        builder.startRow();
        builder.appendCell(HEADING_SYNTAX);
        builder.appendCell(HEADING_SEPARATOR);
        builder.appendCell(usage);

        TextTablePrinter factory = new TextTablePrinter(p);
        factory.setDisplayHeadings(false);
        factory.setColumnWidth(0, HEADING_WIDTH);
        factory.setColumnWidth(2, 0);
        factory.setPadding(0);

        builder.print(factory);
      }
    }

    // The private implementation.
    private final Visitor pimpl;



    /**
     * Creates a new syntax printer which can be used to print
     * detailed syntax information about a property.
     */
    public SyntaxPrinter() {
      this.pimpl = new Visitor();
    }



    /**
     * Print detailed syntax information about a property definition.
     *
     * @param out
     *          The output stream.
     * @param pd
     *          The property definition.
     */
    public void print(PrintStream out, PropertyDefinition<?> pd) {
      pd.accept(pimpl, out);
    }
  }

  // Strings used in property help.
  private static final String KEY_PREFIX = "help-properties.";

  private static final String FIELD_COMPONENT_RESTART = Messages
      .getString(KEY_PREFIX + "field.component.restart"); //$NON-NLS-1$

  private static final String FIELD_ENUM = Messages.getString(KEY_PREFIX
      + "field.enum"); //$NON-NLS-1$

  private static final String FIELD_INHERITS_ABSOLUTE = Messages
      .getString(KEY_PREFIX + "field.inherits.abs"); //$NON-NLS-1$

  private static final String FIELD_INHERITS_RELATIVE_PARENT = Messages
      .getString(KEY_PREFIX + "field.inherits.parent"); //$NON-NLS-1$

  private static final String FIELD_INHERITS_RELATIVE_THIS = Messages
      .getString(KEY_PREFIX + "field.inherits.this"); //$NON-NLS-1$

  private static final String FIELD_MONITORING = Messages.getString(KEY_PREFIX
      + "field.monitoring"); //$NON-NLS-1$

  private static final String FIELD_NO =
    getMessage(MSGID_DSCFG_GENERAL_CONFIRM_NO);

  private static final String FIELD_READ_ONLY = Messages.getString(KEY_PREFIX
      + "field.read-only"); //$NON-NLS-1$

  private static final String FIELD_SERVER_RESTART = Messages
      .getString(KEY_PREFIX + "field.server.restart"); //$NON-NLS-1$

  private static final String FIELD_UNDEFINED = Messages.getString(KEY_PREFIX
      + "field.undefined"); //$NON-NLS-1$

  private static final String FIELD_YES =
    getMessage(MSGID_DSCFG_GENERAL_CONFIRM_YES);

  private final static String HEADING_ADVANCED = Messages
      .getString(KEY_PREFIX + "heading.advanced"); //$NON-NLS-1$

  private final static String HEADING_DEFAULT = Messages.getString(KEY_PREFIX
      + "heading.default"); //$NON-NLS-1$

  private static final String HEADING_MANAGED_OBJECT = Messages
      .getString(KEY_PREFIX + "heading.managed-object"); //$NON-NLS-1$

  private final static String HEADING_MANDATORY = Messages.getString(KEY_PREFIX
      + "heading.mandatory"); //$NON-NLS-1$

  private final static String HEADING_MULTI_VALUED = Messages
      .getString(KEY_PREFIX + "heading.multi-valued"); //$NON-NLS-1$

  private static final String HEADING_PROPERTY = Messages.getString(KEY_PREFIX
      + "heading.property"); //$NON-NLS-1$

  private final static String HEADING_READ_ONLY = Messages.getString(KEY_PREFIX
      + "heading.read-only"); //$NON-NLS-1$

  private final static String HEADING_SEPARATOR = " : "; //$NON-NLS-1$

  private final static String HEADING_SYNTAX = Messages.getString(KEY_PREFIX
      + "heading.syntax"); //$NON-NLS-1$

  private final static String DESCRIPTION_OPTIONS_TITLE = Messages
      .getString(KEY_PREFIX + "description.options"); //$NON-NLS-1$

  private final static String DESCRIPTION_OPTIONS_READ = Messages
      .getString(KEY_PREFIX + "description.read"); //$NON-NLS-1$

  private final static String DESCRIPTION_OPTIONS_WRITE = Messages
      .getString(KEY_PREFIX + "description.write"); //$NON-NLS-1$

  private final static String DESCRIPTION_OPTIONS_MANDATORY = Messages
      .getString(KEY_PREFIX + "description.mandatory"); //$NON-NLS-1$

  private final static String DESCRIPTION_OPTIONS_SINGLE = Messages
      .getString(KEY_PREFIX + "description.single-valued"); //$NON-NLS-1$

  private final static String DESCRIPTION_OPTIONS_ADMIN = Messages
      .getString(KEY_PREFIX + "description.admin-action"); //$NON-NLS-1$

  // Width of biggest heading (need to be careful of I18N).
  private final static int HEADING_WIDTH;

  /**
   * The value for the long option type.
   */
  private static final String OPTION_DSCFG_LONG_TYPE = "type";



  /**
   * The value for the short option type.
   */
  private static final Character OPTION_DSCFG_SHORT_TYPE = 't';

  static {
    int tmp = HEADING_SYNTAX.length();
    tmp = Math.max(tmp, HEADING_DEFAULT.length());
    tmp = Math.max(tmp, HEADING_MULTI_VALUED.length());
    tmp = Math.max(tmp, HEADING_MANDATORY.length());
    tmp = Math.max(tmp, HEADING_READ_ONLY.length());
    HEADING_WIDTH = tmp;
  }

  /**
   * Creates a new help-properties sub-command.
   *
   * @param app
   *          The console application.
   * @param parser
   *          The sub-command argument parser.
   * @return Returns the new help-properties sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static synchronized HelpSubCommandHandler create(
      ConsoleApplication app, SubCommandArgumentParser parser)
      throws ArgumentException {
    if (INSTANCE == null) {
      INSTANCE = new HelpSubCommandHandler(app, parser);
    }
    return INSTANCE;
  }



  /**
   * Gets the application-wide help sub-command handler.
   *
   * @return Returns the application-wide help sub-command handler.
   */
  public static synchronized HelpSubCommandHandler getInstance() {
    if (INSTANCE == null) {
      throw new RuntimeException("Help sub-command handler not initialized");
    } else {
      return INSTANCE;
    }
  }



  // The singleton instance.
  private static HelpSubCommandHandler INSTANCE = null;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;

  // The argument which should be used to specify the type of managed
  // object to be retrieved.
  private final StringArgument typeArgument;

  // A table listing all the available types of managed object.
  private final Map<String, AbstractManagedObjectDefinition<?, ?>> types;

  // Private constructor.
  private HelpSubCommandHandler(ConsoleApplication app,
      SubCommandArgumentParser parser) throws ArgumentException {
    super(app);

    // Create the sub-command.
    String name = "list-properties";
    int descriptionID = MSGID_DSCFG_DESCRIPTION_SUBCMD_HELPPROP;
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        descriptionID);

    this.typeArgument = new StringArgument(OPTION_DSCFG_LONG_TYPE,
        OPTION_DSCFG_SHORT_TYPE, OPTION_DSCFG_LONG_TYPE, false, false, true,
        "{TYPE}", null, null, MSGID_DSCFG_DESCRIPTION_HELP_TYPE);
    this.subCommand.addArgument(this.typeArgument);

    // Register common arguments.
    registerAdvancedModeArgument(this.subCommand,
        MSGID_DSCFG_DESCRIPTION_ADVANCED_HELP);
    registerPropertyNameArgument(this.subCommand);

    this.types = new TreeMap<String, AbstractManagedObjectDefinition<?, ?>>();
  }



  /**
   * Displays detailed help about a single property to the specified
   * output stream.
   *
   * @param d
   *          The managed object definition.
   * @param name
   *          The name of the property definition.
   * @param out
   *          The output stream.
   */
  public void displayVerboseSingleProperty(
      AbstractManagedObjectDefinition<?, ?> d, String name, PrintStream out) {
    PropertyDefinition<?> pd = d.getPropertyDefinition(name);

    // Display the title.
    out.println(String.format(HEADING_PROPERTY, name));

    // Display the property synopsis and description.
    out.println();
    out.println(wrapText(pd.getSynopsis(), MAX_LINE_WIDTH));
    if (pd.getDescription() != null) {
      out.println();
      out.println(wrapText(pd.getDescription(), MAX_LINE_WIDTH));
    }

    // Display the syntax.
    out.println();
    SyntaxPrinter syntaxPrinter = new SyntaxPrinter();
    syntaxPrinter.print(out, pd);

    // Display remaining information in a table.
    out.println();
    TableBuilder builder = new TableBuilder();

    // Display the default behavior.
    DefaultBehaviorPrinter defaultPrinter = new DefaultBehaviorPrinter();

    builder.startRow();
    builder.appendCell(HEADING_DEFAULT);
    builder.appendCell(HEADING_SEPARATOR);
    builder.appendCell(defaultPrinter.print(pd));

    // Display options.
    builder.startRow();
    builder.appendCell(HEADING_ADVANCED);
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.ADVANCED)) {
      builder.appendCell(FIELD_YES);
    } else {
      builder.appendCell(FIELD_NO);
    }

    builder.startRow();
    builder.appendCell(HEADING_MULTI_VALUED);
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.MULTI_VALUED)) {
      builder.appendCell(FIELD_YES);
    } else {
      builder.appendCell(FIELD_NO);
    }

    builder.startRow();
    builder.appendCell(HEADING_MANDATORY);
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.MANDATORY)) {
      builder.appendCell(FIELD_YES);
    } else {
      builder.appendCell(FIELD_NO);
    }

    builder.startRow();
    builder.appendCell(HEADING_READ_ONLY);
    builder.appendCell(HEADING_SEPARATOR);
    if (pd.hasOption(PropertyOption.MONITORING)) {
      builder.appendCell(FIELD_MONITORING);
    } else if (pd.hasOption(PropertyOption.READ_ONLY)) {
      builder.appendCell(String
          .format(FIELD_READ_ONLY, d.getUserFriendlyName()));
    } else {
      builder.appendCell(FIELD_NO);
    }

    TextTablePrinter factory = new TextTablePrinter(out);
    factory.setDisplayHeadings(false);
    factory.setColumnWidth(0, HEADING_WIDTH);
    factory.setColumnWidth(2, 0);
    factory.setPadding(0);
    builder.print(factory);

    // Administrator action.
    AdministratorAction action = pd.getAdministratorAction();
    String synopsis = action.getSynopsis();
    if (synopsis == null) {
      switch (action.getType()) {
      case COMPONENT_RESTART:
        synopsis = String.format(FIELD_COMPONENT_RESTART, d
            .getUserFriendlyName());
        break;
      case SERVER_RESTART:
        synopsis = FIELD_SERVER_RESTART;
        break;
      default:
        // Do nothing.
        break;
      }
    }

    if (synopsis != null) {
      out.println();
      out.println(wrapText(synopsis, MAX_LINE_WIDTH));
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SubCommand getSubCommand() {
    return subCommand;
  }



  /**
   * Registers a managed object definition with this help properties
   * sub-command.
   *
   * @param d
   *          The managed object definition.
   */
  public void registerManagedObjectDefinition(
      AbstractManagedObjectDefinition<?, ?> d) {
    types.put(d.getName(), d);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int run()
      throws ArgumentException, ClientException {
    String typeName = typeArgument.getValue();
    Set<String> propertyNames = getPropertyNames();

    AbstractManagedObjectDefinition<?, ?> d = null;
    if (typeName != null) {
      // Requested help regarding a single managed object type.
      d = types.get(typeName);
      if (d == null) {
        throw ArgumentExceptionFactory.unknownType(typeName);
      }
    }

    // Validate property names if the type was specified.
    if (d != null) {
      for (String propertyName : propertyNames) {
        try {
          d.getPropertyDefinition(propertyName);
        } catch (IllegalArgumentException e) {
          throw ArgumentExceptionFactory.unknownProperty(d, propertyName);
        }
      }
    }

    // Determine the set of managed objects to be displayed.
    Collection<AbstractManagedObjectDefinition<?, ?>> defns;
    if (d == null) {
      defns = types.values();
    } else {
      defns = Collections.<AbstractManagedObjectDefinition<?, ?>> singleton(d);
    }

    if (!getConsoleApplication().isVerbose()) {
      displayNonVerbose(defns, propertyNames);
    } else {
      displayVerbose(defns, propertyNames);
    }
    return 0;
  }



  // Output property summary table.
  private void displayNonVerbose(
      Collection<AbstractManagedObjectDefinition<?, ?>> defns,
      Set<String> propertyNames) {
    PrintStream out = getConsoleApplication().getOutputStream();
    if (!getConsoleApplication().isScriptFriendly()) {
      out.println(DESCRIPTION_OPTIONS_TITLE);
      out.println();
      out.print(" r -- ");
      out.println(DESCRIPTION_OPTIONS_READ);
      out.print(" w -- ");
      out.println(DESCRIPTION_OPTIONS_WRITE);
      out.print(" m -- ");
      out.println(DESCRIPTION_OPTIONS_MANDATORY);
      out.print(" s -- ");
      out.println(DESCRIPTION_OPTIONS_SINGLE);
      out.print(" a -- ");
      out.println(DESCRIPTION_OPTIONS_ADMIN);
      out.println();
      out.println();
    }

    // Headings.
    TableBuilder builder = new TableBuilder();

    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_MANAGED_OBJECT_NAME));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_NAME));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_OPTIONS));
    builder.appendHeading(getMessage(MSGID_DSCFG_HEADING_PROPERTY_SYNTAX));

    // Sort keys.
    builder.addSortKey(0);
    builder.addSortKey(1);

    // Generate the table content.
    for (AbstractManagedObjectDefinition<?, ?> mod : defns) {
      Collection<PropertyDefinition<?>> pds;
      if (getConsoleApplication().isScriptFriendly()) {
        pds = mod.getAllPropertyDefinitions();
      } else {
        pds = mod.getPropertyDefinitions();
      }

      for (PropertyDefinition<?> pd : pds) {
        if (pd.hasOption(PropertyOption.HIDDEN)) {
          continue;
        }

        if (!isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
          continue;
        }

        if (!propertyNames.isEmpty() && !propertyNames.contains(pd.getName())) {
          continue;
        }

        // Display the property.
        builder.startRow();

        // Display the managed object type if necessary.
        builder.appendCell(mod.getName());

        // Display the property name.
        builder.appendCell(pd.getName());

        // Display the options.
        builder.appendCell(getPropertyOptionSummary(pd));

        // Display the syntax.
        PropertyDefinitionUsageBuilder v =
          new PropertyDefinitionUsageBuilder(false);
        builder.appendCell(v.getUsage(pd));
      }
    }

    TablePrinter printer;
    if (getConsoleApplication().isScriptFriendly()) {
      printer = createScriptFriendlyTablePrinter(out);
    } else {
      printer = new TextTablePrinter(out);
    }
    builder.print(printer);
  }



  // Display detailed help on managed objects and their properties.
  private void displayVerbose(
      Collection<AbstractManagedObjectDefinition<?, ?>> defns,
      Set<String> propertyNames) {
    PrintStream out = getConsoleApplication().getOutputStream();

    // Construct line used to separate consecutive sections.
    char[] c1 = new char[MAX_LINE_WIDTH];
    Arrays.fill(c1, '=');
    char[] c2 = new char[MAX_LINE_WIDTH];
    Arrays.fill(c2, '-');

    // Display help for each managed object.
    boolean isFirstManagedObject = true;
    for (AbstractManagedObjectDefinition<?, ?> mod : defns) {
      // Display help for each property.
      Set<PropertyDefinition<?>> pds =
        new TreeSet<PropertyDefinition<?>>(mod.getAllPropertyDefinitions());
      boolean isFirstProperty = true;
      for (PropertyDefinition<?> pd : pds) {
        if (pd.hasOption(PropertyOption.HIDDEN)) {
          continue;
        }

        if (!isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
          continue;
        }

        if (!propertyNames.isEmpty() && !propertyNames.contains(pd.getName())) {
          continue;
        }

        if (isFirstProperty) {
          // User has requested properties relating to this managed
          // object definition, so display the summary of the managed
          // object.
          if (!isFirstManagedObject) {
            out.println();
            out.println(c1);
            out.println();
          } else {
            isFirstManagedObject = false;
          }

          // Display the title.
          out.println(wrapText(String.format(HEADING_MANAGED_OBJECT, mod
              .getUserFriendlyName()), MAX_LINE_WIDTH));

          out.println();
          out.println(wrapText(mod.getSynopsis(), MAX_LINE_WIDTH));
          if (mod.getDescription() != null) {
            out.println();
            out.println(wrapText(mod.getDescription(), MAX_LINE_WIDTH));
          }
        }

        out.println();
        out.println(c2);
        out.println();

        displayVerboseSingleProperty(mod, pd.getName(), out);
        isFirstProperty = false;
      }
    }
  }



  // Compute the options field.
  private String getPropertyOptionSummary(PropertyDefinition<?> pd) {
    StringBuilder b = new StringBuilder();

    if (pd.hasOption(PropertyOption.MONITORING)
        || pd.hasOption(PropertyOption.READ_ONLY)) {
      b.append("r-"); //$NON-NLS-1$
    } else {
      b.append("rw"); //$NON-NLS-1$
    }

    if (pd.hasOption(PropertyOption.MANDATORY)) {
      b.append('m');
    } else {
      b.append('-');
    }

    if (pd.hasOption(PropertyOption.MULTI_VALUED)) {
      b.append('-');
    } else {
      b.append('s');
    }

    AdministratorAction action = pd.getAdministratorAction();
    if (action.getType() != AdministratorAction.Type.NONE) {
      b.append('a');
    } else {
      b.append('-');
    }
    return b.toString();
  }
}
