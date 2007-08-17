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

package org.opends.messages;

import org.opends.server.TestCaseUtils;
import static org.testng.Assert.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

/**
 * Category Tester.
 *
 */
public class PropertiesFilesTest extends MessagesTestCase {

  @DataProvider(name = "messagePropertiesFiles")
  public Object[][] getMessagePropertiesFiles() {
    File propFilesDir = getPropertiesFilesDirectory();
    assertTrue(propFilesDir.exists(), "Directory " +
            propFilesDir.getAbsolutePath() + " does not exist");
    File[] fileList = propFilesDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.endsWith(".properties");
      }
    });
    Object[][] propFiles = new Object[fileList.length][1];
    for (int i = 0; i < propFiles.length; i++) {
      propFiles[i] = new Object[] { fileList[i] };
    }
    return propFiles;
  }

  /**
   * Tests that a properties file does not have duplicate keys.  This
   * is not enforced by GenerateMessageFile because it uses
   * java.util.Properties to load and process the files which ignores
   * duplicate keys.
   *
   * @param propertiesFile file
   * @throws IOException if problems reading the file
   */
  @Test(dataProvider = "messagePropertiesFiles")
  public void testForDuplicateKeys(File propertiesFile) throws IOException {
    Set<String> keys = new HashSet<String>();
    BufferedReader reader = new BufferedReader(new FileReader(propertiesFile));
    String prevLine = null;
    String line;
    while (null != (line = reader.readLine())) {
      if (!(prevLine == null || prevLine.endsWith("\\")) && // not a value continuation
          !(line.startsWith("#")) && // not a comment
          (line.indexOf('=') > 0)) { // defines a key
        String key = line.substring(0, line.indexOf('='));
        assertFalse(keys.contains(key),
                "Key " + key + " is defined multiple places in " +
                        propertiesFile.getName());
        keys.add(key);
      }
      prevLine = line;
    }

  }

  private File getPropertiesFilesDirectory() {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    return new File(buildRoot,
            "src" + File.separator +
            "messages" + File.separator +
            "messages");
  }

}
