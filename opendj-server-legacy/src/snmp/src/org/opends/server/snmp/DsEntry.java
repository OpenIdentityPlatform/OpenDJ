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
 * Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.snmp;

import javax.management.ObjectName;

/**
 * This interface should be implemented by all the DS:OID MBean and
 * allows to get the ObjectName of a SNMP OID MBean.
 */
public interface DsEntry {

  /**
   * Returns the ObjectName of the SNMP Entry.
   * @return the ObjectName of the SNMP Entry
   */
  public ObjectName getObjectName();

}
