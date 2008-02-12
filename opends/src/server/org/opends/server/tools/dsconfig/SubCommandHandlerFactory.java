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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsconfig;



import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelationDefinitionVisitor;
import org.opends.server.admin.RelationOption;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.SubCommandArgumentParser;



/**
 * Uses the administration framework introspection API to construct
 * the dsconfig sub-command handlers.
 */
final class SubCommandHandlerFactory {

  /**
   * A relation definition visitor used to recursively determine the
   * set of available sub-commands.
   */
  private final class Visitor implements
      RelationDefinitionVisitor<Void, ManagedObjectPath<?, ?>> {

    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        Void visitInstantiable(
        InstantiableRelationDefinition<C, S> rd, ManagedObjectPath<?, ?> p) {
      try {
        // Create the sub-commands.
        createHandlers.add(CreateSubCommandHandler.create(parser, p, rd));
        deleteHandlers.add(DeleteSubCommandHandler.create(parser, p, rd));
        listHandlers.add(ListSubCommandHandler.create(parser, p, rd));
        getPropHandlers.add(GetPropSubCommandHandler.create(parser, p, rd));
        setPropHandlers.add(SetPropSubCommandHandler.create(parser, p, rd));

        // Process the referenced managed object definition and its
        // sub-types.
        processRelation(p, rd);
      } catch (ArgumentException e) {
        exception = e;
      }

      return null;
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        Void visitOptional(
        OptionalRelationDefinition<C, S> rd, ManagedObjectPath<?, ?> p) {
      try {
        // Create the sub-commands.
        createHandlers.add(CreateSubCommandHandler.create(parser, p, rd));
        deleteHandlers.add(DeleteSubCommandHandler.create(parser, p, rd));
        listHandlers.add(ListSubCommandHandler.create(parser, p, rd));
        getPropHandlers.add(GetPropSubCommandHandler.create(parser, p, rd));
        setPropHandlers.add(SetPropSubCommandHandler.create(parser, p, rd));

        // Process the referenced managed object definition and its
        // sub-types.
        processRelation(p, rd);
      } catch (ArgumentException e) {
        exception = e;
      }

      return null;
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        Void visitSingleton(
        SingletonRelationDefinition<C, S> rd, ManagedObjectPath<?, ?> p) {
      try {
        // Create the sub-commands.
        getPropHandlers.add(GetPropSubCommandHandler.create(parser, p, rd));
        setPropHandlers.add(SetPropSubCommandHandler.create(parser, p, rd));

        // Process the referenced managed object definition and its
        // sub-types.
        processRelation(p, rd);
      } catch (ArgumentException e) {
        exception = e;
      }

      return null;
    }

  }

  // The set of all available sub-commands.
  private SortedSet<SubCommandHandler> allHandlers =
    new TreeSet<SubCommandHandler>();

  // The set of create-xxx available sub-commands.
  private SortedSet<CreateSubCommandHandler<?, ?>> createHandlers =
    new TreeSet<CreateSubCommandHandler<?, ?>>();

  // The set of delete-xxx available sub-commands.
  private SortedSet<DeleteSubCommandHandler> deleteHandlers =
    new TreeSet<DeleteSubCommandHandler>();

  // Any exception that occurred whilst creating the sub-commands.
  private ArgumentException exception = null;

  // The set of get-xxx-prop available sub-commands.
  private SortedSet<GetPropSubCommandHandler> getPropHandlers =
    new TreeSet<GetPropSubCommandHandler>();

  // The help sub-command handler.
  private HelpSubCommandHandler helpHandler = null;

  // The set of list-xxx available sub-commands.
  private SortedSet<ListSubCommandHandler> listHandlers =
    new TreeSet<ListSubCommandHandler>();

  // The sub-command argument parser.
  private final SubCommandArgumentParser parser;

  // The set of set-xxx-prop available sub-commands.
  private SortedSet<SetPropSubCommandHandler> setPropHandlers =
    new TreeSet<SetPropSubCommandHandler>();

  // The relation visitor.
  private final Visitor visitor = new Visitor();



  /**
   * Create a new sub-command builder.
   *
   * @param parser
   *          The sub-command argument parser.
   * @throws ArgumentException
   *           If a sub-command could not be created successfully.
   */
  public SubCommandHandlerFactory(
      SubCommandArgumentParser parser) throws ArgumentException {
    this.parser = parser;

    // We always need a help properties sub-command handler.
    helpHandler = HelpSubCommandHandler.create(parser);

    processPath(ManagedObjectPath.emptyPath());

    allHandlers.add(helpHandler);
    allHandlers.addAll(createHandlers);
    allHandlers.addAll(deleteHandlers);
    allHandlers.addAll(listHandlers);
    allHandlers.addAll(getPropHandlers);
    allHandlers.addAll(setPropHandlers);

    if (exception != null) {
      throw exception;
    }
  }



  /**
   * Gets all the sub-command handlers.
   *
   * @return Returns all the sub-command handlers.
   */
  public SortedSet<SubCommandHandler> getAllSubCommandHandlers() {
    return allHandlers;
  }



  /**
   * Gets all the create-xxx sub-command handlers.
   *
   * @return Returns all the create-xxx sub-command handlers.
   */
  public SortedSet<CreateSubCommandHandler<?, ?>>
      getCreateSubCommandHandlers() {
    return createHandlers;
  }



  /**
   * Gets all the delete-xxx sub-command handlers.
   *
   * @return Returns all the delete-xxx sub-command handlers.
   */
  public SortedSet<DeleteSubCommandHandler> getDeleteSubCommandHandlers() {
    return deleteHandlers;
  }



  /**
   * Gets all the get-xxx-prop sub-command handlers.
   *
   * @return Returns all the get-xxx-prop sub-command handlers.
   */
  public SortedSet<GetPropSubCommandHandler> getGetPropSubCommandHandlers() {
    return getPropHandlers;
  }



  /**
   * Gets all the list-xxx sub-command handlers.
   *
   * @return Returns all the list-xxx sub-command handlers.
   */
  public SortedSet<ListSubCommandHandler> getListSubCommandHandlers() {
    return listHandlers;
  }



  /**
   * Gets all the set-xxx-prop sub-command handlers.
   *
   * @return Returns all the set-xxx-prop sub-command handlers.
   */
  public SortedSet<SetPropSubCommandHandler> getSetPropSubCommandHandlers() {
    return setPropHandlers;
  }



  // Process the relations associated with the managed object
  // definition identified by the provided path.
  private void processPath(ManagedObjectPath<?, ?> path) {
    AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();

    // Do not process inherited relation definitions.
    for (RelationDefinition<?, ?> r : d.getRelationDefinitions()) {
      if (!r.hasOption(RelationOption.HIDDEN)) {
        r.accept(visitor, path);
      }
    }
  }



  // Process an instantiable relation.
  private <C extends ConfigurationClient, S extends Configuration>
      void processRelation(
      ManagedObjectPath<?, ?> path, InstantiableRelationDefinition<C, S> r) {
    AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

    // Process all relations associated directly with this
    // definition.
    helpHandler.registerManagedObjectDefinition(d);
    processPath(path.child(r, d, "DUMMY"));

    // Now process relations associated with derived definitions.
    for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d
        .getAllChildren()) {
      helpHandler.registerManagedObjectDefinition(c);
      processPath(path.child(r, c, "DUMMY"));
    }
  }



  // Process an optional relation.
  private <C extends ConfigurationClient, S extends Configuration>
      void processRelation(
      ManagedObjectPath<?, ?> path, OptionalRelationDefinition<C, S> r) {
    AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

    // Process all relations associated directly with this
    // definition.
    helpHandler.registerManagedObjectDefinition(d);
    processPath(path.child(r, d));

    // Now process relations associated with derived definitions.
    for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d
        .getAllChildren()) {
      helpHandler.registerManagedObjectDefinition(c);
      processPath(path.child(r, c));
    }
  }



  // Process a singleton relation.
  private <C extends ConfigurationClient, S extends Configuration>
      void processRelation(
      ManagedObjectPath<?, ?> path, SingletonRelationDefinition<C, S> r) {
    AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

    // Process all relations associated directly with this
    // definition.
    helpHandler.registerManagedObjectDefinition(d);
    processPath(path.child(r, d));

    // Now process relations associated with derived definitions.
    for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d
        .getAllChildren()) {
      helpHandler.registerManagedObjectDefinition(c);
      processPath(path.child(r, c));
    }
  }
}
