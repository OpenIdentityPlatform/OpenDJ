HTTP/1.1 200 OK
CONTENT-TYPE: text/xml
CONNECTION: close
SERVER: Sun Java System Application Server 9.1
X-POWERED-BY: Servlet/2.5
DATE: Mon, 17 Dec 2007 15:24:34 GMT

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<batchResponse xmlns="urn:oasis:names:tc:DSML:2:0:core">
<modDNResponse>
<resultCode code="32"/>
<errorMessage>
The modify DN operation for entry ou=MyPeople,ou=Special Users,o=dsmlfe.com cannot be performed because that entry does not exist in the server</errorMessage>
</modDNResponse>
</batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>


