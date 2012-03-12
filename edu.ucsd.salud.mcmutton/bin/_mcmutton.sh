bin_base=$(readlink -f $(dirname $0))
mcmutton_base=$(readlink -f "$bin_base/..")
wala_base=$(readlink -f "$mcmutton_base/..")
mcmutton_classpath="$mcmutton_base/target/classes:$(cat $mcmutton_base/.classpath-run)"

pushd ${MCMUTTON_PATH:="$wala_base"} > /dev/null
mvn compile dependency:build-classpath -Dmdep.outputFile=$mcmutton_base/.classpath-run 1>&2
if [ $? -ne 0 ]; then exit $?; fi
popd > /dev/null
