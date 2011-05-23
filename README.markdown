Aleph is a framework for asynchronous communication, built on top of [Netty](http://www.jboss.org/netty) and [Lamina](http://github.com/ztellman/lamina).  It can do all kinds of things, including:

HTTP Server
----

Aleph conforms to the interface described by [Ring](http://github.com/mmcgrana/ring), with one small difference: the request and response are decoupled.

```clj
(use 'lamina.core 'aleph.http)
	
(defn hello-world [channel request]
  (enqueue channel
    {:status 200
     :headers {"content-type" "text/html"}
     :body "Hello World!"}))

(start-http-server hello-world {:port 8080})
```

For more on HTTP functionality, read the [wiki](https://github.com/ztellman/aleph/wiki/HTTP).

HTTP Client
----

This snippet prints out a never-ending sequence of tweets:

```clj
(use 'lamina.core 'aleph.http)
	
(let [ch (:body
           (sync-http-request
             {:method :get
              :basic-auth ["aleph_example" "_password"]
              :url "http://stream.twitter.com/1/statuses/sample.json"}))]
  (doseq [tweet (lazy-channel-seq ch)]
    (println tweet)))
```

A more in-depth exploration of this example can be found [here](http://github.com/ztellman/aleph/wiki/Consuming-and-Broadcasting-a-Twitter-Stream).

WebSockets
----

Making a simple chat client is trivial.  In this, we assume that the first message sent by the client is the user's name:

```clj
(use 'lamina.core 'aleph.http)

(def broadcast-channel (channel))

(defn chat-handler [ch handshake]
  (receive ch
    (fn [name]
      (siphon (map* #(str name ": " %) ch) broadcast-channel)
      (siphon broadcast-channel ch))))

(start-http-server chat-handler {:port 8080 :websocket true})
```

TCP Client/Server
----

Here is a basic echo server:

```clj
(use 'lamina.core 'aleph.tcp)
	
(defn echo-handler [channel client-info]
  (siphon channel channel))

(start-tcp-server echo-handler {:port 1234})
```

For more on TCP functionality, visit the [wiki](https://github.com/ztellman/aleph/wiki/TCP).

--

Other protocols are supported, and still more are forthcoming.

Aleph is meant to be a sandbox for exploring how Clojure can be used effectively in this context.  Contributions and ideas are welcome.

For more information, visit the [wiki](https://github.com/ztellman/aleph/wiki) or the [API documentation](http://ztellman.github.com/aleph/index.html).  If you have questions, please visit the [mailing list](http://groups.google.com/group/aleph-lib).