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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 *      Portions Copyright 2006 Brighton Consulting, Inc.
 */
package org.opends.server.tools.makeldif;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.ToolsTestCase;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


/**
 * A set of test cases for the MakeLDIF tool.
 */
public class MakeLDIFTestCase
    extends ToolsTestCase
{
  private String resourcePath = null;

  @BeforeClass
  public void setUp() throws Exception
  {
    // The server must be running for these tests, so that
    // it can provide "getServerRoot()".
    TestCaseUtils.startServer();

    resourcePath = DirectoryServer.getServerRoot() + File.separator +
         "config" + File.separator + "MakeLDIF";
  }

  /**
   * Test to show that reporting an error about an
   * uninitialized variable when generating templates reports the
   * correct line.
   */
  @Test()
  public void testParseTemplate() throws Exception 
  {
    String[] lines =
    {
      /* 0 */ "template: template",
      /* 1 */ "a: {missingVar}",
      /* 2 */ "a: b",
      /* 3 */ "a: c",
      /* 4 */ "",
      /* 5 */ "template: template2",
    };
    
    // Test must show "missingVar" missing on line 1.  
    // Previous behaviour showed "missingVar" on line 5.
    
    TemplateFile templateFile = new TemplateFile(resourcePath);
    List<String> warns = new ArrayList<String>();

    try
    {
      templateFile.parse(lines, warns);
    }
    catch (InitializationException e)
    {
      String msg = e.getMessage();
      assertTrue( msg.contains("line 1"), msg );
    }
  }
}

