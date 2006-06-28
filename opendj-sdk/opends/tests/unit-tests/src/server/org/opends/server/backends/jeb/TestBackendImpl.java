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
package org.opends.server.backends.jeb;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.api.Backend;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.Base64;
import org.opends.server.core.*;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.*;

import java.io.File;
import java.io.ByteArrayInputStream;

/**
 * BackendImpl Tester.
 *
 * @author Andy Coulbeck
 */
public class TestBackendImpl extends DirectoryServerTestCase
{
  private String homeDirName;

  private static final String ldifString =
       "dn: cn=JE Backend,cn=Backends,cn=config\n" +
       "objectClass: top\n" +
       "objectClass: ds-cfg-backend\n" +
       "objectClass: extensibleObject\n" +
       "cn: JE Backend\n" +
       "ds-cfg-backend-enabled: true\n" +
       "ds-cfg-backend-class: org.opends.server.backends.jeb.BackendImpl\n" +
       "ds-cfg-backend-directory: db\n";

  private Entry entry;

  public TestBackendImpl(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();

    DirectoryServer.bootstrapClient();
    DirectoryServer.initializeJMX();

    File tempFile = File.createTempFile("jebtest", "");
    tempFile.delete();
    homeDirName = tempFile.getAbsolutePath();

    final String s = ldifString.replaceAll("ds-cfg-backend-directory: db",
         "ds-cfg-backend-directory:: " +
         Base64.encode(homeDirName.getBytes()));
    byte[] bytes = StaticUtils.getBytes(s);

    LDIFReader reader = new LDIFReader(
         new LDIFImportConfig(new ByteArrayInputStream(bytes)));

    entry = reader.readEntry(false);
    reader.close();
  }

  public void tearDown() throws Exception
  {
    super.tearDown();
    File homeDir = new File(homeDirName);
    homeDir.delete();
  }

  public void testFinalize() throws Exception
  {
    File homeDir = new File(homeDirName);
    homeDir.mkdir();
    ConfigEntry configEntry = new ConfigEntry(entry, null);
    DN[] baseDNs = new DN[] { DN.decode("dc=com") };
    Backend backend = new BackendImpl();
    for (int i = 0; i < 10; i++)
    {
      backend.initializeBackend(configEntry, baseDNs);
      try
      {
        backend.getEntry(DN.decode("dc=com"));
      } catch (DirectoryException e)
      {
      }
      backend.finalizeBackend();
    }
  }


}
