build:
	mvn -T6 -DskipTests install
format:
	mvn com.coveo:fmt-maven-plugin:format
