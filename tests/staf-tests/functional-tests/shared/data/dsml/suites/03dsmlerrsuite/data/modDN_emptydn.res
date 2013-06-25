HTTP/1.1 200 OK
X-Powered-By: Servlet/2.5
Server: Sun Java System Application Server 9.1
Content-Type: text/xml
Date: Thu, 29 Nov 2007 15:20:45 GMT
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core" requestID="Mandatory attribute [dn] of [modDNRequest] element is empty">
<dsml:modDNResponse>
<dsml:resultCode code="53"/>
<dsml:errorMessage>
A modify DN operation cannot be performed on entry  because the new RDN would not have a parent DN</dsml:errorMessage>
</dsml:modDNResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
