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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;



/**
 * This class may be used to generate a MakeLDIF template with a specified base
 * DN and number of user entries.
 */
public class CreateTemplate
{
  /**
   * Creates a MakeLDIF template file using the provided information.
   *
   * @param  baseDN      The base DN for the data in the template file.
   * @param  numEntries  The number of user entries the template file should
   *                     create.
   *
   * @return  The {@code File} object that references the created template file.
   *
   * @throws  IOException  If a problem occurs while writing the template file.
   */
  public static File createTemplateFile(String baseDN, int numEntries)
         throws IOException
  {
    File templateFile = File.createTempFile("opends-install", ".template");
    templateFile.deleteOnExit();

    LinkedList<String> lines = new LinkedList<String>();
    lines.add("define suffix=" + baseDN);

    if (numEntries > 0)
    {
      lines.add("define numusers=" + numEntries);
    }

    lines.add("");
    lines.add("branch: [suffix]");
    lines.add("");
    lines.add("branch: ou=People,[suffix]");

    if (numEntries > 0)
    {
      lines.add("subordinateTemplate: person:[numusers]");
      lines.add("");
      lines.add("template: person");
      lines.add("rdnAttr: uid");
      lines.add("objectClass: top");
      lines.add("objectClass: person");
      lines.add("objectClass: organizationalPerson");
      lines.add("objectClass: inetOrgPerson");
      lines.add("givenName: <first>");
      lines.add("sn: <last>");
      lines.add("cn: {givenName} {sn}");
      lines.add("initials: {givenName:1}" +
                "<random:chars:ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}");
      lines.add("employeeNumber: <sequential:0>");
      lines.add("uid: user.{employeeNumber}");
      lines.add("mail: {uid}@maildomain.net");
      lines.add("userPassword: password");
      lines.add("telephoneNumber: <random:telephone>");
      lines.add("homePhone: <random:telephone>");
      lines.add("pager: <random:telephone>");
      lines.add("mobile: <random:telephone>");
      lines.add("street: <random:numeric:5> <file:streets> Street");
      lines.add("l: <file:cities>");
      lines.add("st: <file:states>");
      lines.add("postalCode: <random:numeric:5>");
      lines.add("postalAddress: {cn}${street}${l}, {st}  {postalCode}");
      lines.add("description: This is the description for {cn}.");
    }

    BufferedWriter writer = new BufferedWriter(new FileWriter(templateFile));
    for (String line : lines)
    {
      writer.write(line);
      writer.newLine();
    }

    writer.flush();
    writer.close();

    return templateFile;
  }
}

