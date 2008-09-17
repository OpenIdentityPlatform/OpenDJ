HTTP/1.1 200 OK
Cache-control: no-cache
Date: Mon, 01 Jan 2001 12:00:00 GMT
Accept-Ranges: none
Server: Sun-Java(tm)-System-Directory/6.2
Content-Type: text/xml; charset="utf-8"
Content-Length: 162274

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
   <searchResultEntry dn='dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>domain</value>
      </attr>
      <attr name='dc'>
      <value>example</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='ou=Groups, dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>organizationalunit</value>
      </attr>
      <attr name='ou'>
      <value>Groups</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=Directory Administrators, ou=Groups, dc=example,dc=com'>
      <attr name='cn'>
      <value>Directory Administrators</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>groupofuniquenames</value>
      </attr>
      <attr name='ou'>
      <value>Groups</value>
      </attr>
      <attr name='uniqueMember'>
      <value>uid=kvaughan, ou=People, dc=example,dc=com</value>
      <value>uid=rdaugherty, ou=People, dc=example,dc=com</value>
      <value>uid=hmiller, ou=People, dc=example,dc=com</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='ou=People, dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>organizationalunit</value>
      </attr>
      <attr name='ou'>
      <value>People</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='ou=Special Users,dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>organizationalUnit</value>
      </attr>
      <attr name='ou'>
      <value>Special Users</value>
      </attr>
      <attr name='description'>
      <value>Special Administrative Accounts</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=scarter, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Sam Carter</value>
      </attr>
      <attr name='sn'>
      <value>Carter</value>
      </attr>
      <attr name='givenName'>
      <value>Sam</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>scarter</value>
      </attr>
      <attr name='mail'>
      <value>scarter@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4798</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>4612</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tmorris, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Ted Morris</value>
      </attr>
      <attr name='sn'>
      <value>Morris</value>
      </attr>
      <attr name='givenName'>
      <value>Ted</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>tmorris</value>
      </attr>
      <attr name='mail'>
      <value>tmorris@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9187</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>4117</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=kvaughan, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Kirsten Vaughan</value>
      </attr>
      <attr name='sn'>
      <value>Vaughan</value>
      </attr>
      <attr name='givenName'>
      <value>Kirsten</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>kvaughan</value>
      </attr>
      <attr name='mail'>
      <value>kvaughan@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5625</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>2871</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=abergin, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Andy Bergin</value>
      </attr>
      <attr name='sn'>
      <value>Bergin</value>
      </attr>
      <attr name='givenName'>
      <value>Andy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>abergin</value>
      </attr>
      <attr name='mail'>
      <value>abergin@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8585</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>3472</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dmiller, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>David Miller</value>
      </attr>
      <attr name='sn'>
      <value>Miller</value>
      </attr>
      <attr name='givenName'>
      <value>David</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>dmiller</value>
      </attr>
      <attr name='mail'>
      <value>dmiller@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9423</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>4135</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=gfarmer, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Gern Farmer</value>
      </attr>
      <attr name='sn'>
      <value>Farmer</value>
      </attr>
      <attr name='givenName'>
      <value>Gern</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>gfarmer</value>
      </attr>
      <attr name='mail'>
      <value>gfarmer@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6201</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>1269</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=kwinters, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Kelly Winters</value>
      </attr>
      <attr name='sn'>
      <value>Winters</value>
      </attr>
      <attr name='givenName'>
      <value>Kelly</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>kwinters</value>
      </attr>
      <attr name='mail'>
      <value>kwinters@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9069</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>4178</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=trigden, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Torrey Rigden</value>
      </attr>
      <attr name='sn'>
      <value>Rigden</value>
      </attr>
      <attr name='givenName'>
      <value>Torrey</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>trigden</value>
      </attr>
      <attr name='mail'>
      <value>trigden@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9280</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>3584</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=cschmith, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Chris Schmith</value>
      </attr>
      <attr name='sn'>
      <value>Schmith</value>
      </attr>
      <attr name='givenName'>
      <value>Chris</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>cschmith</value>
      </attr>
      <attr name='mail'>
      <value>cschmith@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8011</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>0416</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jwallace, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Judy Wallace</value>
      </attr>
      <attr name='sn'>
      <value>Wallace</value>
      </attr>
      <attr name='givenName'>
      <value>Judy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>jwallace</value>
      </attr>
      <attr name='mail'>
      <value>jwallace@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0319</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>1033</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jwalker, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>John Walker</value>
      </attr>
      <attr name='sn'>
      <value>Walker</value>
      </attr>
      <attr name='givenName'>
      <value>John</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>jwalker</value>
      </attr>
      <attr name='mail'>
      <value>jwalker@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1476</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>3915</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tclow, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Torrey Clow</value>
      </attr>
      <attr name='sn'>
      <value>Clow</value>
      </attr>
      <attr name='givenName'>
      <value>Torrey</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>tclow</value>
      </attr>
      <attr name='mail'>
      <value>tclow@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8825</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>4376</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rdaugherty, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Robert Daugherty</value>
      </attr>
      <attr name='sn'>
      <value>Daugherty</value>
      </attr>
      <attr name='givenName'>
      <value>Robert</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>rdaugherty</value>
      </attr>
      <attr name='mail'>
      <value>rdaugherty@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1296</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>0194</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jreuter, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jayne Reuter</value>
      </attr>
      <attr name='sn'>
      <value>Reuter</value>
      </attr>
      <attr name='givenName'>
      <value>Jayne</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>jreuter</value>
      </attr>
      <attr name='mail'>
      <value>jreuter@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1122</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>2942</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tmason, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Torrey Mason</value>
      </attr>
      <attr name='sn'>
      <value>Mason</value>
      </attr>
      <attr name='givenName'>
      <value>Torrey</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>tmason</value>
      </attr>
      <attr name='mail'>
      <value>tmason@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1596</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>1124</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bhall, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Benjamin Hall</value>
      </attr>
      <attr name='sn'>
      <value>Hall</value>
      </attr>
      <attr name='givenName'>
      <value>Benjamin</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>bhall</value>
      </attr>
      <attr name='mail'>
      <value>bhall@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6067</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>2511</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=btalbot, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Brad Talbot</value>
      </attr>
      <attr name='sn'>
      <value>Talbot</value>
      </attr>
      <attr name='givenName'>
      <value>Brad</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>btalbot</value>
      </attr>
      <attr name='mail'>
      <value>btalbot@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4992</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>3532</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mward, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Marcus Ward</value>
      </attr>
      <attr name='sn'>
      <value>Ward</value>
      </attr>
      <attr name='givenName'>
      <value>Marcus</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>mward</value>
      </attr>
      <attr name='mail'>
      <value>mward@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5688</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>1707</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bjablons, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Barbara Jablonski</value>
      </attr>
      <attr name='sn'>
      <value>Jablonski</value>
      </attr>
      <attr name='givenName'>
      <value>Barbara</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>bjablons</value>
      </attr>
      <attr name='mail'>
      <value>bjablons@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8815</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>0906</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jmcFarla, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Judy McFarland</value>
      </attr>
      <attr name='sn'>
      <value>McFarland</value>
      </attr>
      <attr name='givenName'>
      <value>Judy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jmcFarla</value>
      </attr>
      <attr name='mail'>
      <value>jmcFarla@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2567</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>2359</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=llabonte, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Lee Labonte</value>
      </attr>
      <attr name='sn'>
      <value>Labonte</value>
      </attr>
      <attr name='givenName'>
      <value>Lee</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>llabonte</value>
      </attr>
      <attr name='mail'>
      <value>llabonte@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0957</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>2854</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jcampaig, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jody Campaigne</value>
      </attr>
      <attr name='sn'>
      <value>Campaigne</value>
      </attr>
      <attr name='givenName'>
      <value>Jody</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>jcampaig</value>
      </attr>
      <attr name='mail'>
      <value>jcampaig@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1660</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>4385</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bhal2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Barbara Hall</value>
      </attr>
      <attr name='sn'>
      <value>Hall</value>
      </attr>
      <attr name='givenName'>
      <value>Barbara</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>bhal2</value>
      </attr>
      <attr name='mail'>
      <value>bhal2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4491</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>2758</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=alutz, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Alexander Lutz</value>
      </attr>
      <attr name='sn'>
      <value>Lutz</value>
      </attr>
      <attr name='givenName'>
      <value>Alexander</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>alutz</value>
      </attr>
      <attr name='mail'>
      <value>alutz@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6505</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>1327</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=btalbo2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Bjorn Talbot</value>
      </attr>
      <attr name='sn'>
      <value>Talbot</value>
      </attr>
      <attr name='givenName'>
      <value>Bjorn</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>btalbo2</value>
      </attr>
      <attr name='mail'>
      <value>btalbo2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4234</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>1205</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=achassin, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Ashley Chassin</value>
      </attr>
      <attr name='sn'>
      <value>Chassin</value>
      </attr>
      <attr name='givenName'>
      <value>Ashley</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>achassin</value>
      </attr>
      <attr name='mail'>
      <value>achassin@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9972</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>0466</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=hmiller, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Harry Miller</value>
      </attr>
      <attr name='sn'>
      <value>Miller</value>
      </attr>
      <attr name='givenName'>
      <value>Harry</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>hmiller</value>
      </attr>
      <attr name='mail'>
      <value>hmiller@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9804</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>4304</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jcampai2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jeffrey Campaigne</value>
      </attr>
      <attr name='sn'>
      <value>Campaigne</value>
      </attr>
      <attr name='givenName'>
      <value>Jeffrey</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jcampai2</value>
      </attr>
      <attr name='mail'>
      <value>jcampai2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7393</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>1377</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=lulrich, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Lee Ulrich</value>
      </attr>
      <attr name='sn'>
      <value>Ulrich</value>
      </attr>
      <attr name='givenName'>
      <value>Lee</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>lulrich</value>
      </attr>
      <attr name='mail'>
      <value>lulrich@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8652</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>0985</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mlangdon, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Marcus Langdon</value>
      </attr>
      <attr name='sn'>
      <value>Langdon</value>
      </attr>
      <attr name='givenName'>
      <value>Marcus</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>mlangdon</value>
      </attr>
      <attr name='mail'>
      <value>mlangdon@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6249</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>4471</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=striplet, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Stephen Triplett</value>
      </attr>
      <attr name='sn'>
      <value>Triplett</value>
      </attr>
      <attr name='givenName'>
      <value>Stephen</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>striplet</value>
      </attr>
      <attr name='mail'>
      <value>striplet@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4519</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>3083</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=gtriplet, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Gern Triplett</value>
      </attr>
      <attr name='sn'>
      <value>Triplett</value>
      </attr>
      <attr name='givenName'>
      <value>Gern</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>gtriplet</value>
      </attr>
      <attr name='mail'>
      <value>gtriplet@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2582</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>4023</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jfalena, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>John Falena</value>
      </attr>
      <attr name='sn'>
      <value>Falena</value>
      </attr>
      <attr name='givenName'>
      <value>John</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jfalena</value>
      </attr>
      <attr name='mail'>
      <value>jfalena@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8133</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>1917</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=speterso, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Sue Peterson</value>
      </attr>
      <attr name='sn'>
      <value>Peterson</value>
      </attr>
      <attr name='givenName'>
      <value>Sue</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>speterso</value>
      </attr>
      <attr name='mail'>
      <value>speterso@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3613</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>3073</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ejohnson, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Emanuel Johnson</value>
      </attr>
      <attr name='sn'>
      <value>Johnson</value>
      </attr>
      <attr name='givenName'>
      <value>Emanuel</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ejohnson</value>
      </attr>
      <attr name='mail'>
      <value>ejohnson@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3287</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>3737</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=prigden, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Peter Rigden</value>
      </attr>
      <attr name='sn'>
      <value>Rigden</value>
      </attr>
      <attr name='givenName'>
      <value>Peter</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>prigden</value>
      </attr>
      <attr name='mail'>
      <value>prigden@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5099</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>1271</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bwalker, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Brad Walker</value>
      </attr>
      <attr name='sn'>
      <value>Walker</value>
      </attr>
      <attr name='givenName'>
      <value>Brad</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>bwalker</value>
      </attr>
      <attr name='mail'>
      <value>bwalker@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5476</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>3529</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=kjensen, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Kurt Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Kurt</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>kjensen</value>
      </attr>
      <attr name='mail'>
      <value>kjensen@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6127</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>1944</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mlott, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Mike Lott</value>
      </attr>
      <attr name='sn'>
      <value>Lott</value>
      </attr>
      <attr name='givenName'>
      <value>Mike</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>mlott</value>
      </attr>
      <attr name='mail'>
      <value>mlott@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2234</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>0498</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=cwallace, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Cecil Wallace</value>
      </attr>
      <attr name='sn'>
      <value>Wallace</value>
      </attr>
      <attr name='givenName'>
      <value>Cecil</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>cwallace</value>
      </attr>
      <attr name='mail'>
      <value>cwallace@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6438</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>0349</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tpierce, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Tobias Pierce</value>
      </attr>
      <attr name='sn'>
      <value>Pierce</value>
      </attr>
      <attr name='givenName'>
      <value>Tobias</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>tpierce</value>
      </attr>
      <attr name='mail'>
      <value>tpierce@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1531</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>1383</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rbannist, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Richard Bannister</value>
      </attr>
      <attr name='sn'>
      <value>Bannister</value>
      </attr>
      <attr name='givenName'>
      <value>Richard</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>rbannist</value>
      </attr>
      <attr name='mail'>
      <value>rbannist@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1833</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>0983</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bplante, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Brian Plante</value>
      </attr>
      <attr name='sn'>
      <value>Plante</value>
      </attr>
      <attr name='givenName'>
      <value>Brian</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>bplante</value>
      </attr>
      <attr name='mail'>
      <value>bplante@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3550</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>4654</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rmills, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Randy Mills</value>
      </attr>
      <attr name='sn'>
      <value>Mills</value>
      </attr>
      <attr name='givenName'>
      <value>Randy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>rmills</value>
      </attr>
      <attr name='mail'>
      <value>rmills@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2072</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>3823</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bschneid, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Benjamin Schneider</value>
      </attr>
      <attr name='sn'>
      <value>Schneider</value>
      </attr>
      <attr name='givenName'>
      <value>Benjamin</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>bschneid</value>
      </attr>
      <attr name='mail'>
      <value>bschneid@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1012</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>4471</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=skellehe, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Sue Kelleher</value>
      </attr>
      <attr name='sn'>
      <value>Kelleher</value>
      </attr>
      <attr name='givenName'>
      <value>Sue</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>skellehe</value>
      </attr>
      <attr name='mail'>
      <value>skellehe@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3480</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>1608</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=brentz, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Bertram Rentz</value>
      </attr>
      <attr name='sn'>
      <value>Rentz</value>
      </attr>
      <attr name='givenName'>
      <value>Bertram</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>brentz</value>
      </attr>
      <attr name='mail'>
      <value>brentz@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5526</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>0617</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dsmith, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Daniel Smith</value>
      </attr>
      <attr name='sn'>
      <value>Smith</value>
      </attr>
      <attr name='givenName'>
      <value>Daniel</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>dsmith</value>
      </attr>
      <attr name='mail'>
      <value>dsmith@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9519</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>0368</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=scarte2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Stephen Carter</value>
      </attr>
      <attr name='sn'>
      <value>Carter</value>
      </attr>
      <attr name='givenName'>
      <value>Stephen</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>scarte2</value>
      </attr>
      <attr name='mail'>
      <value>scarte2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6022</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>2013</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dthorud, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>David Thorud</value>
      </attr>
      <attr name='sn'>
      <value>Thorud</value>
      </attr>
      <attr name='givenName'>
      <value>David</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>dthorud</value>
      </attr>
      <attr name='mail'>
      <value>dthorud@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6185</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>1128</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ekohler, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Elba Kohler</value>
      </attr>
      <attr name='sn'>
      <value>Kohler</value>
      </attr>
      <attr name='givenName'>
      <value>Elba</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>ekohler</value>
      </attr>
      <attr name='mail'>
      <value>ekohler@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1926</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>2721</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=lcampbel, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Laurel Campbell</value>
      </attr>
      <attr name='sn'>
      <value>Campbell</value>
      </attr>
      <attr name='givenName'>
      <value>Laurel</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>lcampbel</value>
      </attr>
      <attr name='mail'>
      <value>lcampbel@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2537</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>2073</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tlabonte, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Tim Labonte</value>
      </attr>
      <attr name='sn'>
      <value>Labonte</value>
      </attr>
      <attr name='givenName'>
      <value>Tim</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>tlabonte</value>
      </attr>
      <attr name='mail'>
      <value>tlabonte@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0058</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>1426</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=slee, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Scott Lee</value>
      </attr>
      <attr name='sn'>
      <value>Lee</value>
      </attr>
      <attr name='givenName'>
      <value>Scott</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>slee</value>
      </attr>
      <attr name='mail'>
      <value>slee@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2335</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>1806</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bfree, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Bjorn Free</value>
      </attr>
      <attr name='sn'>
      <value>Free</value>
      </attr>
      <attr name='givenName'>
      <value>Bjorn</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>bfree</value>
      </attr>
      <attr name='mail'>
      <value>bfree@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8588</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>3307</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tschneid, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Torrey Schneider</value>
      </attr>
      <attr name='sn'>
      <value>Schneider</value>
      </attr>
      <attr name='givenName'>
      <value>Torrey</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>tschneid</value>
      </attr>
      <attr name='mail'>
      <value>tschneid@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7086</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>2292</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=prose, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Paula Rose</value>
      </attr>
      <attr name='sn'>
      <value>Rose</value>
      </attr>
      <attr name='givenName'>
      <value>Paula</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>prose</value>
      </attr>
      <attr name='mail'>
      <value>prose@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9998</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>0542</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jhunter, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Janet Hunter</value>
      </attr>
      <attr name='sn'>
      <value>Hunter</value>
      </attr>
      <attr name='givenName'>
      <value>Janet</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jhunter</value>
      </attr>
      <attr name='mail'>
      <value>jhunter@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7665</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>4856</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ashelton, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Alexander Shelton</value>
      </attr>
      <attr name='sn'>
      <value>Shelton</value>
      </attr>
      <attr name='givenName'>
      <value>Alexander</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ashelton</value>
      </attr>
      <attr name='mail'>
      <value>ashelton@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1081</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>1987</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mmcinnis, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Marcus Mcinnis</value>
      </attr>
      <attr name='sn'>
      <value>Mcinnis</value>
      </attr>
      <attr name='givenName'>
      <value>Marcus</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>mmcinnis</value>
      </attr>
      <attr name='mail'>
      <value>mmcinnis@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9655</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>4818</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=falbers, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Frank Albers</value>
      </attr>
      <attr name='sn'>
      <value>Albers</value>
      </attr>
      <attr name='givenName'>
      <value>Frank</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>falbers</value>
      </attr>
      <attr name='mail'>
      <value>falbers@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3094</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>1439</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mschneid, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Martin Schneider</value>
      </attr>
      <attr name='sn'>
      <value>Schneider</value>
      </attr>
      <attr name='givenName'>
      <value>Martin</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>mschneid</value>
      </attr>
      <attr name='mail'>
      <value>mschneid@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5017</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>3153</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=pcruse, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Patricia Cruse</value>
      </attr>
      <attr name='sn'>
      <value>Cruse</value>
      </attr>
      <attr name='givenName'>
      <value>Patricia</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>pcruse</value>
      </attr>
      <attr name='mail'>
      <value>pcruse@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8641</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>3967</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tkelly, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Timothy Kelly</value>
      </attr>
      <attr name='sn'>
      <value>Kelly</value>
      </attr>
      <attr name='givenName'>
      <value>Timothy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>tkelly</value>
      </attr>
      <attr name='mail'>
      <value>tkelly@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4295</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>3107</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ahel, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Andrew Hel</value>
      </attr>
      <attr name='sn'>
      <value>Hel</value>
      </attr>
      <attr name='givenName'>
      <value>Andrew</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ahel</value>
      </attr>
      <attr name='mail'>
      <value>ahel@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2666</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>0572</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jburrell, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>James Burrell</value>
      </attr>
      <attr name='sn'>
      <value>Burrell</value>
      </attr>
      <attr name='givenName'>
      <value>James</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>jburrell</value>
      </attr>
      <attr name='mail'>
      <value>jburrell@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0751</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>4926</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=smason, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Sue Mason</value>
      </attr>
      <attr name='sn'>
      <value>Mason</value>
      </attr>
      <attr name='givenName'>
      <value>Sue</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>smason</value>
      </attr>
      <attr name='mail'>
      <value>smason@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9780</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>4971</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ptyler, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Pete Tyler</value>
      </attr>
      <attr name='sn'>
      <value>Tyler</value>
      </attr>
      <attr name='givenName'>
      <value>Pete</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ptyler</value>
      </attr>
      <attr name='mail'>
      <value>ptyler@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3335</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>0327</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=calexand, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Chris Alexander</value>
      </attr>
      <attr name='sn'>
      <value>Alexander</value>
      </attr>
      <attr name='givenName'>
      <value>Chris</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>calexand</value>
      </attr>
      <attr name='mail'>
      <value>calexand@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9438</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>2884</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jcruse, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jim Cruse</value>
      </attr>
      <attr name='sn'>
      <value>Cruse</value>
      </attr>
      <attr name='givenName'>
      <value>Jim</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jcruse</value>
      </attr>
      <attr name='mail'>
      <value>jcruse@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9482</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>0083</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=kcarter, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Karen Carter</value>
      </attr>
      <attr name='sn'>
      <value>Carter</value>
      </attr>
      <attr name='givenName'>
      <value>Karen</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>kcarter</value>
      </attr>
      <attr name='mail'>
      <value>kcarter@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4675</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>2320</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rfish, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Randy Fish</value>
      </attr>
      <attr name='sn'>
      <value>Fish</value>
      </attr>
      <attr name='givenName'>
      <value>Randy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>rfish</value>
      </attr>
      <attr name='mail'>
      <value>rfish@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9865</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>2317</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=phunt, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Philip Hunt</value>
      </attr>
      <attr name='sn'>
      <value>Hunt</value>
      </attr>
      <attr name='givenName'>
      <value>Philip</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>phunt</value>
      </attr>
      <attr name='mail'>
      <value>phunt@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1242</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>1183</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rschneid, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Rachel Schneider</value>
      </attr>
      <attr name='sn'>
      <value>Schneider</value>
      </attr>
      <attr name='givenName'>
      <value>Rachel</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>rschneid</value>
      </attr>
      <attr name='mail'>
      <value>rschneid@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9908</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>4183</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bjensen, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Barbara Jensen</value>
      <value>Babs Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Barbara</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>bjensen</value>
      </attr>
      <attr name='mail'>
      <value>bjensen@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1862</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>0209</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jlange, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jim Lange</value>
      </attr>
      <attr name='sn'>
      <value>Lange</value>
      </attr>
      <attr name='givenName'>
      <value>Jim</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jlange</value>
      </attr>
      <attr name='mail'>
      <value>jlange@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0488</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>3798</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rulrich, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Randy Ulrich</value>
      </attr>
      <attr name='sn'>
      <value>Ulrich</value>
      </attr>
      <attr name='givenName'>
      <value>Randy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>rulrich</value>
      </attr>
      <attr name='mail'>
      <value>rulrich@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5311</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>1282</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rfrancis, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Richard Francis</value>
      </attr>
      <attr name='sn'>
      <value>Francis</value>
      </attr>
      <attr name='givenName'>
      <value>Richard</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>rfrancis</value>
      </attr>
      <attr name='mail'>
      <value>rfrancis@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8157</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>3482</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mwhite, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Morgan White</value>
      </attr>
      <attr name='sn'>
      <value>White</value>
      </attr>
      <attr name='givenName'>
      <value>Morgan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>mwhite</value>
      </attr>
      <attr name='mail'>
      <value>mwhite@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9620</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>3088</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=gjensen, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Gern Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Gern</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>gjensen</value>
      </attr>
      <attr name='mail'>
      <value>gjensen@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3299</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>4609</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=awhite, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Alan White</value>
      </attr>
      <attr name='sn'>
      <value>White</value>
      </attr>
      <attr name='givenName'>
      <value>Alan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>awhite</value>
      </attr>
      <attr name='mail'>
      <value>awhite@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3232</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>0142</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bmaddox, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Barbara Maddox</value>
      </attr>
      <attr name='sn'>
      <value>Maddox</value>
      </attr>
      <attr name='givenName'>
      <value>Barbara</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>bmaddox</value>
      </attr>
      <attr name='mail'>
      <value>bmaddox@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7783</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>2207</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mtalbot, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Martin Talbot</value>
      </attr>
      <attr name='sn'>
      <value>Talbot</value>
      </attr>
      <attr name='givenName'>
      <value>Martin</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>mtalbot</value>
      </attr>
      <attr name='mail'>
      <value>mtalbot@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9228</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>1415</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jbrown, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Judy Brown</value>
      </attr>
      <attr name='sn'>
      <value>Brown</value>
      </attr>
      <attr name='givenName'>
      <value>Judy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jbrown</value>
      </attr>
      <attr name='mail'>
      <value>jbrown@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6885</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>4224</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jjensen, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jody Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Jody</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>jjensen</value>
      </attr>
      <attr name='mail'>
      <value>jjensen@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7587</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>4882</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mcarter, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Mike Carter</value>
      </attr>
      <attr name='sn'>
      <value>Carter</value>
      </attr>
      <attr name='givenName'>
      <value>Mike</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>mcarter</value>
      </attr>
      <attr name='mail'>
      <value>mcarter@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1846</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>3819</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dakers, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>David Akers</value>
      </attr>
      <attr name='sn'>
      <value>Akers</value>
      </attr>
      <attr name='givenName'>
      <value>David</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>dakers</value>
      </attr>
      <attr name='mail'>
      <value>dakers@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4812</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>4944</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=sfarmer, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Scott Farmer</value>
      </attr>
      <attr name='sn'>
      <value>Farmer</value>
      </attr>
      <attr name='givenName'>
      <value>Scott</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>sfarmer</value>
      </attr>
      <attr name='mail'>
      <value>sfarmer@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4228</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>0019</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dward, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Daniel Ward</value>
      </attr>
      <attr name='sn'>
      <value>Ward</value>
      </attr>
      <attr name='givenName'>
      <value>Daniel</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>dward</value>
      </attr>
      <attr name='mail'>
      <value>dward@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5322</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>3927</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tward, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Tobias Ward</value>
      </attr>
      <attr name='sn'>
      <value>Ward</value>
      </attr>
      <attr name='givenName'>
      <value>Tobias</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>tward</value>
      </attr>
      <attr name='mail'>
      <value>tward@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7202</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>2238</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=pshelton, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Patricia Shelton</value>
      </attr>
      <attr name='sn'>
      <value>Shelton</value>
      </attr>
      <attr name='givenName'>
      <value>Patricia</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>pshelton</value>
      </attr>
      <attr name='mail'>
      <value>pshelton@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6442</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>2918</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jrentz, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jody Rentz</value>
      </attr>
      <attr name='sn'>
      <value>Rentz</value>
      </attr>
      <attr name='givenName'>
      <value>Jody</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jrentz</value>
      </attr>
      <attr name='mail'>
      <value>jrentz@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5829</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>3025</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=plorig, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Peter Lorig</value>
      </attr>
      <attr name='sn'>
      <value>Lorig</value>
      </attr>
      <attr name='givenName'>
      <value>Peter</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>plorig</value>
      </attr>
      <attr name='mail'>
      <value>plorig@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0624</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>1276</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ajensen, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Allison Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Allison</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ajensen</value>
      </attr>
      <attr name='mail'>
      <value>ajensen@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7892</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>0784</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=kschmith, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Kelly Schmith</value>
      </attr>
      <attr name='sn'>
      <value>Schmith</value>
      </attr>
      <attr name='givenName'>
      <value>Kelly</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>kschmith</value>
      </attr>
      <attr name='mail'>
      <value>kschmith@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9749</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>2221</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=pworrell, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Pete Worrell</value>
      </attr>
      <attr name='sn'>
      <value>Worrell</value>
      </attr>
      <attr name='givenName'>
      <value>Pete</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>pworrell</value>
      </attr>
      <attr name='mail'>
      <value>pworrell@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1637</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>2449</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mreuter, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Matthew Reuter</value>
      </attr>
      <attr name='sn'>
      <value>Reuter</value>
      </attr>
      <attr name='givenName'>
      <value>Matthew</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>mreuter</value>
      </attr>
      <attr name='mail'>
      <value>mreuter@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6879</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>1356</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=gtyler, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Gern Tyler</value>
      </attr>
      <attr name='sn'>
      <value>Tyler</value>
      </attr>
      <attr name='givenName'>
      <value>Gern</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>gtyler</value>
      </attr>
      <attr name='mail'>
      <value>gtyler@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1020</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>0312</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tschmith, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Tobias Schmith</value>
      </attr>
      <attr name='sn'>
      <value>Schmith</value>
      </attr>
      <attr name='givenName'>
      <value>Tobias</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>tschmith</value>
      </attr>
      <attr name='mail'>
      <value>tschmith@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9626</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>4607</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bjense2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Bjorn Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Bjorn</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>bjense2</value>
      </attr>
      <attr name='mail'>
      <value>bjense2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5655</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>4294</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dswain, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Dietrich Swain</value>
      </attr>
      <attr name='sn'>
      <value>Swain</value>
      </attr>
      <attr name='givenName'>
      <value>Dietrich</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>dswain</value>
      </attr>
      <attr name='mail'>
      <value>dswain@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9222</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>4396</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ahall, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Andy Hall</value>
      </attr>
      <attr name='sn'>
      <value>Hall</value>
      </attr>
      <attr name='givenName'>
      <value>Andy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ahall</value>
      </attr>
      <attr name='mail'>
      <value>ahall@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6169</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>3050</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jmuffly, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jeff Muffly</value>
      </attr>
      <attr name='sn'>
      <value>Muffly</value>
      </attr>
      <attr name='givenName'>
      <value>Jeff</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>jmuffly</value>
      </attr>
      <attr name='mail'>
      <value>jmuffly@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5287</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>0997</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tjensen, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Ted Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Ted</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>tjensen</value>
      </attr>
      <attr name='mail'>
      <value>tjensen@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8622</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>4717</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ahunter, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Allison Hunter</value>
      </attr>
      <attr name='sn'>
      <value>Hunter</value>
      </attr>
      <attr name='givenName'>
      <value>Allison</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>ahunter</value>
      </attr>
      <attr name='mail'>
      <value>ahunter@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7713</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>1213</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jgoldste, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jon Goldstein</value>
      </attr>
      <attr name='sn'>
      <value>Goldstein</value>
      </attr>
      <attr name='givenName'>
      <value>Jon</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jgoldste</value>
      </attr>
      <attr name='mail'>
      <value>jgoldste@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5769</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>1454</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=aworrell, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Alan Worrell</value>
      </attr>
      <attr name='sn'>
      <value>Worrell</value>
      </attr>
      <attr name='givenName'>
      <value>Alan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>aworrell</value>
      </attr>
      <attr name='mail'>
      <value>aworrell@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1591</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>3966</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=wlutz, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Wendy Lutz</value>
      </attr>
      <attr name='sn'>
      <value>Lutz</value>
      </attr>
      <attr name='givenName'>
      <value>Wendy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>wlutz</value>
      </attr>
      <attr name='mail'>
      <value>wlutz@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3358</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>4912</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jlutz, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Janet Lutz</value>
      </attr>
      <attr name='sn'>
      <value>Lutz</value>
      </attr>
      <attr name='givenName'>
      <value>Janet</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>jlutz</value>
      </attr>
      <attr name='mail'>
      <value>jlutz@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4902</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>2544</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dlangdon, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Dan Langdon</value>
      </attr>
      <attr name='sn'>
      <value>Langdon</value>
      </attr>
      <attr name='givenName'>
      <value>Dan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>dlangdon</value>
      </attr>
      <attr name='mail'>
      <value>dlangdon@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7044</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>3263</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=aknutson, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Ashley Knutson</value>
      </attr>
      <attr name='sn'>
      <value>Knutson</value>
      </attr>
      <attr name='givenName'>
      <value>Ashley</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>aknutson</value>
      </attr>
      <attr name='mail'>
      <value>aknutson@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2169</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>4736</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=kmcinnis, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Kelly Mcinnis</value>
      </attr>
      <attr name='sn'>
      <value>Mcinnis</value>
      </attr>
      <attr name='givenName'>
      <value>Kelly</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>kmcinnis</value>
      </attr>
      <attr name='mail'>
      <value>kmcinnis@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8596</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>4312</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tcouzens, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Trent Couzens</value>
      </attr>
      <attr name='sn'>
      <value>Couzens</value>
      </attr>
      <attr name='givenName'>
      <value>Trent</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>tcouzens</value>
      </attr>
      <attr name='mail'>
      <value>tcouzens@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8401</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>3994</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=lstockto, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Lee Stockton</value>
      </attr>
      <attr name='sn'>
      <value>Stockton</value>
      </attr>
      <attr name='givenName'>
      <value>Lee</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>lstockto</value>
      </attr>
      <attr name='mail'>
      <value>lstockto@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0518</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>0169</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jbourke, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jon Bourke</value>
      </attr>
      <attr name='sn'>
      <value>Bourke</value>
      </attr>
      <attr name='givenName'>
      <value>Jon</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>jbourke</value>
      </attr>
      <attr name='mail'>
      <value>jbourke@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8541</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>0034</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dlanoway, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Dan Lanoway</value>
      </attr>
      <attr name='sn'>
      <value>Lanoway</value>
      </attr>
      <attr name='givenName'>
      <value>Dan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>dlanoway</value>
      </attr>
      <attr name='mail'>
      <value>dlanoway@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2017</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>3540</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=kcope, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Karl Cope</value>
      </attr>
      <attr name='sn'>
      <value>Cope</value>
      </attr>
      <attr name='givenName'>
      <value>Karl</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>kcope</value>
      </attr>
      <attr name='mail'>
      <value>kcope@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2709</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>3040</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=abarnes, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Anne-Louise Barnes</value>
      </attr>
      <attr name='sn'>
      <value>Barnes</value>
      </attr>
      <attr name='givenName'>
      <value>Anne-Louise</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>abarnes</value>
      </attr>
      <attr name='mail'>
      <value>abarnes@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9445</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>2290</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rjensen, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Richard Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Richard</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>rjensen</value>
      </attr>
      <attr name='mail'>
      <value>rjensen@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5957</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>2631</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=phun2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Pete Hunt</value>
      </attr>
      <attr name='sn'>
      <value>Hunt</value>
      </attr>
      <attr name='givenName'>
      <value>Pete</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>phun2</value>
      </attr>
      <attr name='mail'>
      <value>phun2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0342</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>0087</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mvaughan, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Matthew Vaughan</value>
      </attr>
      <attr name='sn'>
      <value>Vaughan</value>
      </attr>
      <attr name='givenName'>
      <value>Matthew</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>mvaughan</value>
      </attr>
      <attr name='mail'>
      <value>mvaughan@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4692</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>4508</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jlut2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>James Lutz</value>
      </attr>
      <attr name='sn'>
      <value>Lutz</value>
      </attr>
      <attr name='givenName'>
      <value>James</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jlut2</value>
      </attr>
      <attr name='mail'>
      <value>jlut2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9689</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>3541</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mjablons, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Morgan Jablonski</value>
      </attr>
      <attr name='sn'>
      <value>Jablonski</value>
      </attr>
      <attr name='givenName'>
      <value>Morgan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>mjablons</value>
      </attr>
      <attr name='mail'>
      <value>mjablons@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0813</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>3160</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=pchassin, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Peter Chassin</value>
      </attr>
      <attr name='sn'>
      <value>Chassin</value>
      </attr>
      <attr name='givenName'>
      <value>Peter</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>pchassin</value>
      </attr>
      <attr name='mail'>
      <value>pchassin@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2816</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>4524</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=dcope, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Dan Cope</value>
      </attr>
      <attr name='sn'>
      <value>Cope</value>
      </attr>
      <attr name='givenName'>
      <value>Dan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>dcope</value>
      </attr>
      <attr name='mail'>
      <value>dcope@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9813</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>1737</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jrent2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Judy Rentz</value>
      </attr>
      <attr name='sn'>
      <value>Rentz</value>
      </attr>
      <attr name='givenName'>
      <value>Judy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jrent2</value>
      </attr>
      <attr name='mail'>
      <value>jrent2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2523</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>4405</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tcruse, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Tobias Cruse</value>
      </attr>
      <attr name='sn'>
      <value>Cruse</value>
      </attr>
      <attr name='givenName'>
      <value>Tobias</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>tcruse</value>
      </attr>
      <attr name='mail'>
      <value>tcruse@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5980</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4774</value>
      </attr>
      <attr name='roomNumber'>
      <value>4191</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=eward, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Eric Ward</value>
      </attr>
      <attr name='sn'>
      <value>Ward</value>
      </attr>
      <attr name='givenName'>
      <value>Eric</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>eward</value>
      </attr>
      <attr name='mail'>
      <value>eward@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2320</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 7472</value>
      </attr>
      <attr name='roomNumber'>
      <value>4874</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ttully, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Torrey Tully</value>
      </attr>
      <attr name='sn'>
      <value>Tully</value>
      </attr>
      <attr name='givenName'>
      <value>Torrey</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>ttully</value>
      </attr>
      <attr name='mail'>
      <value>ttully@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2274</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>3924</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=charvey, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Cecil Harvey</value>
      </attr>
      <attr name='sn'>
      <value>Harvey</value>
      </attr>
      <attr name='givenName'>
      <value>Cecil</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>charvey</value>
      </attr>
      <attr name='mail'>
      <value>charvey@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1815</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3825</value>
      </attr>
      <attr name='roomNumber'>
      <value>4583</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rfisher, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Randy Fisher</value>
      </attr>
      <attr name='sn'>
      <value>Fisher</value>
      </attr>
      <attr name='givenName'>
      <value>Randy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>rfisher</value>
      </attr>
      <attr name='mail'>
      <value>rfisher@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 1506</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>1579</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=alangdon, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Andrew Langdon</value>
      </attr>
      <attr name='sn'>
      <value>Langdon</value>
      </attr>
      <attr name='givenName'>
      <value>Andrew</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>alangdon</value>
      </attr>
      <attr name='mail'>
      <value>alangdon@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8289</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>2254</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=drose, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>David Rose</value>
      </attr>
      <attr name='sn'>
      <value>Rose</value>
      </attr>
      <attr name='givenName'>
      <value>David</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>drose</value>
      </attr>
      <attr name='mail'>
      <value>drose@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 3963</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>4012</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=polfield, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Peter Olfield</value>
      </attr>
      <attr name='sn'>
      <value>Olfield</value>
      </attr>
      <attr name='givenName'>
      <value>Peter</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>polfield</value>
      </attr>
      <attr name='mail'>
      <value>polfield@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 8231</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>1376</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=awalker, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Andy Walker</value>
      </attr>
      <attr name='sn'>
      <value>Walker</value>
      </attr>
      <attr name='givenName'>
      <value>Andy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>awalker</value>
      </attr>
      <attr name='mail'>
      <value>awalker@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9199</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 3372</value>
      </attr>
      <attr name='roomNumber'>
      <value>0061</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=lrentz, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Lex Rentz</value>
      </attr>
      <attr name='sn'>
      <value>Rentz</value>
      </attr>
      <attr name='givenName'>
      <value>Lex</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>lrentz</value>
      </attr>
      <attr name='mail'>
      <value>lrentz@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2019</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>2203</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jvaughan, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jeff Vaughan</value>
      </attr>
      <attr name='sn'>
      <value>Vaughan</value>
      </attr>
      <attr name='givenName'>
      <value>Jeff</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>jvaughan</value>
      </attr>
      <attr name='mail'>
      <value>jvaughan@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4543</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>1734</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bfrancis, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Barbara Francis</value>
      </attr>
      <attr name='sn'>
      <value>Francis</value>
      </attr>
      <attr name='givenName'>
      <value>Barbara</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>bfrancis</value>
      </attr>
      <attr name='mail'>
      <value>bfrancis@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9111</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>3743</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ewalker, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Eric Walker</value>
      </attr>
      <attr name='sn'>
      <value>Walker</value>
      </attr>
      <attr name='givenName'>
      <value>Eric</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Payroll</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>ewalker</value>
      </attr>
      <attr name='mail'>
      <value>ewalker@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 6387</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8721</value>
      </attr>
      <attr name='roomNumber'>
      <value>2295</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=tjames, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Tobias James</value>
      </attr>
      <attr name='sn'>
      <value>James</value>
      </attr>
      <attr name='givenName'>
      <value>Tobias</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>tjames</value>
      </attr>
      <attr name='mail'>
      <value>tjames@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 2458</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>0730</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=brigden, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Bjorn Rigden</value>
      </attr>
      <attr name='sn'>
      <value>Rigden</value>
      </attr>
      <attr name='givenName'>
      <value>Bjorn</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>brigden</value>
      </attr>
      <attr name='mail'>
      <value>brigden@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5263</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>1643</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ecruse, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Eric Cruse</value>
      </attr>
      <attr name='sn'>
      <value>Cruse</value>
      </attr>
      <attr name='givenName'>
      <value>Eric</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ecruse</value>
      </attr>
      <attr name='mail'>
      <value>ecruse@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0648</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>4233</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rjense2, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Randy Jensen</value>
      </attr>
      <attr name='sn'>
      <value>Jensen</value>
      </attr>
      <attr name='givenName'>
      <value>Randy</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>rjense2</value>
      </attr>
      <attr name='mail'>
      <value>rjense2@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 9045</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 1992</value>
      </attr>
      <attr name='roomNumber'>
      <value>1984</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=rhunt, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Richard Hunt</value>
      </attr>
      <attr name='sn'>
      <value>Hunt</value>
      </attr>
      <attr name='givenName'>
      <value>Richard</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Accounting</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>rhunt</value>
      </attr>
      <attr name='mail'>
      <value>rhunt@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0139</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 8473</value>
      </attr>
      <attr name='roomNumber'>
      <value>0718</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=bparker, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Barry Parker</value>
      </attr>
      <attr name='sn'>
      <value>Parker</value>
      </attr>
      <attr name='givenName'>
      <value>Barry</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>bparker</value>
      </attr>
      <attr name='mail'>
      <value>bparker@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4647</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>1148</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=ealexand, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Erin Alexander</value>
      </attr>
      <attr name='sn'>
      <value>Alexander</value>
      </attr>
      <attr name='givenName'>
      <value>Erin</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>ealexand</value>
      </attr>
      <attr name='mail'>
      <value>ealexand@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 5563</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>2434</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=mtyler, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Matthew Tyler</value>
      </attr>
      <attr name='sn'>
      <value>Tyler</value>
      </attr>
      <attr name='givenName'>
      <value>Matthew</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Human Resources</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Cupertino</value>
      </attr>
      <attr name='uid'>
      <value>mtyler</value>
      </attr>
      <attr name='mail'>
      <value>mtyler@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 7907</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 4661</value>
      </attr>
      <attr name='roomNumber'>
      <value>2701</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=elott, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Emanuel Lott</value>
      </attr>
      <attr name='sn'>
      <value>Lott</value>
      </attr>
      <attr name='givenName'>
      <value>Emanuel</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Testing</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>elott</value>
      </attr>
      <attr name='mail'>
      <value>elott@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0932</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9751</value>
      </attr>
      <attr name='roomNumber'>
      <value>3906</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=cnewport, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Christoph Newport</value>
      </attr>
      <attr name='sn'>
      <value>Newport</value>
      </attr>
      <attr name='givenName'>
      <value>Christoph</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Sunnyvale</value>
      </attr>
      <attr name='uid'>
      <value>cnewport</value>
      </attr>
      <attr name='mail'>
      <value>cnewport@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 0066</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 9332</value>
      </attr>
      <attr name='roomNumber'>
      <value>0056</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='uid=jvedder, ou=People, dc=example,dc=com'>
      <attr name='cn'>
      <value>Jeff Vedder</value>
      </attr>
      <attr name='sn'>
      <value>Vedder</value>
      </attr>
      <attr name='givenName'>
      <value>Jeff</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalPerson</value>
      <value>inetOrgPerson</value>
      </attr>
      <attr name='ou'>
      <value>Product Development</value>
      <value>People</value>
      </attr>
      <attr name='l'>
      <value>Santa Clara</value>
      </attr>
      <attr name='uid'>
      <value>jvedder</value>
      </attr>
      <attr name='mail'>
      <value>jvedder@example.com</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 555 4668</value>
      </attr>
      <attr name='facsimileTelephoneNumber'>
      <value>+1 408 555 0111</value>
      </attr>
      <attr name='roomNumber'>
      <value>3445</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=Accounting Managers,ou=groups,dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>groupOfUniqueNames</value>
      </attr>
      <attr name='cn'>
      <value>Accounting Managers</value>
      </attr>
      <attr name='ou'>
      <value>groups</value>
      </attr>
      <attr name='uniqueMember'>
      <value>uid=scarter, ou=People, dc=example,dc=com</value>
      <value>uid=tmorris, ou=People, dc=example,dc=com</value>
      </attr>
      <attr name='description'>
      <value>People who can manage accounting entries</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=HR Managers,ou=groups,dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>groupOfUniqueNames</value>
      </attr>
      <attr name='cn'>
      <value>HR Managers</value>
      </attr>
      <attr name='ou'>
      <value>groups</value>
      </attr>
      <attr name='uniqueMember'>
      <value>uid=kvaughan, ou=People, dc=example,dc=com</value>
      <value>uid=cschmith, ou=People, dc=example,dc=com</value>
      </attr>
      <attr name='description'>
      <value>People who can manage HR entries</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=QA Managers,ou=groups,dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>groupOfUniqueNames</value>
      </attr>
      <attr name='cn'>
      <value>QA Managers</value>
      </attr>
      <attr name='ou'>
      <value>groups</value>
      </attr>
      <attr name='uniqueMember'>
      <value>uid=abergin, ou=People, dc=example,dc=com</value>
      <value>uid=jwalker, ou=People, dc=example,dc=com</value>
      </attr>
      <attr name='description'>
      <value>People who can manage QA entries</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=PD Managers,ou=groups,dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>groupOfUniqueNames</value>
      </attr>
      <attr name='cn'>
      <value>PD Managers</value>
      </attr>
      <attr name='ou'>
      <value>groups</value>
      </attr>
      <attr name='uniqueMember'>
      <value>uid=kwinters, ou=People, dc=example,dc=com</value>
      <value>uid=trigden, ou=People, dc=example,dc=com</value>
      </attr>
      <attr name='description'>
      <value>People who can manage engineer entries</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='ou=Netscape Servers,dc=example,dc=com'>
      <attr name='objectClass'>
      <value>top</value>
      <value>organizationalUnit</value>
      </attr>
      <attr name='ou'>
      <value>Netscape Servers</value>
      </attr>
      <attr name='description'>
      <value>Standard branch for Netscape Server registration</value>
      </attr>
   </searchResultEntry>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
</batchResponse>
</soap-env:Body>
</soap-env:Envelope>
