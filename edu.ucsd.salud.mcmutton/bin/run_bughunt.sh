source $(dirname $0)/_mcmutton.sh

pushd $mcmutton_base > /dev/null
java -Xmx4G -cp $mcmutton_base/target/classes:$(cat $mcmutton_base/.classpath-run) edu.ucsd.salud.mcmutton.BugHunt $*
popd > /dev/null

#cd ..
#mvn install
#cd -
#mvn compile dependency:build-classpath -Dmdep.outputFile=.classpath-run 1>&2
#java -Xmx4G -cp target/classes:$(cat .classpath-run) edu.ucsd.salud.mcmutton.BugHunt $*
