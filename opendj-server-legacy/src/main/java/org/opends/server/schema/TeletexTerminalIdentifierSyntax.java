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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.server.config.server.AttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;

/**
 * This class implements the teletex terminal identifier attribute syntax, which
 * contains a printable string (the terminal identifier) followed by zero or
 * more parameters, which start with a dollar sign and are followed by a
 * parameter name, a colon, and a value.  The parameter value should consist of
 * any string of bytes (the dollar sign and backslash must be escaped with a
 * preceding backslash), and the parameter name must be one of the following
 * strings:
 * <UL>
 *   <LI>graphic</LI>
 *   <LI>control</LI>
 *   <LI>misc</LI>
 *   <LI>page</LI>
 *   <LI>private</LI>
 * </UL>
 */
public class TeletexTerminalIdentifierSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public TeletexTerminalIdentifierSyntax()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_TELETEX_TERM_ID_OID);
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getName()
  {
    return SYNTAX_TELETEX_TERM_ID_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  @Override
  public String getOID()
  {
    return SYNTAX_TELETEX_TERM_ID_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  @Override
  public String getDescription()
  {
    return SYNTAX_TELETEX_TERM_ID_DESCRIPTION;
  }
}

