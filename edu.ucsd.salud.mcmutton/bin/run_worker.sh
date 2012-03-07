source `dirname $0`/_update.sh
java -Djava.net.preferIPv4Stack=true -Xmx2G -cp target/classes:$(cat .classpath-run) edu.ucsd.salud.mcmutton.WorkConsumer
