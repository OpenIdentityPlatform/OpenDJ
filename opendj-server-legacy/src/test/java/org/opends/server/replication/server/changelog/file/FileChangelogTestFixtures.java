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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server.changelog.file;

import java.io.File;
import java.io.IOException;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.TestCaseUtils;
import org.opends.server.crypto.CryptoSuite;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;

import static org.opends.server.TestCaseUtils.*;

/** Fixtures shared by the tests of the file based changelog. */
final class FileChangelogTestFixtures
{
  static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
  static final int KEY_LENGTH = 128;

  private FileChangelogTestFixtures()
  {
    // static helpers only
  }

  /** Returns a replication server listening on a free port, with no connected replica. */
  static ReplicationServer configureReplicationServer(int windowSize, int queueSize)
      throws IOException, ConfigException
  {
    final int changelogPort = findFreePort();
    ReplServerFakeConfiguration replServerFakeCfg =
        new ReplServerFakeConfiguration(changelogPort, null, 0, 2, queueSize, windowSize, null);
    return new ReplicationServer(replServerFakeCfg);
  }

  /** Returns a crypto suite the changelog can encrypt its records with. */
  static CryptoSuite createCryptoSuite(boolean confidential)
  {
    return getServerContext().getCryptoManager().newCryptoSuite(CIPHER_TRANSFORMATION, KEY_LENGTH, confidential);
  }

  /** Returns an empty directory of the provided name under the unit test build directory. */
  static File createCleanDir(String directoryName) throws IOException
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR, buildRoot
            + File.separator + "build");
    path = path + File.separator + "unit-tests" + File.separator + directoryName;
    final File testRoot = new File(path);
    TestCaseUtils.deleteDirectory(testRoot);
    testRoot.mkdirs();
    return testRoot;
  }
}
