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
package org.opends.server.util;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * A set of test cases for the SetupUtils class.
 */
public class SetupUtilsTestCase extends UtilTestCase
{
  /**
   * Tests that temporary files are created readable and writable only by the
   * owner on POSIX file systems.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @Test
  public void testCreateTemplateFileIsOwnerAccessOnly() throws Exception
  {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
    {
      throw new SkipException("POSIX file permissions are not supported on this platform");
    }

    File templateFile = SetupUtils.createTemplateFile(
        Collections.singleton("dc=example,dc=com"), 1);
    try
    {
      Set<PosixFilePermission> permissions =
          Files.getPosixFilePermissions(templateFile.toPath());
      assertEquals(permissions,
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }
    finally
    {
      templateFile.delete();
    }
  }
}
