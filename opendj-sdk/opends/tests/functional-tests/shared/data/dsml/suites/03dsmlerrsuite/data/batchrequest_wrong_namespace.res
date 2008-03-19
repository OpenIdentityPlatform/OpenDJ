HTTP/1.1 200 OK
Server: Apache-Coyote/1.1
Content-Type: text/xml
Date: Thu, 29 Nov 2007 08:53:04 GMT
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core" requestID="DSMLv2 namespace identifier is incorrect">
<dsml:errorResponse type="malformedRequest">
<dsml:message>
javax.xml.bind.UnmarshalException
 - with linked exception:
[org.xml.sax.SAXParseException: cvc-elt.1: Cannot find the declaration of element 'batchRequest'.]</dsml:message>
</dsml:errorResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
