HTTP/1.1 200 OK
Cache-control: no-cache
Date: Mon, 01 Jan 2001 12:00:00 GMT
Accept-Ranges: none
Server: Sun-Java(tm)-System-Directory/6.2
Content-Type: text/xml; charset="utf-8"
Content-Length: 1467

<?xml version='1.0' encoding='UTF-8' ?>
<soap-env:Envelope 
   xmlns:xsd='http://www.w3.org/2001/XMLSchema' 
   xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
   xmlns:soap-env='http://schemas.xmlsoap.org/soap/envelope/' 
   >
<soap-env:Body>
<batchResponse 
   xmlns:xsd='http://www.w3.org/2001/XMLSchema' 
   xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
   xmlns='urn:oasis:names:tc:DSML:2:0:core' 
   >
   <searchResponse>
   <searchResultEntry dn='cn=Clint Eastwood, ou=Corporate Tax, ou=Fin-Accounting, ou=Americas, ou=Search, o=IMC, c=US'>
      <attr name='cn'>
      <value>Clint Eastwood</value>
      </attr>
      <attr name='sn'>
      <value>Eastwood</value>
      </attr>
      <attr name='givenName'>
      <value>Clint</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalperson</value>
      <value>inetorgperson</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 825 0003</value>
      </attr>
      <attr name='mail'>
      <value>Clint.Eastwood@dirconnect2.org</value>
      </attr>
      <attr name='title'>
      <value>Director</value>
      </attr>
      <attr name='employeeNumber'>
      <value>1100008</value>
      </attr>
   </searchResultEntry>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
</batchResponse>
</soap-env:Body>
</soap-env:Envelope>
