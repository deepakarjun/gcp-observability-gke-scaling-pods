export GOOGLE_APPLICATION_CREDENTIALS=/home/g281508/workbench/keys/templatization-backend-storage.json
echo $GOOGLE_APPLICATION_CREDENTIALS
mvn clean package
mvn clean spring-boot:run