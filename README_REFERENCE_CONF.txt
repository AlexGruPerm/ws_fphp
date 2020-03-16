include "akka-http-version"
akka {
     loglevel = DEBUG
     actor{
      allow-java-serialization = off
     }
     http {
        server {
               preview.enable-http2 = off
               request-timeout = 120 s
               }
        client {
               connecting-timeout = 120 s
               idle-timeout = 366 s
              }
         host-connection-pool {
              max-retries = 0
              max-open-requests = 512
              idle-timeout = 360 s
              client {
                 connecting-timeout = 120 s
                 idle-timeout = 360 s
              }
            }
     }
}