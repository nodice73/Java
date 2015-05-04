JFLAGS = -g -Xlint:deprecation
JC = javac

.SUFFIXES: .java .class

.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	../org/fhcrc/honeycomb/mc/MCImage.java \
	../org/fhcrc/honeycomb/mc/MCSperiment.java \
	../org/fhcrc/honeycomb/ijutils/MaskInfo.java \
	CountFluor.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
