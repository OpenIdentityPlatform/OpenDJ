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

/**
 * This class is used to provide a data model for the Data Options panel of the
 * installer.
 *
 */
public class DataOptions
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

  private String baseDn;

  private String ldifPath;

  private int numberEntries;

  /**
   * Constructor for the DataOptions object.
   *
   * If the Data Options is IMPORT_FROM_LDIF_FILE the args are the baseDn and
   * a String with the ldif location.
   *
   * If the Data Options is IMPORT_AUTOMATICALLY_GENERATED_DATA the args
   * are the baseDn and an Integer with the number of entries.
   *
   * For the rest of the types the args are just the baseDn.
   *
   * @param type the Type of DataOptions.
   * @param args the different argument objects (depending on the Type
   * specified)
   */
  public DataOptions(Type type, Object... args)
  {
    this.type = type;
    baseDn = (String) args[0];

    switch (type)
    {
    case IMPORT_FROM_LDIF_FILE:
      ldifPath = (String) args[1];
      break;

    case IMPORT_AUTOMATICALLY_GENERATED_DATA:
      numberEntries = ((Integer) args[1]).intValue();
      break;
    }
  }

  /**
   * Returns the type of DataOptions represented by this object (import data or
   * not, what use as source of the data...).
   *
   * @return the type of DataOptions.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the path of the LDIF file used to import data.
   * @return the path of the LDIF file used to import data.
   */
  public String getLDIFPath()
  {
    return ldifPath;
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
  public String getBaseDn()
  {
    return baseDn;
  }
}
