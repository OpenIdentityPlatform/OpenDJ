container=${1:-"ldap-01"}
dataVolume=${2:-"./data"}
containerDataDirs=( bak  changelogDb  classes  config  db  import-tmp  ldif  lib  locks  logs )
if [ ! -d "$dataVolume" ]; then
  echo "creating data volume $dataVolume"
  mkdir $dataVolume
fi
for containerDataDir in "${containerDataDirs[@]}"
do
  echo "copy form volume $containerDataDir"
  docker cp $container:/opt/opendj/$containerDataDir $dataVolume$containerDataDir
done
