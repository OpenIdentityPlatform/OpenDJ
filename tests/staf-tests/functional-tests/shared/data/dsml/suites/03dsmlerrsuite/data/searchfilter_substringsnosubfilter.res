HTTP1.1 200 OK
Date: Wed, 30 Sep 2009 07:55:55 GMT
Server: Apache-Coyote/1.1
Content-Type: text/xml
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<batchResponse xmlns="urn:oasis:names:tc:DSML:2:0:core" requestID="[substrings] element has no subfilter [initial], [any], [final]">
<errorResponse type="malformedRequest">
<message>org.opends.server.types.LDAPException: Cannot decode the provided ASN.1 element as an LDAP search filter because the element was null</message>
</errorResponse>
</batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
