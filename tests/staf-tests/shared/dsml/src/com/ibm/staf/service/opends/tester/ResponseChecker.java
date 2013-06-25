/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/proctor/resource/legal-notices/Proctor.LICENSE
 * or https://proctor.dev.java.net/Proctor.LICENSE.
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
 *      Portions Copyright 2009 Sun Microsystems, Inc.
 */

package com.ibm.staf.service.opends.tester;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPMessage;
import org.opends.dsml.protocol.BatchResponse;

public class ResponseChecker {

    private Map<String, String> header = new HashMap<String, String>();
    private String httpCode;
    private BatchResponse batchResponse;
    private String body;
    public ResponseChecker(String response) throws Exception {
        this(new StringReader(response));

    }

    public ResponseChecker(Reader response) throws Exception {

        // this.source = new StringBuilder();
        boolean headerDone = false;
        String line = null;
        BufferedReader reader = new BufferedReader(response);
        StringBuilder sb = new StringBuilder();

        // assign first line of header to httpCode
        // then assign all header to <key>:<value> to header map
        // finally fill sb with body of request
        while ((line = reader.readLine()) != null) {
            if (!headerDone) {
                if (line.length() == 0) {
                    headerDone = true;
                }
                if (!headerDone) {
                    if (httpCode == null) {
                        httpCode = line;
                    } else {
                        String[] entry = line.split(":");
                        header.put(entry[0].trim().toLowerCase(), entry[1].trim());
                    }
                }
            } else {
                sb.append(line);
            }
        }
        this.body = sb.toString();

        if (httpCode != null) {
            if (httpCode.split(" ")[1].equals("200")) {

                // reconstruct header for SOAP engine
                MimeHeaders mimeHeaders = new MimeHeaders();
                for (Map.Entry<String, String> entry : header.entrySet()) {
                    String name = entry.getKey();
                    String value = entry.getValue();
                    StringTokenizer token = new StringTokenizer(value, ",");
                    while (token.hasMoreTokens()) {
                        mimeHeaders.addHeader(name, token.nextToken().trim());
                    }
                }
                MessageFactory messageFactory = MessageFactory.newInstance();
                SOAPMessage message = messageFactory.createMessage(mimeHeaders, new ByteArrayInputStream(this.body.getBytes()));

                //DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                //dbf.setNamespaceAware(true);
                //DocumentBuilder db = dbf.newDocumentBuilder();
                //Document doc = db.newDocument();
                SOAPBody soapBody = message.getSOAPBody();

                Iterator iterator = soapBody.getChildElements();
                while (iterator.hasNext()) {
                    Object object = iterator.next();
                    if (!(object instanceof SOAPElement)) {
                        continue;
                    }
                    SOAPElement soapElement = (SOAPElement) object;

                    JAXBContext jaxbContext = JAXBContext.newInstance(BatchResponse.class);
                    Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

                    JAXBElement<BatchResponse> batchResponseElement = unmarshaller.unmarshal(soapElement, BatchResponse.class);

                    this.batchResponse = batchResponseElement.getValue();
                }
            }
        }

    }
   
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ResponseChecker)) {
            return false;
        }
        ResponseChecker other = (ResponseChecker) o;

        // check code "HTTP/1.1 200 OK".split(" ")[1] = "200"
        if (!this.httpCode.split(" ")[1].equals(other.httpCode.split(" ")[1])) {
            return false;
        }

        if (!Checker.equals(batchResponse, other.batchResponse)) {
            return false;
        }

        return true;
    }
}

