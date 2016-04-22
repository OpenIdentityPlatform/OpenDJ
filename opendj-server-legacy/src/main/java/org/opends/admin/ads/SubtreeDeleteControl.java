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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.admin.ads;

import javax.naming.ldap.BasicControl;

/** This class implements the LDAP subtree delete control for JNDI. */
class SubtreeDeleteControl extends BasicControl
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class implements <CODE>javax.ldap.naming.Control</CODE>,
   * which extends the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  static final long serialVersionUID = 3941576361457157921L;

  /** Default constructor. */
  public SubtreeDeleteControl()
  {
    super("1.2.840.113556.1.4.805");
  }
}
