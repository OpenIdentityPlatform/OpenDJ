/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.RelationDefinitionVisitor;
import org.forgerock.opendj.config.RelationOption;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.SubCommandArgumentParser;

/**
 * Uses the administration framework introspection API to construct the dsconfig sub-command handlers.
 */
final class SubCommandHandlerFactory {

    /**
     * A relation definition visitor used to recursively determine the set of available sub-commands.
     */
    private final class Visitor implements RelationDefinitionVisitor<Void, ManagedObjectPath<?, ?>> {

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitInstantiable(
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

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitOptional(
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

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitSet(SetRelationDefinition<C, S> rd,
                ManagedObjectPath<?, ?> p) {
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

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitSingleton(
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

    /** The set of all available sub-commands. */
    private final SortedSet<SubCommandHandler> allHandlers = new TreeSet<>();
    /** The set of create-xxx available sub-commands. */
    private final SortedSet<CreateSubCommandHandler<?, ?>> createHandlers = new TreeSet<>();
    /** The set of delete-xxx available sub-commands. */
    private final SortedSet<DeleteSubCommandHandler> deleteHandlers = new TreeSet<>();
    /** The set of get-xxx-prop available sub-commands. */
    private final SortedSet<GetPropSubCommandHandler> getPropHandlers = new TreeSet<>();
    /** The help sub-command handler. */
    private final HelpSubCommandHandler helpHandler;
    /** The set of list-xxx available sub-commands. */
    private final SortedSet<ListSubCommandHandler> listHandlers = new TreeSet<>();
    /** The set of set-xxx-prop available sub-commands. */
    private final SortedSet<SetPropSubCommandHandler> setPropHandlers = new TreeSet<>();

    /** The sub-command argument parser. */
    private final SubCommandArgumentParser parser;
    /** Any exception that occurred whilst creating the sub-commands. */
    private ArgumentException exception;

    /** The relation visitor. */
    private final Visitor visitor = new Visitor();

    /**
     * Create a new sub-command builder.
     *
     * @param parser
     *            The sub-command argument parser.
     * @throws ArgumentException
     *             If a sub-command could not be created successfully.
     */
    public SubCommandHandlerFactory(SubCommandArgumentParser parser) throws ArgumentException {
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
    public SortedSet<CreateSubCommandHandler<?, ?>> getCreateSubCommandHandlers() {
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

    /**
     * Process the relations associated with the managed object definition identified by the provided path.
     */
    private void processPath(ManagedObjectPath<?, ?> path) {
        AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();

        // Do not process inherited relation definitions.
        for (RelationDefinition<?, ?> r : d.getRelationDefinitions()) {
            if (!r.hasOption(RelationOption.HIDDEN)) {
                r.accept(visitor, path);
            }
        }
    }

    /** Process an instantiable relation. */
    private <C extends ConfigurationClient, S extends Configuration> void processRelation(ManagedObjectPath<?, ?> path,
            InstantiableRelationDefinition<C, S> r) {
        AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

        // Process all relations associated directly with this
        // definition.
        helpHandler.registerManagedObjectDefinition(d);
        processPath(path.child(r, d, "DUMMY"));

        // Now process relations associated with derived definitions.
        for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d.getAllChildren()) {
            helpHandler.registerManagedObjectDefinition(c);
            processPath(path.child(r, c, "DUMMY"));
        }
    }

    /** Process an optional relation. */
    private <C extends ConfigurationClient, S extends Configuration> void processRelation(ManagedObjectPath<?, ?> path,
            OptionalRelationDefinition<C, S> r) {
        AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

        // Process all relations associated directly with this
        // definition.
        helpHandler.registerManagedObjectDefinition(d);
        processPath(path.child(r, d));

        // Now process relations associated with derived definitions.
        for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d.getAllChildren()) {
            helpHandler.registerManagedObjectDefinition(c);
            processPath(path.child(r, c));
        }
    }

    /** Process a set relation. */
    private <C extends ConfigurationClient, S extends Configuration> void processRelation(ManagedObjectPath<?, ?> path,
            SetRelationDefinition<C, S> r) {
        AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

        // Process all relations associated directly with this
        // definition.
        helpHandler.registerManagedObjectDefinition(d);
        processPath(path.child(r, d));

        // Now process relations associated with derived definitions.
        for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d.getAllChildren()) {
            helpHandler.registerManagedObjectDefinition(c);
            processPath(path.child(r, c));
        }
    }

    /** Process a singleton relation. */
    private <C extends ConfigurationClient, S extends Configuration> void processRelation(ManagedObjectPath<?, ?> path,
            SingletonRelationDefinition<C, S> r) {
        AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();

        // Process all relations associated directly with this
        // definition.
        helpHandler.registerManagedObjectDefinition(d);
        processPath(path.child(r, d));

        // Now process relations associated with derived definitions.
        for (AbstractManagedObjectDefinition<? extends C, ? extends S> c : d.getAllChildren()) {
            helpHandler.registerManagedObjectDefinition(c);
            processPath(path.child(r, c));
        }
    }
}
