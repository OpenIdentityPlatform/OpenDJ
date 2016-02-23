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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.tools.makeldif;



import java.io.IOException;



/**
 * This interface defines a method that may be used to write entries generated
 * through the MakeLDIF utility.
 */
public interface EntryWriter
{
  /**
   * Writes the provided entry to the appropriate target.
   *
   * @param  entry  The entry to be written.
   *
   * @return  <CODE>true</CODE> if the entry writer will accept additional
   *          entries, or <CODE>false</CODE> if no more entries should be
   *         written.
   *
   * @throws  IOException  If a problem occurs while writing the entry to its
   *                       intended destination.
   *
   * @throws  MakeLDIFException  If some other problem occurs.
   */
  boolean writeEntry(TemplateEntry entry)
         throws IOException, MakeLDIFException;



  /**
   * Notifies the entry writer that no more entries will be provided and that
   * any associated cleanup may be performed.
   */
  void closeEntryWriter();
}

