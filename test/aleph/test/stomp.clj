;;   Copyright (c) Zachary Tellman. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns aleph.test.stomp
  (:use
    [clojure test]
    [aleph stomp tcp]
    [aleph.test utils]
    [lamina core connections trace executor viz]))

;;;

(defmacro with-server [handler port & body]
  `(let [stop-server# (start-tcp-server ~handler {:port ~port})]
     (try
       ~@body
       (finally
         (stop-server#)))))

(defn basic-echo-handler [ch _]
  (siphon ch ch))

(defmacro with-stomp-client [[c] port & body]
  `(let [~c (pipelined-client #(deref (stomp-connection {:host "localhost", :port ~port})))]
     (try
       ~@body
       (finally
         (close-connection ~c)))))

(defmacro with-router [port & body]
  `(let [stop-server# (start-stomp-router {:port ~port})]
     (try
       ~@body
       (finally
         (stop-server#)))))

(defn probe-producer [id]
  (map*
    (fn [msg]
      {:command :send
       :headers {"destination" id}
       :body (pr-str msg)})
    (probe-channel id)))

(defmacro with-endpoint [[e] port & body]
  `(let [~e (stomp-endpoint {:producer probe-producer :client-options {:host "localhost", :port ~port}})]
     (try
       ~@body
       (finally
         (close-connection ~e)))))

;;;

(def messages
  [{:command :message
    :headers {"content-length" "10"
              "abc/def/bloop : \n \\" "42 \\ \n :"}
    :body "abcdefghij"}
   {:command :subscribe
    :headers {}
    :body "abcdefghij"}
   {:command :heartbeat}])

(deftest test-basic-echo
  (with-server basic-echo-handler 10000
    (with-stomp-client [c] 10000
      (doseq [m (->> messages
                  (repeat 10)
                  (apply concat))]
        (is (= m @(c m 1000)))))))

;;;

(deftest test-basic-router
  (with-router 10000
    (with-endpoint [c1] 10000
      (let [a (subscribe c1 "abc")
            b (subscribe c1 "def")
            consume #(-> % read-channel (wait-for-result 2500) :body read-string)]

        ;; wait for subscriptions to propagate
        (Thread/sleep 500)

        (is (= true (trace :abc 1)))
        (is (= true (trace :def 2)))
        (is (= 1 (consume a)))
        (is (= 2 (consume b)))

        ;; unsubscribe, and wait to propagate
        (close a)
        (Thread/sleep 500)
        
        (is (= false (trace :abc 3)))
        (is (= true (trace :def 4)))
        (is (= 4 (consume b)))

        (with-endpoint [c2] 10000

          ;; wait for existing subscriptions to propagate
          (Thread/sleep 500)

          ;; this should double broadcast
          (is (= true (trace :def 5)))
          (is (= 5 (consume b)))
          (is (= 5 (consume b))))))
    
    (Thread/sleep 500)
    (is (= false (trace :def 6)))))

;;;

(deftest ^:benchmark test-stomp-roundtrip
  (let [msg (first messages)]
    (with-server basic-echo-handler 10000
      (with-stomp-client [c] 10000
        (bench "simple stomp roundtrip"
          @(c msg))
        (bench "1e3 stomp roundtrips"
          @(apply merge-results (repeatedly 1e3 #(c msg))))))))

(deftest ^:benchmark test-router-roundtrip
  (with-router 10000
    (with-endpoint [c] 10000
      (let [ch (subscribe c "abc")]
        (Thread/sleep 500)
        (bench "stomp router roundtrip"
          (trace :abc 1)
          @(read-channel ch))
        (bench "1e3 stomp router roundtrip"
          (let [f (task
                    (dotimes [_ 1e3]
                      @(read-channel ch)))]
            (dotimes [_ 1e3]
              (trace :abc 1))
            @f))))))
