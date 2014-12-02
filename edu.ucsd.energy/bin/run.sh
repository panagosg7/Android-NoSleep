#mvn compile
#mvn compile dependency:build-classpath -Dmdep.outputFile=.classpath-run
java -Xmx4G -cp target/classes:$(cat .classpath-run) edu.ucsd.energy.Main "$@"
