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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.admin.ads;

import javax.naming.ldap.BasicControl;

/**
 * This class implements the LDAP subtree delete control for JNDI.
 */
public class SubtreeDeleteControl extends BasicControl
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class implements <CODE>javax.ldap.naming.Control</CODE>,
   * which extends the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  static final long serialVersionUID = 3941576361457157921L;

  /**
    * Default constructor.
    */
  public SubtreeDeleteControl()
  {
    super("1.2.840.113556.1.4.805");
  }
}
