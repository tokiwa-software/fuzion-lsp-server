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
JAVAC = javac -encoding UTF8
JAVA_FILES = $(shell find $(SOURCEDIR) -name '*.java')
FUZION_HOME = fuzion/build
JAVA_STACKSIZE=16
VERSION=$(shell cat version.txt)
DEBUGGER_SUSPENDED = -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:8000
CONDITIONS = PRECONDITIONS=true POSTCONDITIONS=true
JAVA_ARGS = -Dfuzion.home=$(FUZION_HOME) -Dfile.encoding=UTF-8 -Xss$(JAVA_STACKSIZE)m
JUNIT_ARGS = --fail-if-no-tests --disable-banner --details=tree -cp $(CLASSPATH) -p test.flang
JUNIT_ARGS_PARALLEL = --config=junit.jupiter.execution.parallel.enabled=true --config=junit.jupiter.execution.parallel.mode.default=concurrent
LANGUAGE_SERVER_PORT ?= 3000

JARS_FOR_CLASSPATH = jars/org.eclipse.lsp4j-0.23.1.jar:jars/org.eclipse.lsp4j.generator-0.23.1.jar:jars/org.eclipse.lsp4j.jsonrpc-0.23.1.jar:jars/gson-2.11.0.jar:jars/junit-platform-console-standalone-1.10.2.jar:jars/junit-jupiter-api-5.10.2.jar
JARS = $(subst :, ,$(JARS_FOR_CLASSPATH))

ifeq ($(FUZION_DEBUG_SYMBOLS),true)
	JAVAC += -g
endif

# on windows classpath separator is ; on linux it is :
_CLASSPATH="classes:$(FUZION_HOME)/classes:$(JARS_FOR_CLASSPATH)"
# https://stackoverflow.com/questions/714100/os-detecting-makefile
ifeq ($(OS),Windows_NT)     # is Windows_NT on XP, 2000, 7, Vista, 10...
	CLASSPATH = $(subst :,;,$(_CLASSPATH))
else
	CLASSPATH= $(_CLASSPATH)
endif

all: classes
	java -cp $(CLASSPATH) $(JAVA_ARGS) dev.flang.lsp.server.Main -socket --port=$(LANGUAGE_SERVER_PORT)

socket: classes
	java -cp $(CLASSPATH) $(JAVA_ARGS) dev.flang.lsp.server.Main -socket --port=$(LANGUAGE_SERVER_PORT)

.SILENT:
classes: $(JARS) build_fuzion_no_java
	rm -Rf $@
	mkdir -p $@
	$(JAVAC) -classpath $(CLASSPATH) -d $@ $(JAVA_FILES)

stdio: classes
	java -cp $(CLASSPATH) $(JAVA_ARGS) dev.flang.lsp.server.Main -stdio

.PHONY: debug
debug: NOOP = $(shell lsof -i:8000 | tail -n 1 | awk -F ' ' '{print $$2}' | xargs kill)
debug: classes
	mkdir -p runDir
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 -cp $(CLASSPATH) $(JAVA_ARGS) dev.flang.lsp.server.Main -socket --port=$(LANGUAGE_SERVER_PORT)

debug_supended: classes
	mkdir -p runDir
	java $(DEBUGGER_SUSPENDED) -cp $(CLASSPATH) $(JAVA_ARGS) dev.flang.lsp.server.Main -socket --port=$(LANGUAGE_SERVER_PORT)

jars/org.eclipse.lsp4j-0.23.1.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/0.23.1/$(@F)

jars/org.eclipse.lsp4j.generator-0.23.1.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.generator/0.23.1/$(@F)

jars/org.eclipse.lsp4j.jsonrpc-0.23.1.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/0.23.1/$(@F)

jars/gson-2.11.0.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/com/google/code/gson/gson/2.11.0/$(@F)

jars/junit-platform-console-standalone-1.10.2.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/$(@F)

jars/junit-jupiter-api-5.10.2.jar:
	mkdir -p $(@D)
	wget -O $@ https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/5.10.2/$(@F)

build_fuzion_no_java:
	make -s -C fuzion no-java

build_fuzion:
	make -s -C fuzion

clean:
	make -C fuzion clean
	rm -fR classes
	rm -f out.jar
	rm -fR jars

jar: build_fuzion_no_java classes
	bash -c "cat Manifest.template | sed 's|JARS|$(JARS)|g' > Manifest.txt"
	jar cfm out.jar Manifest.txt -C classes . -C $(FUZION_HOME)/classes .

run_tests_tagged: classes
	$(CONDITIONS) java $(JAVA_ARGS) -jar jars/junit-platform-console-standalone-1.10.2.jar $(JUNIT_ARGS) --include-tag=TAG

run_tests_tagged_suspended: classes
	$(CONDITIONS) java $(DEBUGGER_SUSPENDED) $(JAVA_ARGS) -jar jars/junit-platform-console-standalone-1.10.2.jar  $(JUNIT_ARGS) --include-tag=TAG

run_tests: classes
	$(CONDITIONS) java $(JAVA_ARGS) -jar jars/junit-platform-console-standalone-1.10.2.jar $(JUNIT_ARGS)

run_tests_parallel: classes
	$(CONDITIONS) java $(JAVA_ARGS) -jar jars/junit-platform-console-standalone-1.10.2.jar $(JUNIT_ARGS) $(JUNIT_ARGS_PARALLEL)

run_tests_suspended: classes
	$(CONDITIONS) java $(DEBUGGER_SUSPENDED) $(JAVA_ARGS) -jar jars/junit-platform-console-standalone-1.10.2.jar $(JUNIT_ARGS)

async-profiler:
	git clone https://github.com/jvm-profiling-tools/async-profiler

async-profiler/build/lib/libasyncProfiler.so: async-profiler
	make -C async-profiler
	sudo sysctl kernel.perf_event_paranoid=1

.PHONY: profile/tests
profile/tests: DATE = $(shell date +%y%m%d-%H%M%S)
profile/tests: classes async-profiler/build/lib/libasyncProfiler.so
	$(CONDITIONS) java $(JAVA_ARGS) -agentpath:async-profiler/build/lib/libasyncProfiler.so=start,event=cpu,file=/tmp/$(DATE)_flamegraph.html -jar jars/junit-platform-console-standalone-1.10.2.jar $(JUNIT_ARGS)
	x-www-browser /tmp/$(DATE)_flamegraph.html

.PHONY: profile/tagged_tests
profile/tagged_tests: DATE = $(shell date +%y%m%d-%H%M%S)
profile/tagged_tests: classes async-profiler/build/lib/libasyncProfiler.so
	$(CONDITIONS) java $(JAVA_ARGS) -agentpath:async-profiler/build/lib/libasyncProfiler.so=start,event=cpu,file=/tmp/$(DATE)_flamegraph.html -jar jars/junit-platform-console-standalone-1.10.2.jar $(JUNIT_ARGS) --include-tag=TAG
	x-www-browser /tmp/$(DATE)_flamegraph.html

release: clean build_fuzion jar
	echo "building fuzion_language_server_$(VERSION).zip"
	rm -f fuzion_language_server_$(VERSION).zip
	7z a -tzip fuzion_language_server_$(VERSION).zip out.jar README.md LICENSE bin/fuzion_language_server jars/*.jar fuzion/build/bin/ fuzion/build/lib/ fuzion/build/modules/

.PHONY: lint/java
lint/java: $(JARS) build_fuzion_no_java
	$(JAVAC) -Xlint -classpath $(CLASSPATH) -d classes $(JAVA_FILES)

.PHONY: lint/javadoc
lint/javadoc: $(JARS) build_fuzion_no_java
	$(JAVAC) -Xdoclint -classpath $(CLASSPATH) -d classes $(JAVA_FILES)
