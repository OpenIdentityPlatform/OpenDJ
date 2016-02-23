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
 * Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.server.schema.SchemaConstants.*;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.util.ServerConstants;

/**
 * This class defines the Boolean attribute syntax, which only allows values of
 * "TRUE" or "FALSE" (although this implementation is more flexible and will
 * also allow "YES", "ON", or "1" instead of "TRUE", or "NO", "OFF", or "0"
 * instead of "FALSE").  Only equality matching is allowed by default for this
 * syntax.
 */
public class BooleanSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public BooleanSyntax()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public Syntax getSDKSyntax(Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_BOOLEAN_OID);
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getName()
  {
    return SYNTAX_BOOLEAN_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  @Override
  public String getOID()
  {
    return SYNTAX_BOOLEAN_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  @Override
  public String getDescription()
  {
    return SYNTAX_BOOLEAN_DESCRIPTION;
  }

  /**
   * Retrieves an attribute value containing a representation of the provided
   * boolean value.
   *
   * @param  b  The boolean value for which to retrieve the attribute value.
   *
   * @return  The attribute value created from the provided boolean value.
   */
  public static ByteString createBooleanValue(boolean b)
  {
    return b ? ServerConstants.TRUE_VALUE : ServerConstants.FALSE_VALUE;
  }
}

