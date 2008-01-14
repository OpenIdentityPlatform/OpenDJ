home=`cd \`dirname $0\`;pwd`
port=389
rootdn="cn=directory manager"
global_vars="-p ${port} -w password -n"
opends_home="${home}/install/product"
dsconfig="${opends_home}/bin/dsconfig"

#echo
#echo "OpenDS set up ..."
#${opends_home}/setup -i -a -b o=telco.com ${global_vars} -D "${rootdn}"

#echo
#echo
#echo "Removing irrelevant indexes ..."
#bad_index_list="cn ds-sync-hist givenName mail member sn telephoneNumber uniqueMember"
#for index in ${bad_index_list}; do
#  set -x
#  ${dsconfig} delete-local-db-index -D "${rootdn}" ${global_vars} --backend-name userRoot --index-name ${index} 
#  set +x
#done
#echo
#echo
#echo "Creating relevant indexes ..."
#equality_index_list="msid esn"
#for index in ${equality_index_list}; do
#  ${dsconfig} create-local-db-index -D "${rootdn}" ${global_vars} --backend-name userRoot --index-name ${index} --set index-type:equality
#done
#exit
echo
echo
#echo "Removing virtual attributes ..."
#set -x
#for virtual_attribute in entryDN isMemberOf subschemaSubentry "VirtualStatic member" "Virtual Static member" "Virtual Static uniqueMember"; do
##  ${dsconfig} delete-virtual-attribute -D "${rootdn}" ${global_vars} --attribute-name "${virtual_attribute}"
#done
#set +x
#exit

#echo
#echo
#echo "Removing unnecessary plug-ins ..."
#for plugin in "7-bit clean" lastmod "LDAP Attribute Description List" "Password Policy Import" Profiler "referential integrity" "uid unique attribute"; do
#  ${dsconfig} delete-plugin -D "${rootdn}" ${global_vars} --plugin-name "${plugin}"
#done
#exit

set -x
echo
echo
echo "Tweaking Database ..."
${dsconfig} set-backend-prop -D "${rootdn}" ${global_vars} --backend-name userRoot --set db-cache-percent:20
${dsconfig} set-backend-prop -D "${rootdn}" ${global_vars} --backend-name userRoot --set db-evictor-lru-only:false
exit
echo
echo
echo "Various Tunings ..."
${dsconfig} set-connection-handler-prop -D "${rootdn}" ${global_vars} --handler-name "LDAP Connection Handler" --set num-request-handlers:4
${dsconfig} set-work-queue-prop ${global_vars} --set num-worker-threads:16
