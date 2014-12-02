# No Sleep Energy Bug Finder

This is the accompanying implementation for the tool described
[here](http://goto.ucsd.edu/~pvekris/docs/hotpower12.pdf)

This tool is based on [WALA](http://wala.sourceforge.net/wiki/index.php/Main_Page), now also maintained in [GitHub](https://github.com/wala/WALA).

# How to use

## Dependencies

This projects depends on:

 * Java 
 * [Maven](http://maven.apache.org/) (recommended)

## Retarget Dalvik into Java Bytecode

The input to our tool needs to be in Java Bytecode (JAR form). 

To retarget a target APK into a Java JAR I suggest using
[Dare: Dalvik Retargetting](http://siis.cse.psu.edu/dare/)

I have only tried the version for Linux.

After installing Dare, using the instructions
[here](http://siis.cse.psu.edu/dare/installation.html), do the following:

```
$ dare -o -d <dare-output> <apk-file>
```

This will create a directory structure withing `<dare-output>` that looks like
the following: 

```
├── optimized
├── optimized-decompiled
├── retargeted
└── stats.csv
```

To create the JAR for the application: 

```
$ cd <dare-output>/optimized
$ cd <app-name>
$ jar cf target-name.jar *
```

This JAR will be used as input to our tool.


## Configure

Make sure the WALA properties in file `edu.ucsd.energy/wala.properties` are valid. 

In particular, `java_runtime_dir` needs to point to a valid Java runtime installation.


## Build

First import the whole project into Eclipse using the Maven plugin (preinstalled in latest version), by clicking on: 
`File -> Import...`, then select `Existing Maven Projects` and navigate to the top-level of the current project.
Once the workspace is built, in a terminal, navigate to the same project folder and issue the following:

```
$ cd edu.ucsd.energy
$ bin/build.sh
```

## Run

To run (while still at `edu.ucsd.energy` directory):
```
$ ./nosleep -i app.jar -r
```

More options:

```
$ ./nosleep -h                           
usage: ./nosleep [-h] [-i <arg>] [-r] [-u] [-w]                           
No-sleep energy bug finder for android applications                       
                                                                          
 -h,--help            prints help message                                 
 -i,--input <arg>     input JAR file                                      
 -r,--verify          verify                                              
 -u,--usage           print components that leave a callback (un)locked   
 -w,--wakelock-info   gather info about wakelock creation                 
                                                                               
Please report issues to Panagiotis Vekris (pvekris@cs.ucsd.edu)           
```

Folder `results/android` will be populated with a number of DOT graphs of call-graphs and control-flow graphs.

