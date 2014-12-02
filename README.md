# No Sleep Energy Bug Finder

This is the accompanying implementation for the tool described
[here](http://goto.ucsd.edu/~pvekris/docs/hotpower12.pdf)

# How to use

## 0. Retarget Dalvik into Java Bytecode

To retarget a target APK into a Java JAR, use
[Dare: Dalvik Retargetting](http://siis.cse.psu.edu/dare/)

I have only tried the version for Linux.

After installing Dare, using the instructions
[here](http://siis.cse.psu.edu/dare/installation.html), do the following:

```
dare -o -d <dare-output> <apk-file>
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
cd <dare-output>/optimized
cd <app-name>
jar cf target-name.jar *
```

This JAR will be used as input to our tool

Configure:

- Set WALA properties in file: edu.ucsd.energy/wala.properties

  In particular, 'java_runtime_dir' needs to point to a valid Java runtime
  installation.


## 1. Build:

```
cd edu.ucsd.energy
bin/build.sh
```

## 2. Run

To run:
```
$ nosleep -i app.jar -r
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

