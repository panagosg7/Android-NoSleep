source $(dirname $0)/_mcmutton.sh

pushd $mcmutton_base > /dev/null
java -Xmx4G -cp $mcmutton_classpath edu.ucsd.energy.entry.WorkMonger
popd > /dev/null
