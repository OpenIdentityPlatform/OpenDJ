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



import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AggregationRelationDefinition;
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
 * A relation definition visitor which is used to determine the
 * run-time sub-commands which are available.
 */
final class SubCommandBuilder {

  /**
   * A relation definition visitor used to recursively determine the
   * set of available sub-commands.
   */
  private static final class Visitor implements
      RelationDefinitionVisitor<Void, ManagedObjectPath<?, ?>> {

    // The application.
    private final ConsoleApplication app;

    // Any exception that occurred whilst creating the sub-commands.
    private ArgumentException exception = null;

    // The set of available sub-commands.
    private List<SubCommandHandler> handlers = null;

    // The help sub-command handler.
    private HelpSubCommandHandler helpHandler = null;

    // The sub-command argument parser.
    private final SubCommandArgumentParser parser;

    // Private constructor.
    private Visitor(ConsoleApplication app, SubCommandArgumentParser parser) {
      this.app = app;
      this.parser = parser;
    }



    /**
     * Get the constructed list of sub-commands handlers.
     *
     * @return Returns the constructed list of sub-commands handlers.
     * @throws ArgumentException
     *           If a sub-command could not be created successfully.
     */
    public List<SubCommandHandler> getSubCommandHandlers()
        throws ArgumentException {
      if (handlers == null) {
        handlers = new LinkedList<SubCommandHandler>();

        // We always need a help properties sub-command handler.
        helpHandler = HelpSubCommandHandler.create(app, parser);
        handlers.add(helpHandler);

        processPath(ManagedObjectPath.emptyPath());
      }

      if (exception != null) {
        throw exception;
      }

      return handlers;
    }



    /**
     * {@inheritDoc}
     */
    public Void visitAggregation(AggregationRelationDefinition<?, ?> r,
        ManagedObjectPath<?, ?> p) {
      // Do not create sub-commands for aggregations.
      return null;
    }



    /**
     * {@inheritDoc}
     */
    public Void visitInstantiable(InstantiableRelationDefinition<?, ?> r,
        ManagedObjectPath<?, ?> p) {
      try {
        // Create the sub-commands.
        handlers.add(CreateSubCommandHandler.create(app, parser, p, r));
        handlers.add(DeleteSubCommandHandler.create(app, parser, p, r));
        handlers.add(ListSubCommandHandler.create(app, parser, p, r));
        handlers.add(GetPropSubCommandHandler.create(app, parser, p, r));
        handlers.add(SetPropSubCommandHandler.create(app, parser, p, r));

        // Process the referenced managed object definition and its
        // sub-types.
        processRelation(p, r);
      } catch (ArgumentException e) {
        exception = e;
      }

      return null;
    }



    /**
     * {@inheritDoc}
     */
    public Void visitOptional(OptionalRelationDefinition<?, ?> r,
        ManagedObjectPath<?, ?> p) {
      try {
        // Create the sub-commands.
        handlers.add(CreateSubCommandHandler.create(app, parser, p, r));
        handlers.add(DeleteSubCommandHandler.create(app, parser, p, r));
        handlers.add(ListSubCommandHandler.create(app, parser, p, r));
        handlers.add(GetPropSubCommandHandler.create(app, parser, p, r));
        handlers.add(SetPropSubCommandHandler.create(app, parser, p, r));

        // Process the referenced managed object definition and its
        // sub-types.
        processRelation(p, r);
      } catch (ArgumentException e) {
        exception = e;
      }

      return null;
    }



    /**
     * {@inheritDoc}
     */
    public Void visitSingleton(SingletonRelationDefinition<?, ?> r,
        ManagedObjectPath<?, ?> p) {
      try {
        // Create the sub-commands.
        handlers.add(GetPropSubCommandHandler.create(app, parser, p, r));
        handlers.add(SetPropSubCommandHandler.create(app, parser, p, r));

        // Process the referenced managed object definition and its
        // sub-types.
        processRelation(p, r);
      } catch (ArgumentException e) {
        exception = e;
      }

      return null;
    }



    // Process the relations associated with the managed object
    // definition identified by the provided path.
    private void processPath(ManagedObjectPath<?, ?> path) {
      AbstractManagedObjectDefinition<?, ?> d = path
          .getManagedObjectDefinition();

      // Do not process inherited relation definitions.
      for (RelationDefinition<?, ?> r : d.getRelationDefinitions()) {
        if (!r.hasOption(RelationOption.HIDDEN)) {
          r.accept(this, path);
        }
      }
    }



    // Process an instantiable relation.
    private <C extends ConfigurationClient, S extends Configuration>
        void processRelation(
        ManagedObjectPath<?, ?> path, InstantiableRelationDefinition<C, S> r) {
      AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

      // Process all relations associated directly with this definition.
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

      // Process all relations associated directly with this definition.
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

      // Process all relations associated directly with this definition.
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



  /**
   * Create a new sub-command builder.
   */
  public SubCommandBuilder() {
    // No implementation required.
  }



  /**
   * Get the set of sub-command handlers constructed by this builder.
   *
   * @param app
   *          The console application.
   * @param parser
   *          The sub-command argument parser.
   * @return Returns the set of sub-command handlers constructed by
   *         this builder.
   * @throws ArgumentException
   *           If a sub-command could not be created successfully.
   */
  public Collection<SubCommandHandler> getSubCommandHandlers(
      ConsoleApplication app, SubCommandArgumentParser parser)
      throws ArgumentException {
    Visitor v = new Visitor(app, parser);
    return v.getSubCommandHandlers();
  }

}
