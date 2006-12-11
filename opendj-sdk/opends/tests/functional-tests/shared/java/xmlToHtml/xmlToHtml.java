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

import javax.xml.transform.*;
import java.net.*;
import java.io.*;

public class xmlToHtml {

public static void main(String[] args) {
  try {

    // xmlToHtml <xsltfile> <xmlfile> <htmlfile>
    if ( args.length != 3 ) {
      System.out.println("Error: Invalid number of args: " + args.length );
      System.out.println("Usage: xmlToHtml <xsltfile> <xmlfile> <htmlfile>");
      System.exit(1);
    }

    String XSLFileName=args[0];
    String XMLFileName=args[1];
    String HTMLFileName=args[2];

    TransformerFactory transFactory = TransformerFactory.newInstance();

    Transformer transformer = transFactory.newTransformer
      (new javax.xml.transform.stream.StreamSource(XSLFileName));

    transformer.transform
      (new javax.xml.transform.stream.StreamSource(XMLFileName),
       new javax.xml.transform.stream.StreamResult
         (new FileOutputStream(HTMLFileName))
      );

    }

    catch (Exception e) {
      e.printStackTrace( );
    }
  }

}
