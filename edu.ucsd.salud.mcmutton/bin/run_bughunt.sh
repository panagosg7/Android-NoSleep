mvn compile dependency:build-classpath -Dmdep.outputFile=.classpath-run 1>&2
java -Xmx4G -cp target/classes:$(cat .classpath-run) edu.ucsd.salud.mcmutton.BugHunt $*
