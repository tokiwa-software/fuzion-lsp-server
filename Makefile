JAVA_FILES = \
	  src/Main.java \
	  src/FuzionTextDocumentService.java \
	  src/FuzionWorkspaceService.java \
	  src/FuzionLanguageServer.java \

JARS_FOR_CLASSPATH = jars/org.eclipse.lsp4j-0.12.0.jar:jars/org.eclipse.lsp4j.generator-0.12.0.jar:jars/org.eclipse.lsp4j.jsonrpc-0.12.0.jar:jars/gson-2.8.7.jar
JARS = $(subst :, ,$(JARS_FOR_CLASSPATH))

all: classes $(IMAGES)
	java -cp classes:$(JARS_FOR_CLASSPATH) Main

classes: $(JAVA_FILES) $(JARS)
	mkdir -p $@
	javac -classpath $(JARS_FOR_CLASSPATH) -d $@ $(JAVA_FILES)

debug: classes $(IMAGES)
	mkdir -p runDir
	java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:8000 -cp classes:$(JARS_FOR_CLASSPATH) Main

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