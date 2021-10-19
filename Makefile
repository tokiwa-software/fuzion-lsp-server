SOURCEDIR = src
CLASSDIR = classes
JAVA_FILES = $(shell find $(SOURCEDIR) -name '*.java')
FUZION_HOME = '${CURDIR}/fuzion/build'

JARS_FOR_CLASSPATH = jars/org.eclipse.lsp4j-0.12.0.jar:jars/org.eclipse.lsp4j.generator-0.12.0.jar:jars/org.eclipse.lsp4j.jsonrpc-0.12.0.jar:jars/gson-2.8.7.jar:jars/junit-platform-console-standalone-1.8.1.jar:jars/junit-jupiter-api-5.8.1.jar
JARS = $(subst :, ,$(JARS_FOR_CLASSPATH))

all: classes
	java -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

tcp:
	java -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

classes: $(JAVA_FILES) $(JARS) build_fuzion
	mkdir -p $@
	javac -classpath $(JARS_FOR_CLASSPATH):$(FUZION_HOME)/classes -d $@ $(JAVA_FILES)

stdio: classes
	java -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main

debug: classes
	mkdir -p runDir
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

debug_supended: classes
	mkdir -p runDir
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000 -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

jars/org.eclipse.lsp4j-0.12.0.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/0.12.0/$(@F)

jars/org.eclipse.lsp4j.generator-0.12.0.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.generator/0.12.0/$(@F)

jars/org.eclipse.lsp4j.jsonrpc-0.12.0.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/0.12.0/$(@F)

jars/gson-2.8.7.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.7/$(@F)

jars/junit-platform-console-standalone-1.8.1.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.8.1/$(@F)

jars/junit-jupiter-api-5.8.1.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/5.8.1/$(@F)

build_fuzion:
	make -C fuzion

clean:
	rm -fR classes
	rm -f out.jar

jar: clean classes
	jar cfm out.jar Manifest.txt -C classes . -C $(FUZION_HOME)/classes .

run_tests: classes
	PRECONDITIONS=true POSTCONDITIONS=true java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 -Xss16m -Xmx265m -jar jars/junit-platform-console-standalone-1.8.1.jar --details=verbose -cp classes:fuzion/build/classes:$(JARS_FOR_CLASSPATH) -p test.flang.lsp.server

run_tests_suspended: classes
	PRECONDITIONS=true POSTCONDITIONS=true java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000 -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 -Xss16m -Xmx265m -jar jars/junit-platform-console-standalone-1.8.1.jar --details=verbose -cp classes:fuzion/build/classes:$(JARS_FOR_CLASSPATH) -p test.flang.lsp.server
