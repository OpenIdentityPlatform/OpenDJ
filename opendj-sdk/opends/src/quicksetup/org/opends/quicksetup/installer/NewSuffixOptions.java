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


package org.opends.quicksetup.installer;

import java.util.LinkedList;


/**
 * This class is used to provide a data model for the Data Options panel of the
 * installer.
 *
 */
public class NewSuffixOptions
{
  /**
   * This enumeration is used to know what the user wants to do for the data
   * (import data or not, what use as source of the data...).
   *
   */
  public enum Type
  {
    /**
     * Create base entry.
     */
    CREATE_BASE_ENTRY,
    /**
     * Do not add any entry to the suffix.
     */
    LEAVE_DATABASE_EMPTY,
    /**
     * Import data from an LDIF file.
     */
    IMPORT_FROM_LDIF_FILE,
    /**
     * Generate data and import it to the suffix.
     */
    IMPORT_AUTOMATICALLY_GENERATED_DATA
  }

  private Type type;

  private LinkedList<String> baseDns = new LinkedList<String>();

  private LinkedList<String> ldifPaths = new LinkedList<String>();

  private String rejectedFile;

  private String skippedFile;

  private int numberEntries = 2000;

  /**
   * Private constructor.
   * @param baseDns the base DNs of the suffix options.
   *
   */
  private NewSuffixOptions(LinkedList<String> baseDns)
  {
    this.baseDns.addAll(baseDns);
  }

  /**
   * Creates a base entry suffix options.
   * @param baseDNs the base DNs of the suffix options.
   * @return a base entry suffix options.
   */
  public static NewSuffixOptions createBaseEntry(LinkedList<String> baseDNs)
  {
    NewSuffixOptions ops = new NewSuffixOptions(baseDNs);
    ops.type = Type.CREATE_BASE_ENTRY;
    return ops;
  }

  /**
   * Creates an empty suffix options.
   * @param baseDNs the base DNs of the suffix options.
   * @return an empty suffix options.
   */
  public static NewSuffixOptions createEmpty(LinkedList<String> baseDNs)
  {
    NewSuffixOptions ops = new NewSuffixOptions(baseDNs);
    ops.type = Type.LEAVE_DATABASE_EMPTY;
    return ops;
  }

  /**
   * Creates a base entry suffix options.
   * @param baseDNs the base DNs of the suffix options.
   * @param ldifPaths the LDIF files to be imported.
   * @param rejectedFile the files where the rejected entries are stored.
   * @param skippedFile the files where the skipped entries are stored.
   * @return a base entry suffix options.
   */
  public static NewSuffixOptions createImportFromLDIF(
      LinkedList<String> baseDNs, LinkedList<String> ldifPaths,
      String rejectedFile, String skippedFile)
  {
    NewSuffixOptions ops = new NewSuffixOptions(baseDNs);
    ops.type = Type.IMPORT_FROM_LDIF_FILE;
    ops.ldifPaths.addAll(ldifPaths);
    ops.rejectedFile = rejectedFile;
    ops.skippedFile = skippedFile;
    return ops;
  }

  /**
   * Creates an automatically generated entries suffix options.
   * @param baseDNs the base DNs of the suffix options.
   * @param numberEntries the number of entries to generate.
   * @return a base entry suffix options.
   */
  public static NewSuffixOptions createAutomaticallyGenerated(
      LinkedList<String> baseDNs, int numberEntries)
  {
    NewSuffixOptions ops = new NewSuffixOptions(baseDNs);
    ops.type = Type.IMPORT_AUTOMATICALLY_GENERATED_DATA;
    ops.numberEntries = numberEntries;
    return ops;
  }

  /**
   * Returns the type of NewSuffixOptions represented by this object (import
   * data or not, what use as source of the data...).
   *
   * @return the type of NewSuffixOptions.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the path of the LDIF file used to import data.
   * @return the path of the LDIF file used to import data.
   */
  public LinkedList<String> getLDIFPaths()
  {
    LinkedList<String> copy = new LinkedList<String>(ldifPaths);
    return copy;
  }

  /**
   * Returns the path to store the rejected entries of the import.
   * <CODE>null</CODE> if no rejected file is specified.
   *
   * @return the path to store the rejected entries of the import.
   * <CODE>null</CODE> if no rejected file is specified.
   */
  public String getRejectedFile()
  {
    return rejectedFile;
  }

  /**
   * Returns the path to store the skipped entries of the import.
   * <CODE>null</CODE> if no skipped file is specified.
   *
   * @return the path to store the skipped entries of the import.
   * <CODE>null</CODE> if no skipped file is specified.
   */
  public String getSkippedFile()
  {
    return skippedFile;
  }

  /**
   * Returns the number of entries that will be automatically generated.
   *
   * @return the number of entries that will be automatically generated.
   */
  public int getNumberEntries()
  {
    return numberEntries;
  }

  /**
   * Returns the base DN of the suffix that will be created in the server.
   *
   * @return the base DN of the suffix that will be created in the server.
   */
  public LinkedList<String> getBaseDns()
  {
    LinkedList<String> copy = new LinkedList<String>(baseDns);
    return copy;
  }
}
