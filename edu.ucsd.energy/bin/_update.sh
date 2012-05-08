hg pull -u
mvn compile
mvn compile dependency:build-classpath -Dmdep.outputFile=.classpath-run
