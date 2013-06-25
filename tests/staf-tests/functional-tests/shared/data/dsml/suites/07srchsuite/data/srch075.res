HTTP/1.1 200 OK
Cache-control: no-cache
Date: Mon, 01 Jan 2001 12:00:00 GMT
Accept-Ranges: none
Server: Sun-Java(tm)-System-Directory/6.2
Content-Type: text/xml; charset="utf-8"
Content-Length: 3999

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
   <searchResultEntry dn='cn=Alice Frostad, ou=Operations, ou=Manufacturing, ou=Europe, ou=Search, o=IMC, c=US'>
      <attr name='cn'>
      <value>Alice Frostad</value>
      </attr>
      <attr name='sn'>
      <value>Frostad</value>
      </attr>
      <attr name='givenName'>
      <value>Alice</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalperson</value>
      <value>inetorgperson</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 720 0020</value>
      </attr>
      <attr name='mail'>
      <value>Alice.Frostad@dirconnect2.org</value>
      </attr>
      <attr name='title'>
      <value>Director</value>
      </attr>
      <attr name='employeeNumber'>
      <value>2200020</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=Charlie Abood, ou=Corporate Tax, ou=Fin-Accounting, ou=Europe, ou=Search, o=IMC, c=US'>
      <attr name='cn'>
      <value>Charlie Abood</value>
      </attr>
      <attr name='sn'>
      <value>Abood</value>
      </attr>
      <attr name='givenName'>
      <value>Charlie</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalperson</value>
      <value>inetorgperson</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 721 0004</value>
      </attr>
      <attr name='mail'>
      <value>Charlie.Abood@dirconnect2.org</value>
      </attr>
      <attr name='title'>
      <value>Lawyer</value>
      </attr>
      <attr name='employeeNumber'>
      <value>2200028</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=Henry Atwood, ou=Government, ou=Sales, ou=Europe, ou=Search, o=IMC, c=US'>
      <attr name='cn'>
      <value>Henry Atwood</value>
      </attr>
      <attr name='sn'>
      <value>Atwood</value>
      </attr>
      <attr name='givenName'>
      <value>Henry</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalperson</value>
      <value>inetorgperson</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 721 0045</value>
      </attr>
      <attr name='mail'>
      <value>Henry.Atwood@dirconnect2.org</value>
      </attr>
      <attr name='title'>
      <value>Associate</value>
      </attr>
      <attr name='employeeNumber'>
      <value>2200071</value>
      </attr>
   </searchResultEntry>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
</batchResponse>
</soap-env:Body>
</soap-env:Envelope>
