<!-- generated, do not modify <% 
def installDir = project.file(".").toPath().relativize(project.installDist.destinationDir.toPath()).toString().replace('\\','/')
def appDir = "${installDir}/bin"
def appScript = project.startScripts.applicationName
def appName = project.applicationName
def projName = project.name
%>-->
##### build app
```
gradle installDist
```

##### run app using gradle
```
gradle run
```

##### run app using command line
```
./${appDir}/${appScript}
```

##### build docker image
```
gradle buildDockerImage
```

##### run interactive docker container using gradle
```
gradle runDockerContainer
```

##### run interactive docker container using command line
```
docker run --rm -ti ${projName}
```

##### run daemonized docker container
```
docker run --restart=always --log-opt max-size=64m --log-opt max-file=16 -d --name=${appName} ${projName}
```

