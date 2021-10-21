# This file is part of the Fuzion language server protocol implementation.
#
# The Fuzion language server protocol implementation is free software: you can redistribute it
# and/or modify it under the terms of the GNU General Public License as published
# by the Free Software Foundation, version 3 of the License.
#
# The Fuzion language server protocol implementation is distributed in the hope that it will be
# useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
# License for more details.
#
# You should have received a copy of the GNU General Public License along with The
# Fuzion language server protocol implementation.  If not, see <https://www.gnu.org/licenses/>.


# -----------------------------------------------------------------------
#
#  Tokiwa Software GmbH, Germany
#
#  Source code of main Makefile
#
#  Author: Michael Lill (michael.lill@tokiwa.software)
#
# -----------------------------------------------------------------------

SOURCEDIR = src
CLASSDIR = classes
JAVA_FILES = $(shell find $(SOURCEDIR) -name '*.java')
FUZION_HOME = '${CURDIR}/fuzion/build'
JAVA_STACKSIZE=16
JAVA_MAXHEAP=256

JARS_FOR_CLASSPATH = jars/org.eclipse.lsp4j-0.12.0.jar:jars/org.eclipse.lsp4j.generator-0.12.0.jar:jars/org.eclipse.lsp4j.jsonrpc-0.12.0.jar:jars/gson-2.8.7.jar:jars/junit-platform-console-standalone-1.8.1.jar:jars/junit-jupiter-api-5.8.1.jar:jars/org.eclipse.xtext.xbase.lib-2.25.0.jar:jars/guava-31.0.1-jre.jar
JARS = $(subst :, ,$(JARS_FOR_CLASSPATH))

all: classes
	java -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

tcp: classes
	java -Xss$(JAVA_STACKSIZE)m -Xmx$(JAVA_MAXHEAP)m -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

classes: $(JAVA_FILES) $(JARS) build_fuzion
	mkdir -p $@
	javac -classpath $(JARS_FOR_CLASSPATH):$(FUZION_HOME)/classes -d $@ $(JAVA_FILES)

stdio: classes
	java -Xss$(JAVA_STACKSIZE)m -Xmx$(JAVA_MAXHEAP)m -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main

debug: classes
	mkdir -p runDir
	java -Xss$(JAVA_STACKSIZE)m -Xmx$(JAVA_MAXHEAP)m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

debug_supended: classes
	mkdir -p runDir
	java -Xss$(JAVA_STACKSIZE)m -Xmx$(JAVA_MAXHEAP)m -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000 -cp classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH) -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 dev.flang.lsp.server.Main -tcp

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

# https://github.com/eclipse/lsp4j/issues/494
jars/org.eclipse.xtext.xbase.lib-2.25.0.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/eclipse/xtext/org.eclipse.xtext.xbase.lib/2.25.0/$(@F)

# https://github.com/eclipse/lsp4j/issues/494
jars/guava-31.0.1-jre.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/com/google/guava/guava/31.0.1-jre/$(@F)

build_fuzion:
	make -s -C fuzion 2> /dev/null

clean:
	make -C fuzion clean
	rm -fR classes
	rm -f out.jar

jar: clean classes
	jar cfm out.jar Manifest.txt -C classes . -C $(FUZION_HOME)/classes .

run_tests: classes
	PRECONDITIONS=true POSTCONDITIONS=true java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 -Xss$(JAVA_STACKSIZE)m -Xmx$(JAVA_MAXHEAP)m -jar jars/junit-platform-console-standalone-1.8.1.jar --details=verbose -cp classes:fuzion/build/classes:$(JARS_FOR_CLASSPATH) -p test.flang.lsp.server

run_tests_suspended: classes
	PRECONDITIONS=true POSTCONDITIONS=true java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000 -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 -Xss$(JAVA_STACKSIZE)m -Xmx$(JAVA_MAXHEAP)m -jar jars/junit-platform-console-standalone-1.8.1.jar --details=verbose -cp classes:fuzion/build/classes:$(JARS_FOR_CLASSPATH) -p test.flang.lsp.server

profile: PID = $(shell ps aux | grep fuzion-lsp-server | grep lsp4j | head -n 1 | awk '{print $$2}')
profile:
# https://github.com/jvm-profiling-tools/async-profiler
	profiler.sh -d 30 -f /tmp/flamegraph.html $(PID)
	x-www-browser /tmp/flamegraph.html
