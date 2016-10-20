all: clean compile package

compile:
	javac -cp lib/*.jar *.java

package:
	jar cvfm ForceMeter.jar Manifest *.class

clean:
	rm *.class *.jar || echo "Nothing to delete!"
