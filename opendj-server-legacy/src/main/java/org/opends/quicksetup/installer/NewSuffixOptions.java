/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.quicksetup.installer;

import java.util.LinkedList;
import java.util.List;

/**
 * This class is used to provide a data model for the Data Options panel of the
 * installer.
 */
public class NewSuffixOptions
{
  /**
   * This enumeration is used to know what the user wants to do for the data
   * (import data or not, what use as source of the data...).
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

  private List<String> baseDns = new LinkedList<>();

  private List<String> ldifPaths = new LinkedList<>();

  private String rejectedFile;
  private String skippedFile;

  private int numberEntries = 2000;

  /**
   * Private constructor.
   * @param baseDns the base DNs of the suffix options.
   */
  private NewSuffixOptions(List<String> baseDns)
  {
    this.baseDns.addAll(baseDns);
  }

  /**
   * Creates a base entry suffix options.
   * @param baseDNs the base DNs of the suffix options.
   * @return a base entry suffix options.
   */
  public static NewSuffixOptions createBaseEntry(List<String> baseDNs)
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
  public static NewSuffixOptions createEmpty(List<String> baseDNs)
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
  public static NewSuffixOptions createImportFromLDIF(List<String> baseDNs,
      List<String> ldifPaths, String rejectedFile, String skippedFile)
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
      List<String> baseDNs, int numberEntries)
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
    return new LinkedList<>(ldifPaths);
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
    return new LinkedList<>(baseDns);
  }

  /**
   * Returns {@link InstallProgressStep} equivalent to the type of new suffix
   * options.
   *
   * @return Returns {@link InstallProgressStep} equivalent to the type of new
   *         suffix options.
   */
  public InstallProgressStep getInstallProgressStep()
  {
    switch (type)
    {
    case CREATE_BASE_ENTRY:
      return InstallProgressStep.CREATING_BASE_ENTRY;
    case IMPORT_FROM_LDIF_FILE:
      return InstallProgressStep.IMPORTING_LDIF;
    case IMPORT_AUTOMATICALLY_GENERATED_DATA:
      return InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED;
    default:
      return null;
    }
  }
}
