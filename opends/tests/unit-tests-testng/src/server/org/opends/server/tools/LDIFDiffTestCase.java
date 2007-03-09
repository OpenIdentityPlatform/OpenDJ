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
package org.opends.server.tools;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.Base64;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static org.testng.Assert.*;



/**
 * A set of test cases for the LDIFDiff tool.
 */
public class LDIFDiffTestCase
       extends ToolsTestCase
{
  // The path to the file that will be used if there are no differences between
  // the source and target LDIF data sets.
  private String noDiffsFile =
       System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT) + File.separator +
       "tests" + File.separator + "unit-tests-testng" + File.separator +
       "resource" + File.separator + "ldif-diff" + File.separator +
       "diff-nochanges.ldif";



  /**
   * Make sure that the server is running, since we need it for schema
   * handling.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the LDIFDiff tool with an argument that will simply cause it to
   * display usage information.
   */
  @Test()
  public void testUsage()
  {
    String[] args = { "--help" };
    assertEquals(LDIFDiff.mainDiff(args, true), 0);

    args = new String[] { "-H" };
    assertEquals(LDIFDiff.mainDiff(args, true), 0);

    args = new String[] { "-?" };
    assertEquals(LDIFDiff.mainDiff(args, true), 0);

    args = new String[] { "/?" };
    assertEquals(LDIFDiff.mainDiff(args, true), 0);
  }



  /**
   * Tests the LDIFDiff tool with an invalid set of arguments.
   */
  @Test()
  public void testInvalidArguments()
  {
    String[] args =
    {
      "--invalid"
    };

    assertFalse(LDIFDiff.mainDiff(args, true) == 0);
  }



  /**
   * Retrieves the names of the files that should be used when testing the
   * ldif-diff tool.  Each element of the outer array should be an array
   * containing the following elements:
   * <OL>
   *   <LI>The path to the source LDIF file</LI>
   *   <LI>The path to the target LDIF file</LI>
   *   <LI>The path to the diff file, or {@code null} if the diff is supposed
   *       to fail</LI>
   * </OL>
   */
  @DataProvider(name = "testdata")
  public Object[][] getTestData()
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String ldifRoot  = buildRoot + File.separator + "tests" + File.separator +
                       "unit-tests-testng" + File.separator + "resource" +
                       File.separator + "ldif-diff" + File.separator;

    return new Object[][]
    {
      // Both files are empty.
      new Object[] { ldifRoot + "source-empty.ldif",
                     ldifRoot + "target-empty.ldif",
                     noDiffsFile, noDiffsFile },

      // Both files are the single-entry source.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "source-singleentry.ldif",
                     noDiffsFile, noDiffsFile },

      // Both files are the single-entry target.
      new Object[] { ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     noDiffsFile, noDiffsFile },

      // Both files are the multiple-entry source.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "source-multipleentries.ldif",
                     noDiffsFile, noDiffsFile },

      // Both files are the multiple-entry target.
      new Object[] { ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     noDiffsFile, noDiffsFile },

      // The source is empty but the target has a single entry.
      new Object[] { ldifRoot + "source-empty.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "diff-emptytosingle.ldif",
                     ldifRoot + "diff-emptytosingle.ldif" },

      // The source has a single entry but the target is empty.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-empty.ldif",
                     ldifRoot + "diff-singletoempty.ldif",
                     ldifRoot + "diff-singletoempty.ldif" },

      // Make a change to only a single entry in the source->target direction.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "diff-singleentry.ldif",
                     ldifRoot + "diff-singleentry.ldif" },

      // Make a change to only a single entry in the target->source direction.
      new Object[] { ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "diff-singleentry-reverse.ldif",
                     ldifRoot + "diff-singleentry-reverse.ldif" },

      // Make changes to multiple entries in the source->target direction.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "diff-multipleentries.ldif",
                     ldifRoot + "diff-multipleentries-singlevalue.ldif" },

      // Make changes to multiple entries in the target->source direction.
      new Object[] { ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "diff-multipleentries-reverse.ldif",
                     ldifRoot +
                          "diff-multipleentries-reverse-singlevalue.ldif" },

      // Go from one entry to multiple in the source->target direction.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "diff-singletomultiple.ldif",
                     ldifRoot + "diff-singletomultiple-singlevalue.ldif" },

      // Go from one entry to multiple in the target->source direction.
      new Object[] { ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "diff-singletomultiple-reverse.ldif",
                     ldifRoot + "diff-singletomultiple-reverse.ldif" },

      // Go from multiple entries to one in the source->target direction.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "diff-multipletosingle.ldif",
                     ldifRoot + "diff-multipletosingle.ldif" },

      // Go from multiple entries to one in the target->source direction.
      new Object[] { ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "diff-multipletosingle-reverse.ldif",
                     ldifRoot +
                          "diff-multipletosingle-reverse-singlevalue.ldif" },

      // The source file doesn't exist.
      new Object[] { ldifRoot + "source-notfound.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     null, null },

      // The target file doesn't exist.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-notfound.ldif",
                     null, null }
    };
  }




  /**
   * Tests the LDIFDiff tool with the provided information to ensure that the
   * normal mode of operation works as expected.  This is a bit tricky because
   * the attributes and values will be written in an indeterminite order, so we
   * can't just use string equality.  We'll have to use a crude checksum
   * mechanism to test whether they are equal.  Combined with other methods in
   * this class, this should be good enough.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testVerifyNormal(String sourceFile, String targetFile,
                               String normalDiffFile,
                               String singleValueDiffFile)
         throws Exception
  {
    File outputFile = File.createTempFile("difftest", "ldif");
    outputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", outputFile.getAbsolutePath(),
      "-O"
    };

    if (normalDiffFile == null)
    {
      // We expect this to fail, so just make sure that it does.
      assertFalse(LDIFDiff.mainDiff(args, true) == 0);
      outputFile.delete();
      return;
    }

    assertEquals(LDIFDiff.mainDiff(args, true), 0);

    long outputChecksum = 0L;
    BufferedReader reader = new BufferedReader(new FileReader(outputFile));
    String line = reader.readLine();
    while (line != null)
    {
      outputChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    long expectedChecksum = 0L;
    reader = new BufferedReader(new FileReader(normalDiffFile));
    line = reader.readLine();
    while (line != null)
    {
      expectedChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    assertEquals(outputChecksum, expectedChecksum);

    outputFile.delete();
  }




  /**
   * Tests the LDIFDiff tool with the provided information to ensure that the
   * single value changes mode of operation works as expected.  This is a bit
   * tricky because the attributes and values will be written in an
   * indeterminite order, so we can't just use string equality.  We'll have to
   * use a crude checksum mechanism to test whether they are equal.  Combined
   * with other methods in this class, this should be good enough.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testVerifySingleValue(String sourceFile, String targetFile,
                                    String normalDiffFile,
                                    String singleValueDiffFile)
         throws Exception
  {
    File outputFile = File.createTempFile("difftest", "ldif");
    outputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", outputFile.getAbsolutePath(),
      "-O",
      "-S"
    };

    if (singleValueDiffFile == null)
    {
      // We expect this to fail, so just make sure that it does.
      assertFalse(LDIFDiff.mainDiff(args, true) == 0);
      outputFile.delete();
      return;
    }

    assertEquals(LDIFDiff.mainDiff(args, true), 0);

    long outputChecksum = 0L;
    BufferedReader reader = new BufferedReader(new FileReader(outputFile));
    String line = reader.readLine();
    while (line != null)
    {
      outputChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    long expectedChecksum = 0L;
    reader = new BufferedReader(new FileReader(singleValueDiffFile));
    line = reader.readLine();
    while (line != null)
    {
      expectedChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    assertEquals(outputChecksum, expectedChecksum);

    outputFile.delete();
  }



  /**
   * Tests the LDIFDiff tool by first identifying the differences between the
   * source and the target and then using the LDIFModify tool to apply the
   * identified changes to the source LDIF and verify that it matches the
   * target.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testReconstructNormal(String sourceFile, String targetFile,
                                    String normalDiffFile,
                                    String singleValueDiffFile)
         throws Exception
  {
    // If the command is expected to fail, or if there aren't any differences,
    // then bail out now.
    if ((normalDiffFile == null) || normalDiffFile.equals(noDiffsFile))
    {
      return;
    }


    // Generate the diff file.
    File diffOutputFile = File.createTempFile("difftest", "ldif");
    diffOutputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", diffOutputFile.getAbsolutePath()
    };

    assertEquals(LDIFDiff.mainDiff(args, true), 0);


    // Use LDIFModify to generate a new target file.
    File newTargetFile = File.createTempFile("difftest", "newtarget.ldif");
    newTargetFile.deleteOnExit();

    args = new String[]
    {
      "-c", DirectoryServer.getInstance().getConfigFile(),
      "-s", sourceFile,
      "-m", diffOutputFile.getAbsolutePath(),
      "-t", newTargetFile.getAbsolutePath()
    };

    assertEquals(LDIFModify.ldifModifyMain(args, true), 0);


    // Use LDIFDiff again to verify that there are effectively no differences
    // between the original target and the new target.
    File newDiffFile = File.createTempFile("difftest", "newdiff.ldif");
    newDiffFile.deleteOnExit();

    args = new String[]
    {
      "-s", targetFile,
      "-t", newTargetFile.getAbsolutePath(),
      "-o", newDiffFile.getAbsolutePath()
    };

    assertEquals(LDIFDiff.mainDiff(args, true), 0);


    // Read the contents of the new diff file and make sure it matches the
    // contents of the "no changes" diff file.
    long newDiffChecksum = 0L;
    BufferedReader reader = new BufferedReader(new FileReader(newDiffFile));
    String line = reader.readLine();
    while (line != null)
    {
      newDiffChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    long expectedChecksum = 0L;
    reader = new BufferedReader(new FileReader(noDiffsFile));
    line = reader.readLine();
    while (line != null)
    {
      expectedChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    assertEquals(newDiffChecksum, expectedChecksum);

    diffOutputFile.delete();
    newTargetFile.delete();
    newDiffFile.delete();
  }



  /**
   * Tests the LDIFDiff tool by first identifying the differences between the
   * source and the target (using the single-value format) and then using the
   * LDIFModify tool to apply the identified changes to the source LDIF and
   * verify that it matches the target.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testReconstructSingleValue(String sourceFile, String targetFile,
                                         String normalDiffFile,
                                         String singleValueDiffFile)
         throws Exception
  {
    // If the command is expected to fail, or if there aren't any differences,
    // then bail out now.
    if ((normalDiffFile == null) || singleValueDiffFile.equals(noDiffsFile))
    {
      return;
    }


    // Generate the diff file.
    File diffOutputFile = File.createTempFile("difftest", "ldif");
    diffOutputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", diffOutputFile.getAbsolutePath(),
      "-S"
    };

    assertEquals(LDIFDiff.mainDiff(args, true), 0);


    // Use LDIFModify to generate a new target file.
    File newTargetFile = File.createTempFile("difftest", "newtarget.ldif");
    newTargetFile.deleteOnExit();

    args = new String[]
    {
      "-c", DirectoryServer.getInstance().getConfigFile(),
      "-s", sourceFile,
      "-m", diffOutputFile.getAbsolutePath(),
      "-t", newTargetFile.getAbsolutePath()
    };

    assertEquals(LDIFModify.ldifModifyMain(args, true), 0);


    // Use LDIFDiff again to verify that there are effectively no differences
    // between the original target and the new target.
    File newDiffFile = File.createTempFile("difftest", "newdiff.ldif");
    newDiffFile.deleteOnExit();

    args = new String[]
    {
      "-s", targetFile,
      "-t", newTargetFile.getAbsolutePath(),
      "-o", newDiffFile.getAbsolutePath()
    };

    assertEquals(LDIFDiff.mainDiff(args, true), 0);


    // Read the contents of the new diff file and make sure it matches the
    // contents of the "no changes" diff file.
    long newDiffChecksum = 0L;
    BufferedReader reader = new BufferedReader(new FileReader(newDiffFile));
    String line = reader.readLine();
    while (line != null)
    {
      newDiffChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    long expectedChecksum = 0L;
    reader = new BufferedReader(new FileReader(noDiffsFile));
    line = reader.readLine();
    while (line != null)
    {
      expectedChecksum += line.hashCode();
      line = reader.readLine();
    }
    reader.close();

    assertEquals(newDiffChecksum, expectedChecksum);

    diffOutputFile.delete();
    newTargetFile.delete();
    newDiffFile.delete();
  }
}

