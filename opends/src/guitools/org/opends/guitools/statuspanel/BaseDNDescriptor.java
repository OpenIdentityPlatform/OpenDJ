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

package org.opends.guitools.statuspanel;

import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.quicksetup.util.Utils;
import org.opends.server.util.StaticUtils;

/**
 * This class is used to represent a Base DN / Replica and is aimed to be
 * used by the classes in the DatabasesTableModel class.
 *
 */
public class BaseDNDescriptor implements Comparable
{
  /**
   * An enumeration describing the type of base DN for a given Database.
   */
  public enum Type
  {
    /**
     * The base DN is not replicated.
     */
    NOT_REPLICATED,
    /**
     * The base DN is replicated.
     */
    REPLICATED
  };

  private int missingChanges;
  private DatabaseDescriptor db;
  private int ageOfOldestMissingChange;
  private Type type;
  private String baseDn;
  private String unescapedDn;
  private static final Logger LOG =
    Logger.getLogger(BaseDNDescriptor.class.getName());


  /**
   * Constructor for this class.
   * @param type the type of replication.
   * @param baseDn the base DN associated with the Replication.
   * @param db the database containing this base DN.
   * @param ageOfOldestMissingChange the number of missing changes.
   * @param missingChanges the number of missing changes.
   */
  public BaseDNDescriptor(Type type, String baseDn, DatabaseDescriptor db,
      int ageOfOldestMissingChange, int missingChanges)
  {
    this.baseDn = baseDn;
    this.db = db;
    this.type = type;
    this.ageOfOldestMissingChange = ageOfOldestMissingChange;
    this.missingChanges = missingChanges;
    try
    {
      this.unescapedDn = unescapeUtf8(baseDn);
    }
    catch (Throwable t)
    {
      this.unescapedDn = baseDn;
      LOG.log(Level.WARNING, "Error unescaping dn: "+baseDn, t);
    }
  }

  /**
   * Return the String DN associated with the base DN..
   * @return the String DN associated with the base DN.
   */
  public String getDn()
  {
    return baseDn;
  }

  /**
   * Return the String DN associated with the base DN with unescaped UTF-8
   * characters.
   * @return the String DN associated with the base DN with unescaped UTF-8
   * characters.
   */
  public String getUnescapedDn()
  {
    return unescapedDn;
  }

  /**
   * {@inheritDoc}
   */
  public boolean equals(Object v)
  {
    boolean equals = false;
    if (this != v)
    {
      if (v instanceof BaseDNDescriptor)
      {
        BaseDNDescriptor desc = (BaseDNDescriptor)v;
        equals = (getType() == desc.getType()) &&
        Utils.areDnsEqual(getDn(), desc.getDn()) &&
        (getAgeOfOldestMissingChange() == desc.getAgeOfOldestMissingChange()) &&
        (getMissingChanges() == desc.getMissingChanges()) &&
        getDatabase().getBackendID().equals(
            desc.getDatabase().getBackendID()) &&
        (getDatabase().getEntries() == desc.getDatabase().getEntries());
      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }

  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    return (getType().toString() + getAgeOfOldestMissingChange() + getDn() +
        getDatabase().getBackendID() + getMissingChanges()).hashCode();
  }
  /**
   * {@inheritDoc}
   */
  public int compareTo(Object o)
  {
    int returnValue = -1;
    if (o instanceof BaseDNDescriptor)
    {
      BaseDNDescriptor desc = (BaseDNDescriptor)o;
      returnValue = desc.getDn().compareTo(getDn());
    }
    return returnValue;
  }


  /**
   * Returns the number of missing changes in the replication topology for
   * this base DN.
   * @return the number of missing changes in the replication topology for
   * this base DN.
   */
  public int getMissingChanges()
  {
    return missingChanges;
  }

  /**
   * Returns the age of the oldest missing change in seconds in the
   * replication topology for this base DN.
   * @return the age of the oldest missing change in seconds in the
   * replication topology for this base DN.
   */
  public int getAgeOfOldestMissingChange()
  {
    return ageOfOldestMissingChange;
  }

  /**
   * Returns the type for this base DN.
   * @return the type for this base DN.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the database where this base DN is defined.
   * @return the database where this base DN is defined.
   */
  public DatabaseDescriptor getDatabase()
  {
    return db;
  }

  /**
   * Sets the type of this base DN.
   * @param type the new type for this base DN.
   */
  void setType(Type type)
  {
    this.type = type;
  }

  /**
   * Sets the database containing this base DN.
   * @param db the database containing this base DN.
   */
  void setDatabase(DatabaseDescriptor db)
  {
    this.db = db;
  }

  private String unescapeUtf8(String v) throws UnsupportedEncodingException
  {
    byte[] stringBytes = v.getBytes("UTF-8");
    byte[] decodedBytes = new byte[stringBytes.length];
    int pos = 0;
    for (int i = 0; i < stringBytes.length; i++)
    {
      if ((stringBytes[i] == '\\') && (i + 2 < stringBytes.length) &&
          StaticUtils.isHexDigit(stringBytes[i+1]) &&
          StaticUtils.isHexDigit(stringBytes[i+2]))
      {
        // Convert hex-encoded UTF-8 to 16-bit chars.
        byte b;

        byte escapedByte1 = stringBytes[++i];
        switch (escapedByte1)
        {
          case '0':
            b = (byte) 0x00;
            break;
          case '1':
            b = (byte) 0x10;
            break;
          case '2':
            b = (byte) 0x20;
            break;
          case '3':
            b = (byte) 0x30;
            break;
          case '4':
            b = (byte) 0x40;
            break;
          case '5':
            b = (byte) 0x50;
            break;
          case '6':
            b = (byte) 0x60;
            break;
          case '7':
            b = (byte) 0x70;
            break;
          case '8':
            b = (byte) 0x80;
            break;
          case '9':
            b = (byte) 0x90;
            break;
          case 'a':
          case 'A':
            b = (byte) 0xA0;
            break;
          case 'b':
          case 'B':
            b = (byte) 0xB0;
            break;
          case 'c':
          case 'C':
            b = (byte) 0xC0;
            break;
          case 'd':
          case 'D':
            b = (byte) 0xD0;
            break;
          case 'e':
          case 'E':
            b = (byte) 0xE0;
            break;
          case 'f':
          case 'F':
            b = (byte) 0xF0;
            break;
          default:
            throw new IllegalStateException("Unexpected byte: "+escapedByte1);
        }

        byte escapedByte2 = stringBytes[++i];
        switch (escapedByte2)
        {
          case '0':
            break;
          case '1':
            b |= 0x01;
            break;
          case '2':
            b |= 0x02;
            break;
          case '3':
            b |= 0x03;
            break;
          case '4':
            b |= 0x04;
            break;
          case '5':
            b |= 0x05;
            break;
          case '6':
            b |= 0x06;
            break;
          case '7':
            b |= 0x07;
            break;
          case '8':
            b |= 0x08;
            break;
          case '9':
            b |= 0x09;
            break;
          case 'a':
          case 'A':
            b |= 0x0A;
            break;
          case 'b':
          case 'B':
            b |= 0x0B;
            break;
          case 'c':
          case 'C':
            b |= 0x0C;
            break;
          case 'd':
          case 'D':
            b |= 0x0D;
            break;
          case 'e':
          case 'E':
            b |= 0x0E;
            break;
          case 'f':
          case 'F':
            b |= 0x0F;
            break;
          default:
            throw new IllegalStateException("Unexpected byte: "+escapedByte2);
        }

        decodedBytes[pos++] = b;
      }
      else {
        decodedBytes[pos++] = stringBytes[i];
      }
    }
    return new String(decodedBytes, 0, pos, "UTF-8");
  }
}
