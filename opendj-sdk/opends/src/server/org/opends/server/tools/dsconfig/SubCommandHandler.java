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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DurationUnit;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.ManagedObjectPathSerializer;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.SizeUnit;
import org.opends.server.admin.Tag;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.table.TabSeparatedTablePrinter;
import org.opends.server.util.table.TablePrinter;



/**
 * An interface for sub-command implementations.
 */
abstract class SubCommandHandler {

  /**
   * A path serializer which is used to retrieve a managed object
   * based on a path and a list of path arguments.
   */
  private static class ManagedObjectFinder implements
      ManagedObjectPathSerializer {

    // Any argument exception that was caught when attempting to find
    // the
    // managed object.
    private ArgumentException ae;

    // The index of the next path argument to be retrieved.
    private int argIndex;

    // The list of managed object path arguments.
    private List<String> args;

    private AuthorizationException authze;

    private CommunicationException ce;

    private ConcurrentModificationException cme;

    // Any operation exception that was caught when attempting to find
    // the
    // managed object.
    private DefinitionDecodingException dde;

    // Flag indicating whether or not an exception occurred during
    // retrieval.
    private boolean gotException;

    // The last managed object retrieved.
    private ManagedObject<?> managedObject;

    private ManagedObjectDecodingException mode;

    private ManagedObjectNotFoundException monfe;



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        InstantiableRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d, String name) {
      if (!gotException) {
        // We should ignore the "template" name here and use a path
        // argument.
        String childName = args.get(argIndex++);

        try {
          ManagedObject<?> child = managedObject.getChild(r, childName);

          // Check that child is a sub-type of the specified
          // definition.
          if (!child.getManagedObjectDefinition().isChildOf(d)) {
            ae = ArgumentExceptionFactory.wrongManagedObjectType(r, child
                .getManagedObjectDefinition());
            gotException = true;
          } else {
            managedObject = child;
          }
        } catch (DefinitionDecodingException e) {
          dde = e;
          gotException = true;
        } catch (ManagedObjectDecodingException e) {
          mode = e;
          gotException = true;
        } catch (AuthorizationException e) {
          authze = e;
          gotException = true;
        } catch (ManagedObjectNotFoundException e) {
          monfe = e;
          gotException = true;
        } catch (ConcurrentModificationException e) {
          cme = e;
          gotException = true;
        } catch (CommunicationException e) {
          ce = e;
          gotException = true;
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        OptionalRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      if (!gotException) {
        try {
          ManagedObject<?> child = managedObject.getChild(r);

          // Check that child is a sub-type of the specified
          // definition.
          if (!child.getManagedObjectDefinition().isChildOf(d)) {
            ae = ArgumentExceptionFactory.wrongManagedObjectType(r, child
                .getManagedObjectDefinition());
            gotException = true;
          } else {
            managedObject = child;
          }
        } catch (DefinitionDecodingException e) {
          dde = e;
          gotException = true;
        } catch (ManagedObjectDecodingException e) {
          mode = e;
          gotException = true;
        } catch (AuthorizationException e) {
          authze = e;
          gotException = true;
        } catch (ManagedObjectNotFoundException e) {
          monfe = e;
          gotException = true;
        } catch (ConcurrentModificationException e) {
          cme = e;
          gotException = true;
        } catch (CommunicationException e) {
          ce = e;
          gotException = true;
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        SingletonRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      if (!gotException) {
        try {
          ManagedObject<?> child = managedObject.getChild(r);

          // Check that child is a sub-type of the specified
          // definition.
          if (!child.getManagedObjectDefinition().isChildOf(d)) {
            ae = ArgumentExceptionFactory.wrongManagedObjectType(r, child
                .getManagedObjectDefinition());
            gotException = true;
          } else {
            managedObject = child;
          }
        } catch (DefinitionDecodingException e) {
          dde = e;
          gotException = true;
        } catch (ManagedObjectDecodingException e) {
          mode = e;
          gotException = true;
        } catch (AuthorizationException e) {
          authze = e;
          gotException = true;
        } catch (ManagedObjectNotFoundException e) {
          monfe = e;
          gotException = true;
        } catch (ConcurrentModificationException e) {
          cme = e;
          gotException = true;
        } catch (CommunicationException e) {
          ce = e;
          gotException = true;
        }
      }
    }



    /**
     * Finds the named managed object.
     *
     * @param context
     *          The management context.
     * @param path
     *          The managed object path.
     * @param args
     *          The managed object path arguments.
     * @return Returns the named managed object.
     * @throws ArgumentException
     *           If one of the naming arguments referenced a managed
     *           object of the wrong type.
     * @throws DefinitionDecodingException
     *           If the managed object was found but its type could
     *           not be determined.
     * @throws ManagedObjectDecodingException
     *           If the managed object was found but one or more of
     *           its properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *           If the requested managed object could not be found on
     *           the server.
     * @throws ConcurrentModificationException
     *           If this managed object has been removed from the
     *           server by another client.
     * @throws AuthorizationException
     *           If the server refuses to retrieve the managed object
     *           because the client does not have the correct
     *           privileges.
     * @throws CommunicationException
     *           If the client cannot contact the server due to an
     *           underlying communication problem.
     */
    public ManagedObject<?> find(ManagementContext context,
        ManagedObjectPath<?, ?> path, List<String> args)
        throws ArgumentException, CommunicationException,
        AuthorizationException, ConcurrentModificationException,
        DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException {
      this.managedObject = context.getRootConfigurationManagedObject();
      this.args = args;
      this.argIndex = 0;

      this.gotException = false;
      this.ae = null;
      this.authze = null;
      this.ce = null;
      this.cme = null;
      this.dde = null;
      this.mode = null;
      this.monfe = null;

      path.serialize(this);

      if (ae != null) {
        throw ae;
      } else if (authze != null) {
        throw authze;
      } else if (ce != null) {
        throw ce;
      } else if (cme != null) {
        throw cme;
      } else if (dde != null) {
        throw dde;
      } else if (mode != null) {
        throw mode;
      } else if (monfe != null) {
        throw monfe;
      } else {
        return managedObject;
      }
    }
  }



  /**
   * A path serializer which is used to register a sub-command's
   * naming arguments.
   */
  private static class NamingArgumentBuilder implements
      ManagedObjectPathSerializer {

    /**
     * Creates the naming arguments for a given path.
     *
     * @param subCommand
     *          The sub-command.
     * @param path
     *          The managed object path.
     * @param isCreate
     *          Indicates whether the sub-command is a create-xxx
     *          sub-command, in which case the final path element will
     *          have different usage information.
     * @return Returns the naming arguments.
     * @throws ArgumentException
     *           If one or more naming arguments could not be
     *           registered.
     */
    public static List<StringArgument> create(SubCommand subCommand,
        ManagedObjectPath<?, ?> path, boolean isCreate)
        throws ArgumentException {
      NamingArgumentBuilder builder = new NamingArgumentBuilder(subCommand,
          path.size(), isCreate);
      path.serialize(builder);

      if (builder.e != null) {
        throw builder.e;
      }

      return builder.arguments;
    }

    // The list of naming arguments.
    private final List<StringArgument> arguments =
      new LinkedList<StringArgument>();

    // Any argument exception thrown when creating the naming
    // arguments.
    private ArgumentException e = null;

    // The sub-command.
    private final SubCommand subCommand;

    // Indicates whether the sub-command is a create-xxx
    // sub-command, in which case the final path element will
    // have different usage information.
    private final boolean isCreate;

    // The number of path elements to expect.
    private int sz;



    // Private constructor.
    private NamingArgumentBuilder(SubCommand subCommand, int sz,
        boolean isCreate) {
      this.subCommand = subCommand;
      this.sz = sz;
      this.isCreate = isCreate;
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        InstantiableRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d, String name) {
      sz--;

      // Use the last word in the managed object name as the argument
      // prefix.
      StringBuilder builder = new StringBuilder();

      String s = d.getName();
      int i = s.lastIndexOf('-');
      if (i < 0 || i == (s.length() - 1)) {
        builder.append(s);
      } else {
        builder.append(s.substring(i + 1));
      }
      builder.append("-name");
      String argName = builder.toString();
      StringArgument arg;

      try {
        if (isCreate && sz == 0) {
          // The final path element in create-xxx sub-commands should
          // have a different usage.
          PropertyDefinition<?> pd = r.getNamingPropertyDefinition();

          if (pd != null) {
            // Use syntax and description from naming property.
            PropertyDefinitionUsageBuilder b =
              new PropertyDefinitionUsageBuilder(false);
            String usage = "{" + b.getUsage(pd) + "}";
            arg = new StringArgument(argName, null, argName, true, true, usage,
                MSGID_DSCFG_DESCRIPTION_NAME_CREATE_EXT, d
                    .getUserFriendlyName(), pd.getName(), pd.getSynopsis());
          } else {
            arg = new StringArgument(argName, null, argName, true, true,
                "{NAME}", MSGID_DSCFG_DESCRIPTION_NAME_CREATE, d
                    .getUserFriendlyName());
          }
        } else {
          // A normal naming argument.
          arg = new StringArgument(argName, null, argName, true, true,
              "{NAME}", MSGID_DSCFG_DESCRIPTION_NAME, d.getUserFriendlyName());
        }
        subCommand.addArgument(arg);
        arguments.add(arg);
      } catch (ArgumentException e) {
        this.e = e;
      }
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        OptionalRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      sz--;
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        SingletonRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      sz--;
    }

  }

  /**
   * The value for the long option advanced.
   */
  private static final String OPTION_DSCFG_LONG_ADVANCED = "advanced";

  /**
   * The value for the long option property.
   */
  private static final String OPTION_DSCFG_LONG_PROPERTY = "property";

  /**
   * The value for the long option record.
   */
  private static final String OPTION_DSCFG_LONG_RECORD = "record";

  /**
   * The value for the long option unit-size.
   */
  private static final String OPTION_DSCFG_LONG_UNIT_SIZE = "unit-size";

  /**
   * The value for the long option unit-time.
   */
  private static final String OPTION_DSCFG_LONG_UNIT_TIME = "unit-time";

  /**
   * The value for the short option advanced.
   */
  private static final Character OPTION_DSCFG_SHORT_ADVANCED = null;

  /**
   * The value for the short option property.
   */
  private static final Character OPTION_DSCFG_SHORT_PROPERTY = null;

  /**
   * The value for the short option record.
   */
  private static final char OPTION_DSCFG_SHORT_RECORD = 'E';

  /**
   * The value for the short option unit-size.
   */
  private static final char OPTION_DSCFG_SHORT_UNIT_SIZE = 'Z';

  /**
   * The value for the short option unit-time.
   */
  private static final char OPTION_DSCFG_SHORT_UNIT_TIME = 'M';

  // The argument which should be used to request advanced mode.
  private BooleanArgument advancedModeArgument;

  // The argument which should be used to specify zero or more
  // property names.
  private StringArgument propertyArgument;

  // The argument which should be used to request record mode.
  private BooleanArgument recordModeArgument;

  // The tags associated with this sub-command handler.
  private final Set<Tag> tags = new HashSet<Tag>();

  // The argument which should be used to request specific size units.
  private StringArgument unitSizeArgument;

  // The argument which should be used to request specific time units.
  private StringArgument unitTimeArgument;



  /**
   * Create a new sub-command handler.
   */
  protected SubCommandHandler() {
    // No implementation required.
  }



  /**
   * Gets the sub-command associated with this handler.
   *
   * @return Returns the sub-command associated with this handler.
   */
  public abstract SubCommand getSubCommand();



  /**
   * Gets the tags associated with this sub-command handler.
   *
   * @return Returns the tags associated with this sub-command
   *         handler.
   */
  public final Set<Tag> getTags() {
    return tags;
  }



  /**
   * Run this sub-command handler.
   *
   * @param app
   *          The application.
   * @param out
   *          The application output stream.
   * @param err
   *          The application error stream.
   * @return Returns zero if the sub-command completed successfully or
   *         non-zero if it did not.
   * @throws ArgumentException
   *           If an argument required by the sub-command could not be
   *           parsed successfully.
   * @throws ClientException
   *           If the management context could not be created.
   */
  public abstract int run(DSConfig app, PrintStream out, PrintStream err)
      throws ArgumentException, ClientException;



  /**
   * Get the string representation of this sub-command handler.
   * <p>
   * The string representation is simply the sub-command's name.
   *
   * @return Returns the string representation of this sub-command
   *         handler.
   */
  @Override
  public final String toString() {
    return getSubCommand().getName();
  }



  /**
   * Adds one or more tags to this sub-command handler.
   *
   * @param tags
   *          The tags to be added to this sub-command handler.
   */
  protected final void addTags(Collection<Tag> tags) {
    this.tags.addAll(tags);
  }



  /**
   * Adds one or more tags to this sub-command handler.
   *
   * @param tags
   *          The tags to be added to this sub-command handler.
   */
  protected final void addTags(Tag... tags) {
    addTags(Arrays.asList(tags));
  }



  /**
   * Creates the naming arguments for a given path and registers them.
   *
   * @param subCommand
   *          The sub-command.
   * @param p
   *          The managed object path.
   * @param isCreate
   *          Indicates whether the sub-command is a create-xxx
   *          sub-command, in which case the final path element will
   *          have different usage information.
   * @return Returns the naming arguments.
   * @throws ArgumentException
   *           If one or more naming arguments could not be
   *           registered.
   */
  protected final List<StringArgument> createNamingArgs(SubCommand subCommand,
      ManagedObjectPath<?, ?> p, boolean isCreate) throws ArgumentException {
    return NamingArgumentBuilder.create(subCommand, p, isCreate);
  }



  /**
   * Creates a script-friendly table printer. This factory method
   * should be used by sub-command handler implementations rather than
   * constructing a table printer directly so that we can easily
   * switch table implementations (perhaps dynamically depending on
   * argument).
   *
   * @param stream
   *          The output stream for the table.
   * @return Returns a script-friendly table printer.
   */
  protected final TablePrinter createScriptFriendlyTablePrinter(
      PrintStream stream) {
    return new TabSeparatedTablePrinter(stream);
  }



  /**
   * Get the managed object referenced by the provided managed object
   * path.
   *
   * @param context
   *          The management context.
   * @param path
   *          The managed object path.
   * @param args
   *          The list of managed object names required by the path.
   * @return Returns the managed object referenced by the provided
   *         managed object path.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the managed object was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   * @throws ArgumentException
   *           If one of the naming arguments referenced a managed
   *           object of the wrong type.
   */
  protected final ManagedObject<?> getManagedObject(ManagementContext context,
      ManagedObjectPath<?, ?> path, List<String> args)
      throws ArgumentException, AuthorizationException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      CommunicationException, ConcurrentModificationException,
      ManagedObjectNotFoundException {
    ManagedObjectFinder finder = new ManagedObjectFinder();
    return finder.find(context, path, args);
  }



  /**
   * Gets the values of the naming arguments.
   *
   * @param namingArgs
   *          The naming arguments.
   * @return Returns the values of the naming arguments.
   */
  protected final List<String> getNamingArgValues(
      List<StringArgument> namingArgs) {
    ArrayList<String> values = new ArrayList<String>(namingArgs.size());
    for (StringArgument arg : namingArgs) {
      values.add(arg.getValue());
    }
    return values;
  }



  /**
   * Gets the optional list of property names that the user requested.
   *
   * @return Returns the optional list of property names that the user
   *         requested.
   */
  protected final Set<String> getPropertyNames() {
    if (propertyArgument != null) {
      return new LinkedHashSet<String>(propertyArgument.getValues());
    } else {
      return Collections.emptySet();
    }
  }



  /**
   * Gets the optional size unit that the user requested.
   *
   * @return Returns the size unit that the user requested, or
   *         <code>null</code> if no size unit was specified.
   * @throws ArgumentException
   *           If the user specified an invalid size unit.
   */
  protected final SizeUnit getSizeUnit() throws ArgumentException {
    if (unitSizeArgument != null) {
      String value = unitSizeArgument.getValue();

      if (value != null) {
        try {
          return SizeUnit.getUnit(value);
        } catch (IllegalArgumentException e) {
          int msgID = MSGID_DSCFG_ERROR_SIZE_UNIT_UNRECOGNIZED;
          String msg = getMessage(msgID, value);
          throw new ArgumentException(msgID, msg);
        }
      }
    }

    return null;
  }



  /**
   * Gets the optional time unit that the user requested.
   *
   * @return Returns the time unit that the user requested, or
   *         <code>null</code> if no time unit was specified.
   * @throws ArgumentException
   *           If the user specified an invalid time unit.
   */
  protected final DurationUnit getTimeUnit() throws ArgumentException {
    if (unitTimeArgument != null) {
      String value = unitTimeArgument.getValue();

      if (value != null) {
        try {
          return DurationUnit.getUnit(value);
        } catch (IllegalArgumentException e) {
          int msgID = MSGID_DSCFG_ERROR_TIME_UNIT_UNRECOGNIZED;
          String msg = getMessage(msgID, value);
          throw new ArgumentException(msgID, msg);
        }
      }
    }

    return null;
  }



  /**
   * Determines whether the user requested advanced mode.
   *
   * @return Returns <code>true</code> if the user requested
   *         advanced mode.
   */
  protected final boolean isAdvancedMode() {
    if (advancedModeArgument != null) {
      return advancedModeArgument.isPresent();
    } else {
      return false;
    }
  }



  /**
   * Determines whether the user requested record-mode.
   *
   * @return Returns <code>true</code> if the user requested
   *         record-mode.
   */
  protected final boolean isRecordMode() {
    if (recordModeArgument != null) {
      return recordModeArgument.isPresent();
    } else {
      return false;
    }
  }



  /**
   * Registers the advanced mode argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @param descriptionID
   *          The usage description message ID to be used for the
   *          argument.
   * @param args
   *          The arguments for the usage description.
   * @throws ArgumentException
   *           If the advanced mode argument could not be registered.
   */
  protected final void registerAdvancedModeArgument(SubCommand subCommand,
      int descriptionID, String... args) throws ArgumentException {
    this.advancedModeArgument = new BooleanArgument(OPTION_DSCFG_LONG_ADVANCED,
        OPTION_DSCFG_SHORT_ADVANCED, OPTION_DSCFG_LONG_ADVANCED, descriptionID,
        (Object[]) args);
    subCommand.addArgument(advancedModeArgument);
  }



  /**
   * Registers the property name argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the property name argument could not be registered.
   */
  protected final void registerPropertyNameArgument(SubCommand subCommand)
      throws ArgumentException {
    this.propertyArgument = new StringArgument(OPTION_DSCFG_LONG_PROPERTY,
        OPTION_DSCFG_SHORT_PROPERTY, OPTION_DSCFG_LONG_PROPERTY, false, true,
        true, "{PROP}", null, null, MSGID_DSCFG_DESCRIPTION_PROP);
    subCommand.addArgument(propertyArgument);
  }



  /**
   * Registers the record mode argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the record mode argument could not be registered.
   */
  protected final void registerRecordModeArgument(SubCommand subCommand)
      throws ArgumentException {
    this.recordModeArgument = new BooleanArgument(OPTION_DSCFG_LONG_RECORD,
        OPTION_DSCFG_SHORT_RECORD, OPTION_DSCFG_LONG_RECORD,
        MSGID_DSCFG_DESCRIPTION_RECORD);
    subCommand.addArgument(recordModeArgument);
  }



  /**
   * Registers the unit-size argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the unit-size argument could not be registered.
   */
  protected final void registerUnitSizeArgument(SubCommand subCommand)
      throws ArgumentException {
    this.unitSizeArgument = new StringArgument(OPTION_DSCFG_LONG_UNIT_SIZE,
        OPTION_DSCFG_SHORT_UNIT_SIZE, OPTION_DSCFG_LONG_UNIT_SIZE, false, true,
        "{UNIT}", MSGID_DSCFG_DESCRIPTION_UNIT_SIZE);

    subCommand.addArgument(unitSizeArgument);
  }



  /**
   * Registers the unit-time argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the unit-time argument could not be registered.
   */
  protected final void registerUnitTimeArgument(SubCommand subCommand)
      throws ArgumentException {
    this.unitTimeArgument = new StringArgument(OPTION_DSCFG_LONG_UNIT_TIME,
        OPTION_DSCFG_SHORT_UNIT_TIME, OPTION_DSCFG_LONG_UNIT_TIME, false, true,
        "{UNIT}", MSGID_DSCFG_DESCRIPTION_UNIT_TIME);

    subCommand.addArgument(unitTimeArgument);
  }
}
