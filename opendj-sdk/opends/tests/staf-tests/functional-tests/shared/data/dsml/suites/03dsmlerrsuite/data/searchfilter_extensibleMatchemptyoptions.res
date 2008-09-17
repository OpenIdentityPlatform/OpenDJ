HTTP/1.1 200 OK
X-Powered-By: Servlet/2.5
Server: Sun Java System Application Server 9.1
Content-Type: text/xml
Date: Wed, 28 Nov 2007 16:01:25 GMT
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core" requestID="All optional attribute of [extensibleMatch] element are empty">
<dsml:errorResponse type="malformedRequest">
<dsml:message>
javax.xml.bind.UnmarshalException
 - with linked exception:
[org.xml.sax.SAXParseException: cvc-pattern-valid: Value '' is not facet-valid with respect to pattern '((([0-2](\.[0-9]+)+)|([a-zA-Z]+([a-zA-Z0-9]|[\-])*))(;([a-zA-Z0-9]|[\-])+)*)' for type 'AttributeDescriptionValue'.]</dsml:message>
</dsml:errorResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
