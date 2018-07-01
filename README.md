Saws
======

[![Build Status](https://travis-ci.org/ambiata/saws.png)](https://travis-ci.org/ambiata/saws)

Scala AWS api

aws-java-sdk dependency
-----------------------
Reasoning
- Hadoop has an incompatible dependency on `aws-java-sdk` with the version in use here
- [saws-aws](https://github.com/ambiata/saws-aws/blob/master/README.md#reasoning)

Attempted use of Proguard in saws
- Created a saws-shim project where we attempted to use proguard to rename the `com.amazonaws` packages throughout the `aws-java-sdk` as well as the `saws` codebase.
- Proguard was successful in renaming the `aws-java-sdk` packages in the java codebase - this is now used in [saws-aws](https://github.com/ambiata/saws-aws)
- Proguard was unsuccessful in handling certain scala classes, namely package objects and some type parameters

Current solution
- `saws-aws` adds the `com.ambiata` prefix to package names from the `aws-java-sdk`. i.e. `com.amazonaws.foo` -> `com.amazonaws.foo`
- There is a source code change to match `saws-aws`

NB - Any downstream dependencies will need to use the `saws-aws` version of the `aws-java-sdk` or end up with two large jars (`saws-aws` as well as `aws-java-sdk`) on your classpath
