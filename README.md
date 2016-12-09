# Purchase Service

To run in a terminal:

* cd to project directory.
* `./gradlew run`
* in separate window
* `curl 'http://localhost:5050/api/recent_purchases/Howard.Jast'` (or other valid user)
* execute multiple times and notice much faster responses due to caching.
* In original window ^C to stop server.
 
A few notes:
Cache settings are configurable. No particular cache strategy was specified.
The requirements specify that two of the requests happen concurrently so I call `fork`.
However with a system like ratpack, this is not recommended as you'll trade off
individual response times at the expense of overall throughput.
