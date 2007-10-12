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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.messages;



import org.testng.annotations.Test;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Enumeration;
import java.util.Locale;


/**
 * An abstract base class for all messages test cases.
 */
@Test(groups = { "precommit", "messages" }, sequential=true)
public abstract class MessagesTestCase
       extends DirectoryServerTestCase
{
  /** Locale for accessing a pseudo localized test messages file. */
  protected static final Locale TEST_LOCALE = Locale.CHINA;

  /** Message to appear in pseudo localized test messages file. */
  protected static final String TEST_MSG = "XXX";
  protected static final String EOL = System.getProperty("line.separator");

  // No implementation required.
  protected void createDummyLocalizedCoreMessagesFile() throws IOException {
    Properties corePseudoI18nMsgs = new Properties();
    ResourceBundle coreDefaultMsgs = ResourceBundle.getBundle("messages/core");
    Enumeration<String> keyEnum = coreDefaultMsgs.getKeys();
    while (keyEnum.hasMoreElements()) {
      corePseudoI18nMsgs.put(keyEnum.nextElement(), TEST_MSG);
    }
    File buildRoot = new File(System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT));
    File corePseudoI18nMsgsFile = new File(buildRoot,
            "build" + File.separator + "unit-tests" +
                    File.separator + "classes" +
                    File.separator + "messages" +
                    File.separator + "core_" + TEST_LOCALE.getLanguage() +
                    ".properties");
    if (!corePseudoI18nMsgsFile.getParentFile().exists()) {
      corePseudoI18nMsgsFile.getParentFile().mkdirs();
    }
    corePseudoI18nMsgs.store(new FileOutputStream(corePseudoI18nMsgsFile), "");
  }
}

