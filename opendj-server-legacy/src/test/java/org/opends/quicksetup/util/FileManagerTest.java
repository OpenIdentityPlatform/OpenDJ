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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.quicksetup.util;

import static org.testng.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.QuickSetupTestCase;
import org.opends.quicksetup.TestUtilities;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * FileManager Tester.
 */
@SuppressWarnings("javadoc")
@Test(groups = {"slow"}, singleThreaded = true)
public class FileManagerTest extends QuickSetupTestCase {

  private File fmWorkspace;
  private FileManager fileManager;

  @BeforeClass
  public void setUp() throws Exception {
    fileManager = new FileManager();
    fmWorkspace = new File(TestUtilities.getQuickSetupTestWorkspace(),
            "filemanagertests");
    if (fmWorkspace.exists()) {
      fileManager.deleteRecursively(fmWorkspace);
    }
    if (!fmWorkspace.mkdir()) {
      throw new IllegalStateException("cannot create FileManagerTest workspace");
    }
  }

  @AfterMethod
  public void cleanupWorkspace() throws Exception {
    String[] children = fmWorkspace.list();
    if (children != null) {
      for (String child : children) {
        fileManager.deleteRecursively(new File(fmWorkspace, child));
      }
    }
  }

  /**
   * Tests synchronized.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testSynchronize() throws Exception {
    File s = new File(fmWorkspace, "s");
    File t = new File(fmWorkspace, "t");

    String childOfS = "child of s";
    String childOfT = "child of t";

    createSourceFiles(s, childOfS);
    createSourceFiles(t, childOfT);

    File t_d1 = new File(t, "d1");
    File t_f1a = new File(t_d1, "f1a");
    File t_f1b = new File(t_d1, "f1b");

    File t_d2 = new File(t, "d2");
    File t_f2a = new File(t_d2, "f2a");
    File t_d2b = new File(t_d2, "d2b");
    File t_f2b1 = new File(t_d2b, "f2b1");

    assertTrue(t_d1.exists());
    assertTrue(t_f1a.exists());
    assertTrue(t_f1b.exists());

    assertTrue(t_d2.exists());
    assertTrue(t_f2a.exists());
    assertTrue(t_d2b.exists());
    assertTrue(t_f2b1.exists());

    assertTrue(t_f2b1.delete());
    assertTrue(t_d2b.delete());
    assertTrue(t_f1a.delete());

    fileManager.synchronize(s, t);

    // Make sure files we deleted above were copied from the source dir
    assertTrue(t_f2b1.exists());
    assertEquals(childOfS, contentsOf(t_f2b1));

    assertTrue(t_d2b.exists());

    assertTrue(t_f1a.exists());
    assertEquals(childOfS, contentsOf(t_f1a));

    // Make sure files that pre-existed didn't get touched
    assertEquals(childOfT, contentsOf(t_f1b));
    assertEquals(childOfT, contentsOf(t_f2a));
  }

  /** Tests the rename. */
  @Test
  public void testRenameNonExistentTarget() throws Exception {
    File src = File.createTempFile("src", null);
    File target = new File(src.getParentFile(), "target");
    try {
      if (target.exists()) {
        target.delete();
        assertFalse(target.exists());
      }
      fileManager.rename(src, target);
      assertFalse(src.exists());
      assertTrue(target.exists());
    } finally {
      src.delete();
      target.delete();
    }
  }

  /**
   * Tests the {@link StaticUtils#renameFile(java.io.File, java.io.File)}
   * method.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(enabled = false)
  public void testRenameFileExistentTarget() throws Exception {
    File src = File.createTempFile("src", null);
    File target = File.createTempFile("target", null);
    try {
      StaticUtils.renameFile(src, target);
      assertFalse(src.exists());
      assertTrue(target.exists());
    } finally {
      src.delete();
      target.delete();
    }
  }

  /**
   * Tests the {@link StaticUtils#renameFile(java.io.File, java.io.File)}
   * method.  Renaming locked files is a problem on Windows but not so
   * much on other platforms.
   *
   * @throws Exception If the test failed unexpectedly.
   */
  @Test(enabled = false, groups={"windows"}, expectedExceptions=IOException.class)
  public void testRenameFileLockedTarget() throws Exception {
    File src = File.createTempFile("src", null);
    File target = File.createTempFile("target", null);
    FileChannel c = new RandomAccessFile(target, "rw").getChannel();
    FileLock lock = c.lock();
    try {
      StaticUtils.renameFile(src, target);
    } finally {
      lock.release();
      src.delete();
      target.delete();
    }
  }

  /**
   * Tests basic move.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testMove() throws Exception {

    File fromDir = new File(fmWorkspace, "from");
    fromDir.mkdir();

    File toDir = new File(fmWorkspace, "to");
    toDir.mkdir();

    createSourceFiles(fromDir, "abc");

    fileManager.move(fromDir, toDir);

    assertFalse(fromDir.exists());
    assertTrue(new File(toDir, fromDir.getName()).exists());
  }

  /**
   * Tests basic move with filtering.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testMove2() throws Exception {
    File fromDir = new File(fmWorkspace, "from");
    fromDir.mkdir();

    File toDir = new File(fmWorkspace, "to");
    toDir.mkdir();

    createSourceFiles(fromDir, "abc");

    // Create a filter that should reject the operation
    FileFilter filter = new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return false;
      }
    };
    fileManager.move(fromDir, toDir, filter);

    assertTrue(fromDir.exists());
    assertFalse(new File(toDir, fromDir.getName()).exists());
  }

  /**
   * Tests basic delete.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testDelete() throws Exception {
    File dir = new File(fmWorkspace, "dir");
    dir.mkdir();
    assertTrue(dir.exists());
    fileManager.delete(dir);
    assertFalse(dir.exists());

    File file = new File(fmWorkspace, "file");
    writeContents(file, "abc");
    assertTrue(file.exists());
    fileManager.delete(file);
    assertFalse(file.exists());
  }

  /**
   * Tests basic delete with filtering.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testDelete2() throws Exception {

    // Create a filter that should reject the operation
    FileFilter filter = new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return false;
      }
    };

    File dir = new File(fmWorkspace, "dir");
    dir.mkdir();
    assertTrue(dir.exists());
    fileManager.delete(dir, filter);
    assertTrue(dir.exists());

    File file = new File(fmWorkspace, "file");
    writeContents(file, "abc");
    assertTrue(file.exists());
    fileManager.delete(file, filter);
    assertTrue(file.exists());
  }

  /**
   * Test recursive delete.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testDeleteRecursively() throws Exception {
    File dir = new File(fmWorkspace, "dir");
    createSourceFiles(dir, "abc");
    assertTrue(dir.exists());
    fileManager.deleteRecursively(dir);
    assertFalse(dir.exists());
  }

  /**
   * Test recursive delete with filtering.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testDeleteRecursively1() throws Exception {
    File dir = new File(fmWorkspace, "dir");
    createSourceFiles(dir, "abc");
    File d2 = new File(dir, "d2");
    File d2b = new File(d2, "d2b");
    final File f2b1 = new File(d2b, "f2b1");

    // Test that a filter can stop a delete altogether
    FileFilter rejectOpFilter = new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return false;
      }
    };
    int childCount = countSelfAndAllChildren(dir);
    assertTrue(dir.exists());
    fileManager.deleteRecursively(dir, rejectOpFilter,
            FileManager.DeletionPolicy.DELETE_IMMEDIATELY);
    assertTrue(dir.exists());
    // Make sure we didn't lose any kids
    assertEquals(childCount, countSelfAndAllChildren(dir));

    // Test that using a filter to delete one file works
    FileFilter killChildFileFilter = new FileFilter() {
      @Override
      public boolean accept(File f) {
        return f.equals(f2b1);
      }
    };
    assertTrue(dir.exists());
    assertTrue(f2b1.exists());
    fileManager.deleteRecursively(dir, killChildFileFilter,
            FileManager.DeletionPolicy.DELETE_IMMEDIATELY);
    assertTrue(dir.exists());
    assertEquals(childCount - 1, countSelfAndAllChildren(dir));
    assertFalse(f2b1.exists());
  }

  /**
   * Test basic copy.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testCopy() throws Exception {
    File file = new File(fmWorkspace, "file");
    writeContents(file, "abc");
    File dir = new File(fmWorkspace, "dir");
    String[] children = dir.list();
    assertTrue(children == null || children.length == 0);
    fileManager.copy(file, dir);
    assertTrue(file.exists());
    assertEquals(dir.list().length, 1);
  }

  /**
   * Make sure things fail if target is a file and not a directory.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false, expectedExceptions = ApplicationException.class)
  public void testCopy1() throws Exception {
    File file = new File(fmWorkspace, "file");
    writeContents(file, "abc");
    File file2 = new File(fmWorkspace, "file2");
    writeContents(file2, "abc");
    fileManager.copy(file, file2);
  }

  /**
   * Make sure things fail if target is a file and not a directory.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testCopy2() throws Exception {
    File file = new File(fmWorkspace, "file");
    String NEW_CHILD_CONTENT = "new";
    writeContents(file, NEW_CHILD_CONTENT);
    File dir = new File(fmWorkspace, "dir");
    dir.mkdir();
    File dirChild = new File(dir, "file");
    String ORIGINAL_CHILD_CONTENT = "orinld";
    writeContents(dirChild, ORIGINAL_CHILD_CONTENT);

    // Try a copy without overwriting and make sure the original file didn't get replaced.
    fileManager.copy(file, dir, false);
    assertEquals(contentsOf(dirChild), ORIGINAL_CHILD_CONTENT);

    // New try a copy with overwrite true and make sure the original file got replaced.
    fileManager.copy(file, dir, true);
    assertEquals(contentsOf(dirChild), NEW_CHILD_CONTENT);
  }

  /**
   * Test copy recursively.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testCopyRecursively() throws Exception {
    File source = new File(fmWorkspace, "source");
    createSourceFiles(source, "abc");
    int count = countSelfAndAllChildren(source);

    File dest = new File(fmWorkspace, "dest");
    fileManager.copyRecursively(source, dest);

    File copiedSource = new File(dest, "source");
    assertTrue(copiedSource.exists());
    assertEquals(count, countSelfAndAllChildren(copiedSource));
  }

  /**
   * Tests copy recursively with filtering.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testCopyRecursively1() throws Exception {
    // Test that a filter can stop a delete altogether
    FileFilter rejectOpFilter = new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return false;
      }
    };

    File source = new File(fmWorkspace, "source");
    createSourceFiles(source, "abc");
    File d2 = new File(source, "d2");
    File d2b = new File(d2, "d2b");
    final File f2b1 = new File(d2b, "f2b1");
    File dest = new File(fmWorkspace, "dest");

    // Make sure a copy with a rejection filter doesn't do anything
    fileManager.copyRecursively(source, dest, rejectOpFilter);

    File copiedSource = new File(dest, "source");
    assertFalse(copiedSource.exists());
    assertEquals(countSelfAndAllChildren(dest), 1);

    // Test that using a filter to delete one file works
    FileFilter copyChildFileFilter = new FileFilter() {
      @Override
      public boolean accept(File f) {
        return f.equals(f2b1);
      }
    };
    fileManager.copyRecursively(source, dest, copyChildFileFilter);
    File copiedD2 = new File(copiedSource, "d2");
    File copiedD2b = new File(d2, "d2b");
    final File copiedF2b1 = new File(d2b, "f2b1");
    assertTrue(copiedSource.exists());
    assertEquals(countSelfAndAllChildren(copiedSource), 4);
    assertTrue(copiedD2.exists());
    assertEquals(countSelfAndAllChildren(copiedD2), 3);
    assertTrue(copiedD2b.exists());
    assertEquals(countSelfAndAllChildren(copiedD2b), 2);
    assertTrue(copiedF2b1.exists());
  }

  /**
   * Tests copy recursively with filtering and overwrite.
   * @throws Exception if something unexpected
   */
  @Test(enabled = false)
  public void testCopyRecursively2() throws Exception {
    File source = new File(fmWorkspace, "source");
    String FILES_TO_COPY = "to copy";
    createSourceFiles(source, FILES_TO_COPY);
    File d2 = new File(source, "d2");
    File d2b = new File(d2, "d2b");
    final File f2b1 = new File(d2b, "f2b1");
    File dest = new File(fmWorkspace, "dest");

    File copiedSource = new File(dest, "source");
    File copiedD2 = new File(copiedSource, "d2");
    copiedD2.mkdir();
    File copiedD2b = new File(d2, "d2b");
    copiedD2b.mkdir();
    final File copiedF2b1 = new File(d2b, "f2b1");
    String ORIGINAL = "original";
    writeContents(copiedF2b1, ORIGINAL);

    // Test that using a filter to delete one file works
    FileFilter copyChildFileFilter = new FileFilter() {
      @Override
      public boolean accept(File f) {
        return f.equals(f2b1);
      }
    };
    // With overwrite off make sure it doesn't get replaced
    fileManager.copyRecursively(source, dest, copyChildFileFilter, false);
    assertEquals(ORIGINAL, contentsOf(copiedF2b1));

    // Now with overwrite make sure it gets replaced.
    fileManager.copyRecursively(source, dest, copyChildFileFilter, true);
    assertEquals(ORIGINAL, contentsOf(copiedF2b1));
  }

  @DataProvider(name = "differTestData")
  public Object[][] differTestData() {
    return new Object[][] {
      new Object[] { "abc", "abc" },
      new Object[] { "abc", "xyz" },
      new Object[] { "abc", "abc\n" },
      new Object[] { "abc\n", "abc\n" },
      new Object[] { "abc\nabc", "abc\nabc" },
      new Object[] { "abc\nabc\nabc", "abc\nabc\nabc" }
    };
  }

  @Test(dataProvider = "differTestData")
  public void testFilesDiffer(String contents1, String contents2)
          throws Exception
  {
    File f1 = new File(fmWorkspace, "f1");
    File f2 = new File(fmWorkspace, "f2");
    writeContents(f1, contents1);
    writeContents(f2, contents2);
    if (!contents1.equals(contents2)) {
      assertTrue(fileManager.filesDiffer(f1, f2),
              "File contents '" + contents1 + "' and '" + contents2 + "' are " +
                      "not equal despite what FileManager claims");
    } else {
      assertFalse(fileManager.filesDiffer(f1, f2),
              "File contents '" + contents1 + "' and '" + contents2 + "' are " +
                      "equal despite what FileManager claims");
    }
    f1.delete();
    f2.delete();
  }

  /**
   * Creates a set of file for testing.
   * @param parent of the files.
   * @param childContent content of non-directory files.
   * @throws IOException if something unexpected
   */
  private void createSourceFiles(File parent, String childContent)
          throws IOException {
    if (!parent.exists()) {
      parent.mkdir();
    }
    File d1 = new File(parent, "d1");
    File f1a = new File(d1, "f1a");
    File f1b = new File(d1, "f1b");

    File d2 = new File(parent, "d2");
    File f2a = new File(d2, "f2a");
    File d2b = new File(d2, "d2b");
    File f2b1 = new File(d2b, "f2b1");

    d1.mkdir();
    d2.mkdir();
    d2b.mkdir();

    writeContents(f1a, childContent);
    writeContents(f1b, childContent);
    writeContents(f2a, childContent);
    writeContents(f2b1, childContent);
  }

  private void writeContents(File f, String content) throws IOException {
    if (!f.exists())
    {
      f.createNewFile();
    }
    FileWriter fw = new FileWriter(f);
    fw.write(content);
    fw.flush();
    fw.close();
  }

  private String contentsOf(File f) throws IOException {
    BufferedReader br = new BufferedReader(new FileReader(f));
    StringBuilder sb = new StringBuilder();
    String s;
    s = br.readLine();
    while (s != null) {
      sb.append(s);
      s = br.readLine();
      if (s != null) {
        sb.append(Constants.LINE_SEPARATOR);
      }
    }
    br.close();
    return sb.toString();
  }

  private int countSelfAndAllChildren(File f) {
    int count = 0;
    if (f != null) {
      count = 1;
      if (f.isDirectory()) {
        File[] children = f.listFiles();
        if (children != null) {
          for (File child : children) {
            count += countSelfAndAllChildren(child);
          }
        }
      }
    }
    return count;
  }
}
