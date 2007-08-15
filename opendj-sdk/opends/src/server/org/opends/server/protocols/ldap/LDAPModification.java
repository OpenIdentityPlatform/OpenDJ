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
package org.opends.server.protocols.ldap;



import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with an LDAP modification, which describes a change that should be made to an
 * attribute.
 */
public class LDAPModification
       extends RawModification
{
  // The modification type for this modification.
  private ModificationType modificationType;

  // The attribute for this modification.
  private RawAttribute attribute;



  /**
   * Creates a new LDAP modification with the provided type and attribute.
   *
   * @param  modificationType  The modification type for this modification.
   * @param  attribute         The attribute for this modification.
   */
  public LDAPModification(ModificationType modificationType,
                          RawAttribute attribute)
  {
    this.modificationType = modificationType;
    this.attribute        = attribute;
  }



  /**
   * Retrieves the modification type for this modification.
   *
   * @return  The modification type for this modification.
   */
  public ModificationType getModificationType()
  {
    return modificationType;
  }



  /**
   * Specifies the modification type for this modification.
   *
   * @param  modificationType  The modification type for this modification.
   */
  public void setModificationType(ModificationType modificationType)
  {
    this.modificationType = modificationType;
  }



  /**
   * Retrieves the attribute for this modification.
   *
   * @return  The attribute for this modification.
   */
  public RawAttribute getAttribute()
  {
    return attribute;
  }



  /**
   * Specifies the attribute for this modification.
   *
   * @param  attribute  The attribute for this modification.
   */
  public void setAttribute(RawAttribute attribute)
  {
    this.attribute = attribute;
  }



  /**
   * Creates a new core <CODE>Modification</CODE> object from this LDAP
   * modification.
   *
   * @return  The decoded modification.
   *
   * @throws  LDAPException  If a problem occurs while trying to convert the
   *                         LDAP attribute to a core <CODE>Attribute</CODE>.
   */
  public Modification toModification()
         throws LDAPException
  {
    return new Modification(modificationType, attribute.toAttribute());
  }



  /**
   * Retrieves a string representation of this modification.
   *
   * @return  A string representation of this modification.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this modification to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPModification(type=");
    buffer.append(String.valueOf(modificationType));
    buffer.append(", attr=");
    attribute.toString(buffer);
    buffer.append("})");
  }



  /**
   * Appends a multi-line string representation of this LDAP modification to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("LDAP Modification");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Modification Type:  ");
    buffer.append(String.valueOf(modificationType));
    buffer.append(" (");
    buffer.append(modificationType.intValue());
    buffer.append(")");
    buffer.append(EOL);

    buffer.append("  Attribute:");
    buffer.append(EOL);
    attribute.toString(buffer, indent+4);
  }
}

