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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.forgerock.opendj.ldap.ModificationType;
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
  /** The modification type for this modification. */
  private ModificationType modificationType;

  /** The attribute for this modification. */
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
  @Override
  public ModificationType getModificationType()
  {
    return modificationType;
  }



  /**
   * Specifies the modification type for this modification.
   *
   * @param  modificationType  The modification type for this modification.
   */
  @Override
  public void setModificationType(ModificationType modificationType)
  {
    this.modificationType = modificationType;
  }



  /**
   * Retrieves the attribute for this modification.
   *
   * @return  The attribute for this modification.
   */
  @Override
  public RawAttribute getAttribute()
  {
    return attribute;
  }



  /**
   * Specifies the attribute for this modification.
   *
   * @param  attribute  The attribute for this modification.
   */
  @Override
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
  @Override
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
  @Override
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
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDAPModification(type=").append(modificationType);
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
  @Override
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf).append("LDAP Modification").append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Modification Type:  ").append(modificationType);
    buffer.append(" (").append(modificationType.intValue()).append(")");
    buffer.append(EOL);

    buffer.append("  Attribute:");
    buffer.append(EOL);
    attribute.toString(buffer, indent+4);
  }
}

