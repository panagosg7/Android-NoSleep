#! /bin/bash

source $(dirname $0)/_mcmutton.sh

pushd $mcmutton_base > /dev/null
java -Xmx8G -cp $mcmutton_base/target/classes:$(cat $mcmutton_base/.classpath-run) edu.ucsd.salud.mcmutton.BugHunt $*
popd > /dev/null
