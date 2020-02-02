# Overview
The repository is created to share the created source code for the exercises. 

# Tasks

## Task 01
> The goal of this exercise is to run a Nexus application on GCP Kubernetes, that can save the uploaded artifacts in Cloud Storage. In your solution, please, follow the order of the subtasks below. The ultimate goal is to solve every part, but turn in your solution even if you do not reach the end of the tasks. Please upload the finished files to public GitHub, every task in its own folder, then send us the repo

The nexus folder contains the created resources.

##### Dockerfile
Docker file depends on the `sonatype/nexus3` image. 
I specified the version because later in the exercise, there is noted that the Dockerfile will be in a git repository and when a new nexus version gets released, the version will be updated there. 
Then the commit will trigger a CI/CD build.

Dockerfile has a build parameter that determines the plugin version included in the nexus container.

##### nexus-all.yaml
The YAML file contains the PersistenceVolume, PersistentVolumeClaim, Deployment and Service definition. 

A secret volume is also part of the configuration that holds the service access JSON for the plugin. I'm not including that.

I worked on the solution locally on windows with docker-for-windows. 
That's why I used the local-storage. On GCP, I would use something like this:

```$yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: faster
provisioner: kubernetes.io/gce-pd
parameters:
  type: pd-ssd
```  

##### nexusbuilder.sh
The "automated" script. 

I was not so sure what you meant with the automated script at this point. Usually, I don't include all of those steps into one script file. I hope the bash scripts is fine with you. I will describe how I would do that in real-life.

##### Continuous integration
 This is my theoretic answer :) 
 
I assume we are using Jenkins for CI/CD and the nexus resources Dockerfile, Kubernetes resource definitions (most likely in Terraform) in a GIT repository.
The git repository might contain other application's definition but in a separate directory tree.
Also, I assume the GIT repository supports web-hooks.

In the Git repository, I would create a webhook which calls the Jenkins, in case of commits or PRs. The PR call helps to ensure the new Dockerfile is correct and the container builds succesfully. We might run some security checks on the container. 

For the master branch builds, a separate Jenkins job would be responsible which would build and publish the container to the Docker registry. If this job finishes, it will trigger another job. This job will deploy the container to a staging environment to ensure the Nexus starts correctly and the configured repositories still available. If this job succeeds, it will trigger a production deployment job.

For the deployments, I would use terraform scripts.

## Task 02
> There's a folder structure where documents are edited by users. For each open document there's a .bak file as backup (doc1.bak for doc1.doc), but it's quite common that they are left there after closing the document editor. In many cases the documents are also deleted or moved and an empty folder wouldn't be necessary, but they are left there because they contain only unnecessary .bak files. Create a program that - starting from a root folder given as a program parameter - cleans up the remaining .bak files without a corresponding document and then deletes any folder emptied by the clean up process. The folder structure is an extremely big virtual file system (but accessed via the regular Java File API ) and deletion is a very slow operation. You can delete multiple files in parallel, that's handled well because of the distribution of the file system.

I hope it is not a big problem that I went with Kotlin. The task seemed a good candidate for practicing coroutines a little.

### Solution
I created 3 components. 

The Main class which handles the CLI parameter, with helper text as well.

The FileScanner component walks through the file system and recognize bak files and empty folders.
The component sends the files and folders to two separate channels. 
Because the files can be deleted on multiple threads, the folders can't be deleted unless all sub resources has already been deleted.

The BackupRemover component takes care the actual delete and the asynchronous and parallel execution.

### Usage
The CLI parameters help can be reached like:
```java -jar backup-remover.jar -h```
Or
```mvn exec:java -Dexec.args="-h"```

To remove bak files under the working directory just run the
```java -jar backup-remover.jar```
command. This removes the bak files with "5 thread" and 2 retries for each file. 
 

### Note
To verify the files delete is executed on a separate thread, just uncomment line 88 in the [BackupRemover.kt].

```
// Uncomment for checking it is running on different threads
//        logger.info { "Deleted: $file" }
```

### Technology stack

* Kotlin - for less and more maintainable code
* RXjava2 - for creating channels
* Kotlin Coroutines - for light threads
* JUnit5
