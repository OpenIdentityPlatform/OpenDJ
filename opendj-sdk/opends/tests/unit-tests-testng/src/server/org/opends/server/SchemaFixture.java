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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server;

import static org.opends.server.TestCaseUtils.copyDirectory;

import java.io.File;

import org.opends.server.core.DirectoryServer;

/**
 *
 * This fixture makes sure that a directory server instance is available
 * with the core schema files loaded from the source tree's resource
 * directory.
 * <p>
 * This fixture should be used by test cases which need a directory
 * server instance with core schema files loaded.
 */
public final class SchemaFixture {

  /**
   * A factory used to obtain the schema fixture instance.
   */
  @Deprecated
  public static final FixtureFactory<SchemaFixture> FACTORY;

  static {
    FACTORY = new SingletonFixtureFactory<SchemaFixture>(new Factory());
  }

  /**
   * Internal factory implementation.
   */
  @Deprecated
  private static final class Factory implements
      FixtureFactory<SchemaFixture> {

    /**
     * {@inheritDoc}
     */
    @Deprecated
    public SchemaFixture setUp() throws Exception {
      // This fixture requires the configuration fixture.
      ConfigurationFixture fixture = ConfigurationFixture.FACTORY.setUp();

      // Copy over the schema files.
      File tempDirectory = fixture.getInstanceRoot();
      File configDirectory = new File(tempDirectory, "config");
      File schemaDirectory = new File(configDirectory, "schema");
      File resourceDirectory = new File("resource");
      copyDirectory(new File(resourceDirectory, "schema"), schemaDirectory);

      // Initialize and load the schema files.
      DirectoryServer.getInstance().initializeSchema();

      return new SchemaFixture();
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    public void tearDown() throws Exception {
      // TODO: clean up the schema?

      // Make sure resource held by the configuration fixture are
      // released.
      ConfigurationFixture.FACTORY.tearDown();
    }

  }

  /**
   * Create a schema fixture.
   */
  @Deprecated
  private SchemaFixture() {
    // No implementation required.
  }
}
