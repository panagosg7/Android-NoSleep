#! /bin/bash

source $(dirname $0)/_mcmutton.sh

pushd $mcmutton_base > /dev/null
#java -agentlib:hprof=heap=dump,format=b -Xmx2G -cp $mcmutton_base/target/classes:$(cat $mcmutton_base/.classpath-run) edu.ucsd.energy.Main $*
java -Xmx16G -cp $mcmutton_base/target/classes:$(cat $mcmutton_base/.classpath-run) edu.ucsd.energy.Main $*
popd > /dev/null
