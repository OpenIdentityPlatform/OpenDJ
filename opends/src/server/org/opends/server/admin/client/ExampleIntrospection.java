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
package org.opends.server.admin.client;



import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.ServerConstants.PROPERTY_SERVER_ROOT;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AggregationRelationDefinition;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelationDefinitionVisitor;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.types.InitializationException;



/**
 * An example application which uses introspection to output a list of
 * potential CLI sub-commands.
 */
public final class ExampleIntrospection implements
    RelationDefinitionVisitor<Void, String> {

  /**
   * Main application.
   *
   * @param args
   *          The command line arguments.
   * @throws ConfigException
   *           If there was a configuration problem.
   * @throws InitializationException
   *           If there was an initialization problem.
   */
  public static void main(String[] args)
      throws InitializationException, ConfigException {
    ExampleIntrospection app = new ExampleIntrospection();

    app.processRootConfiguration();
  }



  // Private constructor.
  private ExampleIntrospection() throws InitializationException,
      ConfigException {
    // Make sure a new instance is created.
    //
    // This is effectively a no-op at the moment, but may do lazy
    // initialization at some point.
    DirectoryServer.getInstance();

    // Initialize minimal features such as key syntaxes.
    DirectoryServer.bootstrapClient();

    // Many things are dependent on JMX to register an alert
    // generator.
    DirectoryServer.initializeJMX();

    removeAllErrorLogPublishers();

    // Initialize the configuration.
    File instanceRoot = new File("build/package/OpenDS-0.1");
    File configResourceDirectory = new File(instanceRoot, "config");
    System.setProperty(PROPERTY_SERVER_ROOT, instanceRoot
        .getAbsolutePath());
    File configFile = new File(configResourceDirectory, "config.ldif");

    DirectoryServer directoryServer = DirectoryServer.getInstance();

    // Bootstrap definition classes.
    ClassLoaderProvider.getInstance().enable();

    directoryServer.initializeConfiguration(ConfigFileHandler.class
        .getName(), configFile.getAbsolutePath());

    // Initialize and load the schema files.
    DirectoryServer.getInstance().initializeSchema();

    // Switch off class name validation in client.
    ClassPropertyDefinition.setAllowClassValidation(false);

    // Switch off attribute type name validation in client.
    AttributeTypePropertyDefinition.setCheckSchema(false);
  }



  /**
   * Process the root configuration definition.
   */
  public void processRootConfiguration() {
    RootCfgDefn d = RootCfgDefn.getInstance();

    String operands = "";
    for (RelationDefinition<?, ?> r : d.getAllRelationDefinitions()) {
      r.accept(this, operands);
    }
  }



  /**
   * {@inheritDoc}
   */
  public Void visitAggregation(AggregationRelationDefinition<?, ?> d,
      String p) {
    System.err.println("Found aggregation \"" + d.getName()
        + "\": aggregations are not supported yet");

    return null;
  }



  /**
   * {@inheritDoc}
   */
  public Void visitInstantiable(
      InstantiableRelationDefinition<?, ?> d, String p) {
    String operands = p + " NAME";

    List<ManagedObjectDefinition<?, ?>> types = getSubTypes(d);
    String typesOption = getSubTypesOption(d, types);

    System.out.println("create-" + d.getName() + typesOption
        + operands + " [PROP:VALUE ...]");
    System.out.println("delete-" + d.getName() + operands);
    System.out.println("list-" + d.getPluralName() + p);
    System.out.println("get-" + d.getName() + "-prop" + operands
        + " [PROP ...]");
    System.out.println("set-" + d.getName() + "-prop" + operands
        + " [PROP[+|-]:VALUE ...]");

    System.out.println();

    // Process relations associated with the referenced definition and
    // its sub-types.
    processRelationDefinition(d, operands);

    return null;
  }



  /**
   * {@inheritDoc}
   */
  public Void visitOptional(OptionalRelationDefinition<?, ?> d,
      String p) {
    String name = d.getName();

    List<ManagedObjectDefinition<?, ?>> types = getSubTypes(d);
    String typesOption = getSubTypesOption(d, types);

    System.out.println("create-" + name + typesOption + p
        + " [PROP:VALUE ...]");
    System.out.println("delete-" + name + p);
    System.out.println("has-" + name + p);
    System.out.println("get-" + name + "-prop" + p + " [PROP ...]");
    System.out.println("set-" + name + "-prop" + p
        + " [PROP[+|-]:VALUE ...]");

    System.out.println();

    // Process relations associated with the referenced definition and
    // its sub-types.
    processRelationDefinition(d, p);

    return null;
  }



  /**
   * {@inheritDoc}
   */
  public Void visitSingleton(SingletonRelationDefinition<?, ?> d,
      String p) {
    String name = d.getName();

    System.out.println("get-" + name + "-prop" + p + " [PROP ...]");
    System.out.println("set-" + name + "-prop" + p
        + " [PROP[+|-]:VALUE ...]");

    System.out.println();

    return null;
  }



  // Get all the possible types that can be associated with a
  // relation.
  private List<ManagedObjectDefinition<?, ?>> getSubTypes(
      RelationDefinition<?, ?> d) {
    List<ManagedObjectDefinition<?, ?>> types =
      new LinkedList<ManagedObjectDefinition<?, ?>>();
    AbstractManagedObjectDefinition<?, ?> ad = d.getChildDefinition();

    getSubTypesHelp(ad, types);

    return types;
  }



  // Get sub-types helper method.
  private void getSubTypesHelp(
      AbstractManagedObjectDefinition<?, ?> ad,
      List<ManagedObjectDefinition<?, ?>> types) {
    // Add this definition if it is not abstract.
    if (ad instanceof ManagedObjectDefinition<?, ?>) {
      ManagedObjectDefinition<?, ?> d = (ManagedObjectDefinition<?, ?>) ad;
      types.add(d);
    }

    // Repeat for children.
    for (AbstractManagedObjectDefinition<?, ?> d : ad.getChildren()) {
      getSubTypesHelp(d, types);
    }
  }



  // Convert a list of sub-types to a "-t" option usage.
  private String getSubTypesOption(RelationDefinition<?, ?> r,
      List<ManagedObjectDefinition<?, ?>> types) {
    AbstractManagedObjectDefinition<?, ?> cd = r.getChildDefinition();
    String base = "";
    if (cd instanceof AbstractManagedObjectDefinition<?, ?>) {
      base = cd.getName();
    }

    StringBuilder builder = new StringBuilder();
    builder.append(" -t ");

    Iterator<ManagedObjectDefinition<?, ?>> i = types.iterator();
    String suffix = "-" + base;
    while (i.hasNext()) {
      ManagedObjectDefinition<?, ?> d = i.next();

      String name = d.getName();
      if (name.equals(cd.getName())) {
        // When the base type is itself instantiable use "generic".
        name = "generic";
      } else if (name.endsWith(suffix)) {
        name = name.substring(0, name.length() - suffix.length());
      }

      builder.append(name);
      if (i.hasNext()) {
        builder.append('|');
      }
    }

    return builder.toString();
  }



  // Process the relations associated with the managed objects
  // referenced from the specified relation.
  private void processRelationDefinition(RelationDefinition<?, ?> d,
      String operands) {
    AbstractManagedObjectDefinition<?, ?> ad = d.getChildDefinition();
    processManagedObjectDefinition(null, ad, operands);
  }



  // Process the relations of a child managed object definition.
  private void processManagedObjectDefinition(
      AbstractManagedObjectDefinition<?, ?> parent,
      AbstractManagedObjectDefinition<?, ?> child, String operands) {
    for (RelationDefinition<?, ?> r : child.getRelationDefinitions()) {
      r.accept(this, operands);
    }

    // Process sub-types.
    for (AbstractManagedObjectDefinition<?, ?> d : child
        .getChildren()) {
      processManagedObjectDefinition(child, d, operands);
    }
  }
}
