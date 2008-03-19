HTTP/1.1 200 OK
X-Powered-By: Servlet/2.5
Server: Sun Java System Application Server 9.1
Content-Type: text/xml
Date: Thu, 29 Nov 2007 15:25:35 GMT
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core" requestID="Mandatory attribute [newrdn] of [modDNRequest] element is empty">
<dsml:modDNResponse>
<dsml:resultCode code="32"/>
<dsml:errorMessage>
The modify DN operation for entry uid=abergin,ou=People,dc=siroe,dc=com cannot be performed because no backend is registered to handle that DN</dsml:errorMessage>
</dsml:modDNResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
