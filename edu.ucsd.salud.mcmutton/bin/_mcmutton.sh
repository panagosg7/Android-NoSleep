

bin_base=$(realpath $(dirname $0))
mcmutton_base=$(realpath "$bin_base/..")
wala_base=$(realpath "$mcmutton_base/..")

pushd ${MCMUTTON_PATH:="$wala_base"} > /dev/null
mvn compile dependency:build-classpath -Dmdep.outputFile=$mcmutton_base/.classpath-run 1>&2
if [ $? -ne 0 ]; then exit $?; fi
popd > /dev/null
