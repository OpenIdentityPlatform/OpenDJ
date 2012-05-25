#!/bin/bash

# Smoke test RewriterProxy.java using OpenDJ tools.
# Depends on http://opendj.forgerock.org/Example.ldif being in OpenDJ.

OPENDJ_TOOLS_DIR="/path/to/OpenDJ/bin"  # ldapcompare, ldapdelete, ldapmodify, ldapsearch
HOST=localhost                          # Host where proxy listens
PORT=8389                               # Port where proxy listens

BINDDN="uid=kvaughan,ou=People,dc=example,dc=com"
BINDPWD=bribery

CURRDIR=`pwd`

if [ -e $OPENDJ_TOOLS_DIR ]
then
	cd $OPENDJ_TOOLS_DIR
else
	exit 1
fi

#set -x

echo Deleting uid=fdupont,ou=People,o=example...
./ldapdelete -h $HOST -p $PORT -D $BINDDN -w $BINDPWD uid=fdupont,ou=People,o=example
echo

add() {
  echo Adding uid=fdupont,ou=People,o=example...
  ./ldapmodify -h $HOST -p $PORT -D $BINDDN -w $BINDPWD -a <<EOF

dn: uid=fdupont,ou=People,o=example
uid: fdupont
fullname: Frederique Dupont
fullname;lang-fr: Fredérique Dupont
givenName: Fredérique
sn: Dupont
objectClass: inetOrgPerson
objectClass: organizationalPerson
objectClass: person
objectClass: posixAccount
objectClass: top
ou: People
ou: Product Development
telephoneNumber: +33 1 23 45 67 89
facsimileTelephoneNumber: +33 1 23 45 67 88
mail: fdupont@example.fr
roomNumber: 0042
l: Paris
gidNumber: 1000
uidNumber: 1110
homeDirectory: /home/fdupont
userPassword: password

EOF
  echo
}

add

echo Looking for fullname=Frederique Dupont...
./ldapsearch -h $HOST -p $PORT -D $BINDDN -w $BINDPWD -b o=example "(fullname=Frederique Dupont)" fullname
echo

echo Comparing fullname:Frederique Dupont...
./ldapcompare -h $HOST -p $PORT -D $BINDDN -w $BINDPWD "fullname:Frederique Dupont" uid=fdupont,ou=People,o=example
echo

echo Changing fullname...
./ldapmodify -h $HOST -p $PORT -D $BINDDN -w $BINDPWD <<EOM

dn: uid=fdupont,ou=People,o=example
changetype: modify
replace: fullname
fullname: Fred Dupont

EOM
echo

echo Changing uid=fdupont to uid=qdupont...
./ldapmodify -h $HOST -p $PORT -D $BINDDN -w $BINDPWD <<EOR

dn: uid=fdupont,ou=People,o=example
changetype: modrdn
newrdn: uid=qdupont
deleteoldrdn: 1

EOR
echo

echo Deleting uid=qdupont,ou=People,o=example
./ldapdelete -h $HOST -p $PORT -D $BINDDN -w $BINDPWD uid=qdupont,ou=People,o=example
echo

add

cd $CURRDIR

echo Done.
exit 0
