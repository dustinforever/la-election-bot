How to run:

sbt runProd and then Ctrl-D to get out of it and leave it running. Once it's running, hit the /loggo endpoint to make sure it's working.

The crontab:

This crontab is setup to run more frequently in standard election result updating hours and less frequently other times. It can be edited with crontab -e . Here are its contents:

*/20 4-22 * * * curl -X GET 'http://localhost:9000/refresh'

*/2 23 * * * curl -X GET 'http://localhost:9000/refresh'

*/2 0,1,2,3 * * * curl -X GET 'http://localhost:9000/refresh'
